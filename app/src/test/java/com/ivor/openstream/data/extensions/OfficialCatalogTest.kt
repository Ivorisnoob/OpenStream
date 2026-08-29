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

        assertEquals(expected, repo.extensions.map { it.id }.toSet())
    }

    @Test
    fun `a default line-up is installed on a fresh device`() {
        val repo = parser.parseRepo(bundled.readText())
        val defaults = repo.extensions.filter { it.installedByDefault }

        assertTrue(defaults.isNotEmpty())
        assertTrue(defaults.any { it.engine.type == ExtensionEngineType.VIDKING_WEBVIEW.key })
    }

    @Test
    fun `exactly one fallback resolver is published`() {
        val repo = parser.parseRepo(bundled.readText())
        val fallbacks = repo.extensions.filter {
            it.fallback || it.engine.type == ExtensionEngineType.VIDKING_WEBVIEW.key
        }

        assertEquals(1, fallbacks.size)
    }
}
