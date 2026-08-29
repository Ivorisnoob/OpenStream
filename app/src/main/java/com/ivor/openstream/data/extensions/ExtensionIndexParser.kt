package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionEngine
import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.ExtensionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns repository JSON into domain manifests. Tolerant by design: a malformed entry is dropped
 * instead of failing the whole index, so one bad publish cannot empty a user's marketplace.
 */
@Singleton
class ExtensionIndexParser @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /** Parses a repository document: an object, or a bare array of entries. */
    fun parseRepo(raw: String): ExtensionRepoDto {
        val root = json.parseToJsonElement(raw)
        return when (root) {
            is JsonArray -> ExtensionRepoDto(extensions = decodeEntries(root))
            is JsonObject -> {
                val dto = json.decodeFromJsonElement(ExtensionRepoDto.serializer(), stripEntries(root))
                dto.copy(extensions = decodeEntries(root["extensions"] as? JsonArray))
            }
            else -> throw IllegalArgumentException("Repository index is not a JSON object or array")
        }
    }

    /** Parses a linked extension list: a bare array, or `{ "extensions": [...] }`. */
    fun parseExtensionList(raw: String): List<ExtensionEntryDto> {
        val root = json.parseToJsonElement(raw)
        return when (root) {
            is JsonArray -> decodeEntries(root)
            is JsonObject -> decodeEntries(root["extensions"] as? JsonArray)
            else -> emptyList()
        }
    }

    fun encodeSnapshot(snapshot: CachedRepoSnapshot): String =
        json.encodeToString(CachedRepoSnapshot.serializer(), snapshot)

    fun decodeSnapshot(raw: String): CachedRepoSnapshot =
        json.decodeFromString(CachedRepoSnapshot.serializer(), raw)

    fun toManifest(entry: ExtensionEntryDto, repoId: String): ExtensionManifest? {
        val id = entry.id.trim()
        if (id.isEmpty()) return null
        val engineType = ExtensionEngineType.fromKey(entry.engine.type)
        return ExtensionManifest(
            id = id,
            repoId = repoId,
            name = entry.name.trim().ifEmpty { id },
            description = entry.description.trim(),
            authors = entry.authors.filter { it.isNotBlank() },
            versionName = entry.version.trim().ifEmpty { "1.0.0" },
            versionCode = entry.versionCode.coerceAtLeast(1),
            apiVersion = entry.apiVersion.coerceAtLeast(1),
            language = entry.language.trim().ifEmpty { "Multi" },
            iconUrl = entry.iconUrl?.trim()?.takeIf { it.isNotEmpty() },
            tags = entry.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
            status = ExtensionStatus.fromCode(entry.status),
            isNsfw = entry.nsfw,
            installs = entry.installs.coerceAtLeast(0L),
            installsLast7Days = entry.installsLast7Days.coerceAtLeast(0L),
            rating = entry.rating.coerceIn(0f, 5f),
            ratingCount = entry.ratingCount.coerceAtLeast(0),
            updatedAt = parseTimestamp(entry.updatedAt.asRawString()),
            homepage = entry.homepage?.trim()?.takeIf { it.isNotEmpty() },
            engine = ExtensionEngine(
                type = engineType,
                endpoint = entry.engine.endpoint.trim(),
                priority = entry.engine.priority,
                language = entry.engine.language?.trim()?.takeIf { it.isNotEmpty() },
                qualityFilter = entry.engine.qualityFilter?.trim()?.takeIf { it.isNotEmpty() }
            ),
            isFallback = entry.fallback || engineType == ExtensionEngineType.VIDKING_WEBVIEW,
            installedByDefault = entry.installedByDefault
        )
    }

    /** Accepts `2026-08-01`, a full ISO instant, or epoch millis. Returns 0 when unknown. */
    fun parseTimestamp(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return 0L
        value.toLongOrNull()?.let { return if (it > 0) it else 0L }
        return try {
            LocalDate.parse(value.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (error: DateTimeParseException) {
            0L
        }
    }

    private fun decodeEntries(array: JsonArray?): List<ExtensionEntryDto> =
        array.orEmpty().mapNotNull { element ->
            runCatching {
                json.decodeFromJsonElement(ExtensionEntryDto.serializer(), element)
            }.getOrNull()
        }

    /** Entries are decoded one by one, so keep them out of the strict repository decode. */
    private fun stripEntries(root: JsonObject): JsonObject =
        JsonObject(root.filterKeys { it != "extensions" })
}
