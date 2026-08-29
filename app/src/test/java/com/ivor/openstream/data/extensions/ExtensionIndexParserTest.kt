package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.domain.model.ExtensionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionIndexParserTest {

    private val parser = ExtensionIndexParser()

    @Test
    fun `parses repository object with inline extensions`() {
        val repo = parser.parseRepo(
            """
            {
              "manifestVersion": 1,
              "name": "Test repo",
              "description": "Sources",
              "extensions": [
                {
                  "id": "alpha",
                  "name": "Alpha",
                  "engine": { "type": "vidking-direct", "endpoint": "cdn/sources", "priority": 3 }
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("Test repo", repo.name)
        assertEquals(1, repo.extensions.size)
        assertEquals("alpha", repo.extensions.first().id)
    }

    @Test
    fun `parses bare array index like Mihon style repositories`() {
        val repo = parser.parseRepo(
            """
            [
              { "id": "alpha", "name": "Alpha", "engine": { "type": "vidking-direct", "endpoint": "a" } },
              { "id": "beta", "name": "Beta", "engine": { "type": "vidking-webview" } }
            ]
            """.trimIndent()
        )

        assertEquals(2, repo.extensions.size)
    }

    @Test
    fun `keeps good entries when one entry is malformed`() {
        val entries = parser.parseExtensionList(
            """
            [
              { "id": "alpha", "name": "Alpha", "engine": { "type": "vidking-direct", "endpoint": "a" } },
              { "name": "missing id and engine" },
              { "id": "beta", "name": "Beta", "engine": { "type": "vidking-webview" } }
            ]
            """.trimIndent()
        )

        assertEquals(listOf("alpha", "beta"), entries.map { it.id })
    }

    @Test
    fun `maps entry fields onto a manifest`() {
        val entry = parser.parseExtensionList(
            """
            [{
              "id": " fade ",
              "name": "Fade",
              "description": "Hindi route",
              "version": "2.1.0",
              "versionCode": 7,
              "authors": ["OpenStream"],
              "language": "Hindi",
              "tags": ["Movies", "hindi", "movies"],
              "status": 2,
              "installs": 4200,
              "rating": 4.6,
              "ratingCount": 90,
              "updatedAt": "2026-08-01",
              "engine": {
                "type": "vidking-direct",
                "endpoint": "hdmovie/sources-with-title",
                "priority": 6,
                "qualityFilter": "Hindi"
              }
            }]
            """.trimIndent()
        ).single()

        val manifest = parser.toManifest(entry, repoId = "official")
        assertNotNull(manifest)
        requireNotNull(manifest)

        assertEquals("fade", manifest.id)
        assertEquals("official/fade", manifest.key)
        assertEquals(7, manifest.versionCode)
        assertEquals(ExtensionStatus.SLOW, manifest.status)
        assertEquals(listOf("movies", "hindi"), manifest.tags)
        assertEquals(ExtensionEngineType.VIDKING_DIRECT, manifest.engine.type)
        assertEquals("Hindi", manifest.engine.qualityFilter)
        assertEquals(4200L, manifest.installs)
        assertTrue(manifest.isSupported)
        assertTrue(manifest.updatedAt > 0L)
    }

    @Test
    fun `unknown engines are parsed but not runnable`() {
        val entry = parser.parseExtensionList(
            """[{ "id": "x", "name": "X", "engine": { "type": "torrent-dht" } }]"""
        ).single()

        val manifest = requireNotNull(parser.toManifest(entry, repoId = "custom"))
        assertEquals(ExtensionEngineType.UNSUPPORTED, manifest.engine.type)
        assertFalse(manifest.isSupported)
    }

    @Test
    fun `direct engine without an endpoint cannot run`() {
        val entry = parser.parseExtensionList(
            """[{ "id": "x", "name": "X", "engine": { "type": "vidking-direct" } }]"""
        ).single()

        val manifest = requireNotNull(parser.toManifest(entry, repoId = "custom"))
        assertFalse(manifest.isSupported)
    }

    @Test
    fun `manifests declaring a future api version are not installable`() {
        val entry = parser.parseExtensionList(
            """[{ "id": "x", "name": "X", "apiVersion": 99, "engine": { "type": "vidking-webview" } }]"""
        ).single()

        val manifest = requireNotNull(parser.toManifest(entry, repoId = "custom"))
        assertFalse(manifest.isSupported)
    }

    @Test
    fun `timestamps accept iso dates epoch millis and nothing`() {
        assertEquals(0L, parser.parseTimestamp(null))
        assertEquals(0L, parser.parseTimestamp("not a date"))
        assertEquals(1_700_000_000_000L, parser.parseTimestamp("1700000000000"))
        assertTrue(parser.parseTimestamp("2026-08-01") > 0L)
    }

    @Test
    fun `entries without an id are dropped`() {
        val manifest = parser.toManifest(
            ExtensionEntryDto(
                id = "  ",
                name = "Nameless",
                engine = ExtensionEngineDto(type = "vidking-webview")
            ),
            repoId = "custom"
        )
        assertNull(manifest)
    }
}
