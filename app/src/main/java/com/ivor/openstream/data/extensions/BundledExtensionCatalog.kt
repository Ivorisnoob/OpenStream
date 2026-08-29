package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.SourceExtensionManifest

internal object BundledExtensionCatalog {
    val extensions = listOf(
        SourceExtensionManifest(
            id = "yoru",
            name = "Yoru",
            description = "Primary fast route with broad episode coverage.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-yoru"),
            isRecommended = true
        ),
        SourceExtensionManifest(
            id = "cypher",
            name = "Cypher",
            description = "Balanced backup route for when the primary source is busy.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-cypher"),
            isRecommended = true
        ),
        SourceExtensionManifest(
            id = "breach",
            name = "Breach",
            description = "Alternative route tuned for wide movie and series availability.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-breach")
        ),
        SourceExtensionManifest(
            id = "neon",
            name = "Neon",
            description = "Lightweight alternative source for quick failover.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-neon")
        ),
        SourceExtensionManifest(
            id = "vyse",
            name = "Vyse",
            description = "English-focused route with matching audio labels.",
            versionName = "1.0.0",
            language = "English",
            providerIds = setOf("vidking-vyse")
        ),
        SourceExtensionManifest(
            id = "killjoy",
            name = "Killjoy",
            description = "German-language source route.",
            versionName = "1.0.0",
            language = "German",
            providerIds = setOf("vidking-killjoy")
        ),
        SourceExtensionManifest(
            id = "fade",
            name = "Fade",
            description = "Hindi-focused route with matching audio labels.",
            versionName = "1.0.0",
            language = "Hindi",
            providerIds = setOf("vidking-fade")
        ),
        SourceExtensionManifest(
            id = "omen",
            name = "Omen",
            description = "Extra coverage for titles missing from the primary routes.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-omen")
        ),
        SourceExtensionManifest(
            id = "raze",
            name = "Raze",
            description = "Additional high-availability fallback route.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-raze")
        ),
        SourceExtensionManifest(
            id = "web-fallback",
            name = "Web compatibility",
            description = "Slower compatibility resolver used only when direct routes fail.",
            versionName = "1.0.0",
            language = "Multi",
            providerIds = setOf("vidking-webview"),
            isFallback = true
        )
    )
}
