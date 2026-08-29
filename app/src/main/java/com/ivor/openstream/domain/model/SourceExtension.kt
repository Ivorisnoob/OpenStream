package com.ivor.openstream.domain.model

data class SourceExtensionManifest(
    val id: String,
    val name: String,
    val description: String,
    val versionName: String,
    val language: String,
    val providerIds: Set<String>,
    val isRecommended: Boolean = false,
    val isFallback: Boolean = false,
    val installedByDefault: Boolean = true
)

data class SourceExtension(
    val manifest: SourceExtensionManifest,
    val isInstalled: Boolean,
    val isEnabled: Boolean
)
