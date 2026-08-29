package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionEngine
import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.ExtensionStatus
import com.ivor.openstream.domain.model.ExtensionUsage
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MarketplaceSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceRankerTest {

    private val now = 1_756_000_000_000L

    private fun extension(
        id: String,
        installs: Long = 0L,
        installsLast7Days: Long = 0L,
        rating: Float = 0f,
        ratingCount: Int = 0,
        status: ExtensionStatus = ExtensionStatus.OK,
        priority: Int = 50,
        updatedAt: Long = 0L,
        tags: List<String> = emptyList(),
        language: String = "Multi",
        engineType: ExtensionEngineType = ExtensionEngineType.VIDKING_DIRECT,
        usage: ExtensionUsage = ExtensionUsage(),
        installed: Boolean = false
    ) = MarketplaceExtension(
        manifest = ExtensionManifest(
            id = id,
            repoId = "official",
            name = id.replaceFirstChar { it.uppercase() },
            description = "Source $id",
            language = language,
            tags = tags,
            status = status,
            installs = installs,
            installsLast7Days = installsLast7Days,
            rating = rating,
            ratingCount = ratingCount,
            updatedAt = updatedAt,
            engine = ExtensionEngine(type = engineType, endpoint = "e", priority = priority)
        ),
        isInstalled = installed,
        isEnabled = installed,
        usage = usage
    )

    @Test
    fun `popular sort favours install count`() {
        val ranked = MarketplaceRanker.sort(
            listOf(
                extension("small", installs = 100),
                extension("huge", installs = 900_000),
                extension("medium", installs = 5_000)
            ),
            MarketplaceSort.POPULAR,
            now
        )

        assertEquals(listOf("huge", "medium", "small"), ranked.map { it.manifest.id })
    }

    @Test
    fun `a source flagged down drops below a healthy one with fewer installs`() {
        val ranked = MarketplaceRanker.sort(
            listOf(
                extension("broken", installs = 500_000, status = ExtensionStatus.DOWN),
                extension("healthy", installs = 2_000)
            ),
            MarketplaceSort.POPULAR,
            now
        )

        assertEquals("healthy", ranked.first().manifest.id)
    }

    @Test
    fun `local success history breaks ties between equally popular sources`() {
        val ranked = MarketplaceRanker.sort(
            listOf(
                extension("flaky", installs = 1_000, usage = ExtensionUsage(successes = 1, failures = 9)),
                extension("reliable", installs = 1_000, usage = ExtensionUsage(successes = 9, failures = 1))
            ),
            MarketplaceSort.POPULAR,
            now
        )

        assertEquals("reliable", ranked.first().manifest.id)
    }

    @Test
    fun `curation order ranks a repository that publishes no telemetry`() {
        val ranked = MarketplaceRanker.sort(
            listOf(
                extension("last", priority = 8),
                extension("first", priority = 0),
                extension("middle", priority = 4)
            ),
            MarketplaceSort.POPULAR,
            now
        )

        assertEquals(listOf("first", "middle", "last"), ranked.map { it.manifest.id })
    }

    @Test
    fun `trending favours recent installs and fresh updates`() {
        val ranked = MarketplaceRanker.sort(
            listOf(
                extension("classic", installs = 900_000, updatedAt = now - 400L * 24 * 60 * 60 * 1000),
                extension("rising", installs = 4_000, installsLast7Days = 3_500, updatedAt = now - 24L * 60 * 60 * 1000)
            ),
            MarketplaceSort.TRENDING,
            now
        )

        assertEquals("rising", ranked.first().manifest.id)
    }

    @Test
    fun `recent sort orders by publish date`() {
        val ranked = MarketplaceRanker.sort(
            listOf(
                extension("old", updatedAt = now - 90L * 24 * 60 * 60 * 1000),
                extension("new", updatedAt = now)
            ),
            MarketplaceSort.RECENT,
            now
        )

        assertEquals(listOf("new", "old"), ranked.map { it.manifest.id })
    }

    @Test
    fun `name sort is alphabetical and stable`() {
        val ranked = MarketplaceRanker.sort(
            listOf(extension("zulu"), extension("alpha"), extension("mike")),
            MarketplaceSort.NAME,
            now
        )

        assertEquals(listOf("alpha", "mike", "zulu"), ranked.map { it.manifest.id })
    }

    @Test
    fun `search matches name description tags and language`() {
        val catalog = listOf(
            extension("fade", language = "Hindi", tags = listOf("movies")),
            extension("killjoy", language = "German", tags = listOf("series"))
        )

        assertEquals(listOf("fade"), MarketplaceRanker.search(catalog, "hindi").map { it.manifest.id })
        assertEquals(listOf("killjoy"), MarketplaceRanker.search(catalog, "SERIES").map { it.manifest.id })
        assertEquals(2, MarketplaceRanker.search(catalog, "  ").size)
    }

    @Test
    fun `tag filter also accepts a language as a category`() {
        val catalog = listOf(
            extension("fade", language = "Hindi", tags = listOf("movies")),
            extension("yoru", tags = listOf("movies", "fast"))
        )

        assertEquals(2, MarketplaceRanker.filterByTag(catalog, "movies").size)
        assertEquals(listOf("fade"), MarketplaceRanker.filterByTag(catalog, "hindi").map { it.manifest.id })
        assertEquals(2, MarketplaceRanker.filterByTag(catalog, null).size)
    }

    @Test
    fun `tags are ordered by how many extensions use them`() {
        val catalog = listOf(
            extension("a", tags = listOf("movies", "fast")),
            extension("b", tags = listOf("movies")),
            extension("c", tags = listOf("movies", "series"))
        )

        assertEquals(listOf("movies", "fast", "series"), MarketplaceRanker.tags(catalog))
    }

    @Test
    fun `top charts exclude broken and unsupported extensions`() {
        val charts = MarketplaceRanker.topCharts(
            listOf(
                extension("down", installs = 900_000, status = ExtensionStatus.DOWN),
                extension("unsupported", installs = 800_000, engineType = ExtensionEngineType.UNSUPPORTED),
                extension("good", installs = 1_000)
            ),
            limit = 5,
            now = now
        )

        assertEquals(listOf("good"), charts.map { it.manifest.id })
    }

    @Test
    fun `charts are capped at the requested size`() {
        val catalog = (1..12).map { extension("source$it", installs = it * 100L) }
        assertEquals(5, MarketplaceRanker.topCharts(catalog, now = now).size)
    }

    @Test
    fun `usage needs a minimum sample before it is trusted`() {
        assertNull(ExtensionUsage(successes = 2, failures = 1).successRate)
        assertEquals(0.8f, requireNotNull(ExtensionUsage(successes = 8, failures = 2).successRate), 0.0001f)
    }

    @Test
    fun `update state is derived from the published version code`() {
        val installed = extension("yoru", installed = true).copy(installedVersionCode = 1)
        assertTrue(installed.copy(manifest = installed.manifest.copy(versionCode = 3)).hasUpdate)
        assertFalse(installed.copy(manifest = installed.manifest.copy(versionCode = 1)).hasUpdate)
    }
}
