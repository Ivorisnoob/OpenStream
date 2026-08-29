package com.ivor.openstream.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledExtensionCatalogTest {
    @Test
    fun `extension and provider identifiers are unique`() {
        val extensions = BundledExtensionCatalog.extensions
        val providerIds = extensions.flatMap { it.providerIds }

        assertEquals(extensions.size, extensions.map { it.id }.distinct().size)
        assertEquals(providerIds.size, providerIds.distinct().size)
    }

    @Test
    fun `every bundled stream provider belongs to an extension`() {
        val expectedProviderIds = setOf(
            "vidking-yoru",
            "vidking-cypher",
            "vidking-breach",
            "vidking-neon",
            "vidking-vyse",
            "vidking-killjoy",
            "vidking-fade",
            "vidking-omen",
            "vidking-raze",
            "vidking-webview"
        )

        assertEquals(
            expectedProviderIds,
            BundledExtensionCatalog.extensions.flatMapTo(mutableSetOf()) { it.providerIds }
        )
        assertTrue(BundledExtensionCatalog.extensions.all { it.installedByDefault })
    }
}
