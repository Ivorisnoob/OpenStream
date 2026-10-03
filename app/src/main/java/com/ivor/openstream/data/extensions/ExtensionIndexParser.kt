package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionEngine
import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.ExtensionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

    /**
     * Parses a repository document: an object, a bare array of entries, or a Stremio add-on
     * manifest ([sourceUrl] is where it was fetched, which is how the add-on is addressed).
     */
    fun parseRepo(raw: String, sourceUrl: String? = null): ExtensionRepoDto {
        val root = json.parseToJsonElement(raw)
        if (root is JsonObject && isStremioManifest(root)) return stremioRepo(root, sourceUrl)
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
                qualityFilter = entry.engine.qualityFilter?.trim()?.takeIf { it.isNotEmpty() },
                movieUrl = entry.engine.movieUrl?.trim()?.takeIf { it.isNotEmpty() },
                tvUrl = entry.engine.tvUrl?.trim()?.takeIf { it.isNotEmpty() }
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

    private companion object {
        /** After the built-in direct routes, before WebView fallbacks. */
        const val STREMIO_PRIORITY = 40
    }

    private fun decodeEntries(array: JsonArray?): List<ExtensionEntryDto> =
        array.orEmpty().mapNotNull { element ->
            runCatching {
                json.decodeFromJsonElement(ExtensionEntryDto.serializer(), element)
            }.getOrNull()
        }

    private fun isStremioManifest(root: JsonObject): Boolean =
        "resources" in root && "id" in root && "extensions" !in root && "extensionLists" !in root

    /**
     * A Stremio add-on as a one-entry repository, so pasting its link in "Add repository" is all
     * it takes. Installed straight away when it can play here: it serves streams and doesn't
     * need configuring first (a configured add-on's link already carries its settings).
     */
    private fun stremioRepo(manifest: JsonObject, sourceUrl: String?): ExtensionRepoDto {
        val addonId = manifest.text("id")?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Stremio manifest has no id")
        val name = manifest.text("name")?.trim().orEmpty().ifEmpty { addonId }
        val baseUrl = sourceUrl?.substringBefore('?')?.removeSuffix("/manifest.json")?.trimEnd('/').orEmpty()
        val resources = (manifest["resources"] as? JsonArray).orEmpty().mapNotNull { resource ->
            when (resource) {
                is JsonPrimitive -> resource.contentOrNull
                is JsonObject -> resource.text("name")
                else -> null
            }
        }
        val hints = manifest["behaviorHints"] as? JsonObject
        val needsConfiguring = hints?.text("configurationRequired") == "true"
        val servesStreams = "stream" in resources
        val playable = servesStreams && !needsConfiguring && baseUrl.isNotEmpty()
        val types = (manifest["types"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val note = when {
            !servesStreams -> "This add-on has no streams (only catalogs, metadata or subtitles), so it has nothing to play here."
            needsConfiguring -> "Configure this add-on on its own page first, then add the link it gives you."
            else -> null
        }
        val logo = manifest.text("logo")?.takeIf { it.startsWith("https://") }
        val entry = ExtensionEntryDto(
            id = addonId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "addon" },
            name = name,
            description = listOfNotNull(manifest.text("description")?.trim()?.takeIf { it.isNotEmpty() }, note)
                .joinToString("\n\n"),
            version = manifest.text("version")?.trim().orEmpty().ifEmpty { "1.0.0" },
            versionCode = semverCode(manifest.text("version")),
            authors = listOfNotNull(baseUrl.substringAfter("://").substringBefore('/').takeIf { it.isNotEmpty() }),
            iconUrl = logo,
            tags = (listOf("stremio") + types.map { if (it == "movie") "movies" else it }).distinct(),
            status = if (playable) 3 else 0,
            homepage = baseUrl.takeIf { it.isNotEmpty() && hints?.text("configurable") == "true" }?.let { "$it/configure" },
            installedByDefault = playable,
            engine = ExtensionEngineDto(
                type = if (playable) "stremio" else "unsupported",
                endpoint = baseUrl,
                priority = STREMIO_PRIORITY
            )
        )
        return ExtensionRepoDto(
            name = "$name (Stremio)",
            description = "Stremio add-on",
            iconUrl = logo,
            website = baseUrl.takeIf { it.isNotEmpty() },
            extensions = listOf(entry)
        )
    }

    /** "1.4.12" -> 1004012, so a newer add-on version shows as an update. */
    private fun semverCode(version: String?): Int {
        val parts = version.orEmpty().split('.', '-', '+').take(3).map { it.toIntOrNull()?.coerceIn(0, 999) ?: 0 }
        return (parts.getOrElse(0) { 0 } * 1_000_000 + parts.getOrElse(1) { 0 } * 1_000 + parts.getOrElse(2) { 0 })
            .coerceAtLeast(1)
    }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    /** Entries are decoded one by one, so keep them out of the strict repository decode. */
    private fun stripEntries(root: JsonObject): JsonObject =
        JsonObject(root.filterKeys { it != "extensions" })
}
