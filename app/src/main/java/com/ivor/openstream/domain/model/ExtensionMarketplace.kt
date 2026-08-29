package com.ivor.openstream.domain.model

/**
 * Version of the extension manifest contract this build understands. Extensions declaring a
 * higher [ExtensionManifest.apiVersion] are listed but cannot be installed, mirroring the
 * `apiVersion` gate CloudStream uses in its plugin lists.
 */
const val EXTENSION_API_VERSION: Int = 1

/** Availability reported by the repository. Status codes match CloudStream's convention. */
enum class ExtensionStatus(val code: Int, val label: String) {
    DOWN(0, "Down"),
    OK(1, "Online"),
    SLOW(2, "Slow"),
    BETA(3, "Beta");

    companion object {
        fun fromCode(code: Int): ExtensionStatus = entries.firstOrNull { it.code == code } ?: OK
    }
}

/**
 * The runtime an extension binds to. Manifests are declarative — like a Stremio add-on they
 * describe *where* to resolve streams, and the app supplies the engine that talks the protocol.
 */
enum class ExtensionEngineType(val key: String) {
    VIDKING_DIRECT("vidking-direct"),
    VIDKING_WEBVIEW("vidking-webview"),
    UNSUPPORTED("unsupported");

    companion object {
        fun fromKey(key: String?): ExtensionEngineType =
            entries.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) } ?: UNSUPPORTED
    }
}

data class ExtensionEngine(
    val type: ExtensionEngineType,
    val endpoint: String = "",
    val priority: Int = DEFAULT_PRIORITY,
    val language: String? = null,
    val qualityFilter: String? = null
) {
    val isRunnable: Boolean
        get() = when (type) {
            ExtensionEngineType.VIDKING_DIRECT -> endpoint.isNotBlank()
            ExtensionEngineType.VIDKING_WEBVIEW -> true
            ExtensionEngineType.UNSUPPORTED -> false
        }

    companion object {
        const val DEFAULT_PRIORITY = 50
    }
}

/** A single catalog entry as published by a repository. */
data class ExtensionManifest(
    val id: String,
    val repoId: String,
    val name: String,
    val description: String,
    val authors: List<String> = emptyList(),
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val apiVersion: Int = EXTENSION_API_VERSION,
    val language: String = "Multi",
    val iconUrl: String? = null,
    val tags: List<String> = emptyList(),
    val status: ExtensionStatus = ExtensionStatus.OK,
    val isNsfw: Boolean = false,
    val installs: Long = 0L,
    val installsLast7Days: Long = 0L,
    val rating: Float = 0f,
    val ratingCount: Int = 0,
    val updatedAt: Long = 0L,
    val homepage: String? = null,
    val engine: ExtensionEngine,
    val isFallback: Boolean = false,
    val installedByDefault: Boolean = false
) {
    /** Globally unique across repositories — two repos may publish the same extension id. */
    val key: String get() = "$repoId/$id"

    val author: String get() = authors.firstOrNull().orEmpty().ifBlank { "Community" }

    val isSupported: Boolean get() = engine.isRunnable && apiVersion <= EXTENSION_API_VERSION
}

/** How often this extension actually produced a playable stream on this device. */
data class ExtensionUsage(
    val successes: Int = 0,
    val failures: Int = 0
) {
    val total: Int get() = successes + failures

    /** Null until there is enough local history to be meaningful. */
    val successRate: Float?
        get() = if (total < MIN_SAMPLES) null else successes.toFloat() / total

    companion object {
        const val MIN_SAMPLES = 4
    }
}

data class MarketplaceExtension(
    val manifest: ExtensionManifest,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val installedVersionCode: Int = 0,
    val installedAt: Long = 0L,
    val usage: ExtensionUsage = ExtensionUsage()
) {
    val key: String get() = manifest.key
    val hasUpdate: Boolean get() = isInstalled && manifest.versionCode > installedVersionCode
    val isActive: Boolean get() = isInstalled && isEnabled && manifest.isSupported
}

data class ExtensionRepo(
    val id: String,
    val url: String,
    val name: String,
    val description: String = "",
    val iconUrl: String? = null,
    val website: String? = null,
    val isBuiltIn: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val extensionCount: Int = 0,
    val error: String? = null
)

enum class MarketplaceSort(val label: String) {
    POPULAR("Popular"),
    TRENDING("Trending"),
    TOP_RATED("Top rated"),
    RECENT("Recently updated"),
    NAME("A–Z")
}

data class ExtensionCatalog(
    val repos: List<ExtensionRepo> = emptyList(),
    val extensions: List<MarketplaceExtension> = emptyList(),
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val syncError: String? = null
) {
    val installed: List<MarketplaceExtension> get() = extensions.filter { it.isInstalled }
    val enabled: List<MarketplaceExtension> get() = extensions.filter { it.isInstalled && it.isEnabled }
    val updatable: List<MarketplaceExtension> get() = extensions.filter { it.hasUpdate }
}
