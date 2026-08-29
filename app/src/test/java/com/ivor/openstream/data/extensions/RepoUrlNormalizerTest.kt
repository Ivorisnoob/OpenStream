package com.ivor.openstream.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoUrlNormalizerTest {

    @Test
    fun `github blob links become raw links`() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/extensions/index.json",
            RepoUrlNormalizer.normalize(
                "https://github.com/owner/repo/blob/main/extensions/index.json"
            )
        )
    }

    @Test
    fun `github project links fall back to the conventional index path`() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/extensions/index.json",
            RepoUrlNormalizer.normalize("https://github.com/owner/repo")
        )
    }

    @Test
    fun `plain hosts get https and keep their path`() {
        assertEquals(
            "https://example.com/repo/index.json",
            RepoUrlNormalizer.normalize("  example.com/repo/index.json  ")
        )
    }

    @Test
    fun `http is upgraded to https`() {
        assertEquals(
            "https://example.com/index.json",
            RepoUrlNormalizer.normalize("http://example.com/index.json")
        )
    }

    @Test
    fun `nonsense input is rejected`() {
        assertNull(RepoUrlNormalizer.normalize(""))
        assertNull(RepoUrlNormalizer.normalize("not a url"))
        assertNull(RepoUrlNormalizer.normalize("repository"))
    }

    @Test
    fun `repo ids are stable and distinct per url`() {
        val first = RepoUrlNormalizer.repoId("https://example.com/a/index.json")
        val second = RepoUrlNormalizer.repoId("https://example.com/b/index.json")

        assertEquals(first, RepoUrlNormalizer.repoId("https://example.com/a/index.json"))
        assertNotEquals(first, second)
    }
}
