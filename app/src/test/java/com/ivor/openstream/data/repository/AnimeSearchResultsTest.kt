package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.model.AnimeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeSearchResultsTest {
    @Test
    fun `keeps Japanese animation from both TV and movie title searches`() {
        val results = AnimeSearchResults.prepare(
            tvShows = listOf(anime(id = 1, name = "Frieren")),
            movies = listOf(anime(id = 2, name = "Spirited Away")),
            sortBy = "unknown"
        )

        assertEquals(listOf("tv", "movie"), results.map(AnimeDto::mediaType))
        assertEquals(listOf("Frieren", "Spirited Away"), results.map(AnimeDto::name))
    }

    @Test
    fun `removes unrelated shows returned by TMDB title search`() {
        val results = AnimeSearchResults.prepare(
            tvShows = listOf(
                anime(id = 1, name = "Naruto"),
                anime(id = 2, name = "Random drama", genres = listOf(18)),
                anime(id = 3, name = "Western animation", language = "en")
            ),
            movies = emptyList(),
            sortBy = "popularity.desc"
        )

        assertEquals(listOf("Naruto"), results.map(AnimeDto::name))
    }

    @Test
    fun `sorts combined results using the selected option`() {
        val results = AnimeSearchResults.prepare(
            tvShows = listOf(
                anime(id = 1, name = "Older", popularity = 10.0),
                anime(id = 2, name = "Popular", popularity = 90.0)
            ),
            movies = emptyList(),
            sortBy = "popularity.desc"
        )

        assertEquals(listOf("Popular", "Older"), results.map(AnimeDto::name))
        assertTrue(results.all { it.mediaType == "tv" })
    }

    private fun anime(
        id: Int,
        name: String,
        genres: List<Int> = listOf(16),
        language: String = "ja",
        popularity: Double = 1.0
    ) = AnimeDto(
        id = id,
        tvName = name,
        genreIds = genres,
        originalLanguage = language,
        popularity = popularity
    )
}
