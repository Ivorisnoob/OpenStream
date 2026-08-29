package com.ivor.openstream.data.extensions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Wire format for an OpenStream extension repository.
 *
 * The shape is deliberately close to formats users already know:
 *  - CloudStream `repo.json`: `name` / `description` / `manifestVersion` / list-of-lists indirection.
 *  - Mihon & Aniyomi `index.min.json`: a flat array of entries is also accepted.
 *  - Stremio add-on manifests: entries are declarative, the client owns the runtime.
 */
@Serializable
data class ExtensionRepoDto(
    val manifestVersion: Int = 1,
    val name: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val website: String? = null,
    /** Indirection to one or more extension lists, as in CloudStream's `pluginLists`. */
    @SerialName("extensionLists")
    val extensionLists: List<String> = emptyList(),
    /** Inline catalog, so a single file can be a complete repository. */
    val extensions: List<ExtensionEntryDto> = emptyList()
)

@Serializable
data class ExtensionEntryDto(
    val id: String,
    val name: String,
    val description: String = "",
    val version: String = "1.0.0",
    val versionCode: Int = 1,
    val apiVersion: Int = 1,
    val authors: List<String> = emptyList(),
    val language: String = "Multi",
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    /** 0 down, 1 ok, 2 slow, 3 beta. */
    val status: Int = 1,
    val nsfw: Boolean = false,
    val installs: Long = 0L,
    val installsLast7Days: Long = 0L,
    val rating: Float = 0f,
    val ratingCount: Int = 0,
    /** ISO-8601 date (`2026-08-01`) or epoch millis. */
    val updatedAt: JsonElement? = null,
    val homepage: String? = null,
    val fallback: Boolean = false,
    val installedByDefault: Boolean = false,
    val engine: ExtensionEngineDto
)

@Serializable
data class ExtensionEngineDto(
    val type: String,
    val endpoint: String = "",
    val priority: Int = 50,
    val language: String? = null,
    val qualityFilter: String? = null
)

/** Cached snapshot of one repository, persisted verbatim so the catalog survives being offline. */
@Serializable
data class CachedRepoSnapshot(
    val url: String,
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
    val website: String? = null,
    val fetchedAt: Long = 0L,
    val extensions: List<ExtensionEntryDto> = emptyList()
)

internal fun JsonElement?.asRawString(): String? = (this as? JsonPrimitive)?.content
