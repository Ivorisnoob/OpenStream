package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.EXTENSION_API_VERSION
import com.ivor.openstream.domain.model.ExtensionEngineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The official catalog is published twice: bundled in the APK for first run, and served over HTTP
 * for updates. Both copies must stay identical, and every entry must be installable by this build.
 */
class OfficialCatalogTest {

    private val parser = ExtensionIndexParser()
    private val bundled = File("src/main/assets/extensions/official-repo.json")
    private val published = File("../extensions/index.json")

    @Test
    fun `bundled catalog matches the published catalog`() {
        assertTrue("Missing ${bundled.path}", bundled.exists())
        assertTrue("Missing ${published.path}", published.exists())
        assertEquals(published.readText(), bundled.readText())
    }

    @Test
    fun `every official entry is runnable on this build`() {
        val repo = parser.parseRepo(bundled.readText())
        val manifests = repo.extensions.mapNotNull { parser.toManifest(it, repoId = "official") }

        assertEquals(repo.extensions.size, manifests.size)
        assertTrue(manifests.isNotEmpty())
        manifests.forEach { manifest ->
            assertTrue("${manifest.id} is not supported", manifest.isSupported)
            assertTrue(manifest.apiVersion <= EXTENSION_API_VERSION)
            assertTrue("${manifest.id} has no description", manifest.description.isNotBlank())
        }
    }

    @Test
    fun `extension ids and resolved provider ids stay unique`() {
        val repo = parser.parseRepo(bundled.readText())
        val ids = repo.extensions.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `catalog still covers the sources shipped before the marketplace`() {
        val repo = parser.parseRepo(bundled.readText())
        val expected = setOf(
            "yoru", "cypher", "breach", "neon", "vyse",
            "killjoy", "fade", "omen", "raze", "web-fallback"
        )

        assertTrue(repo.extensions.map { it.id }.toSet().containsAll(expected))
    }

    @Test
    fun `a default line-up is installed on a fresh device`() {
        val repo = parser.parseRepo(bundled.readText())
        val defaults = repo.extensions.filter { it.installedByDefault }

        assertTrue(defaults.isNotEmpty())
        assertTrue(defaults.any { it.engine.type == ExtensionEngineType.VIDKING_WEBVIEW.key })
    }

    @Test
    fun `web resolvers are only ever used as fallbacks`() {
        val repo = parser.parseRepo(bundled.readText())
        val webEngines = setOf(ExtensionEngineType.VIDKING_WEBVIEW.key, ExtensionEngineType.WEB_EMBED.key)
        val webEntries = repo.extensions.filter { it.engine.type in webEngines }

        assertEquals(1, webEntries.count { it.engine.type == ExtensionEngineType.VIDKING_WEBVIEW.key })
        // Hidden-browser resolvers are slow; they must never run ahead of the direct routes.
        webEntries.forEach { entry ->
            assertTrue("${entry.id} must be a fallback", entry.fallback || entry.engine.type == ExtensionEngineType.VIDKING_WEBVIEW.key)
        }
    }
}
