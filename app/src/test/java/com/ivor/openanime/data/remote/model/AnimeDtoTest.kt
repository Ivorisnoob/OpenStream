package com.ivor.openanime.data.remote.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeDtoTest {

    @Test
    fun `name property returns movieTitle when available`() {
        val anime = AnimeDto(
            id = 1,
            movieTitle = "Movie Title",
            tvName = "TV Name",
            overview = "Overview"
        )
        assertEquals("Movie Title", anime.name)
    }

    @Test
    fun `name property returns tvName when movieTitle is null`() {
        val anime = AnimeDto(
            id = 1,
            movieTitle = null,
            tvName = "TV Name",
            overview = "Overview"
        )
        assertEquals("TV Name", anime.name)
    }

    @Test
    fun `name property returns empty string when both titles are null`() {
        val anime = AnimeDto(
            id = 1,
            movieTitle = null,
            tvName = null,
            overview = "Overview"
        )
        assertEquals("", anime.name)
    }

    @Test
    fun `date property returns releaseDate when available`() {
        val anime = AnimeDto(
            id = 1,
            releaseDate = "2024-01-01",
            firstAirDate = "2024-02-01",
            overview = "Overview"
        )
        assertEquals("2024-01-01", anime.date)
    }

    @Test
    fun `date property returns firstAirDate when releaseDate is null`() {
        val anime = AnimeDto(
            id = 1,
            releaseDate = null,
            firstAirDate = "2024-02-01",
            overview = "Overview"
        )
        assertEquals("2024-02-01", anime.date)
    }

    @Test
    fun `date property returns empty string when both dates are null`() {
        val anime = AnimeDto(
            id = 1,
            releaseDate = null,
            firstAirDate = null,
            overview = "Overview"
        )
        assertEquals("", anime.date)
    }

    @Test
    fun `isMovie returns true when mediaType is movie`() {
        val anime = AnimeDto(
            id = 1,
            mediaType = "movie",
            overview = "Overview"
        )
        assertTrue(anime.isMovie)
    }

    @Test
    fun `isMovie returns false when mediaType is tv`() {
        val anime = AnimeDto(
            id = 1,
            mediaType = "tv",
            overview = "Overview"
        )
        assertFalse(anime.isMovie)
    }

    @Test
    fun `isMovie returns false when mediaType is null`() {
        val anime = AnimeDto(
            id = 1,
            mediaType = null,
            overview = "Overview"
        )
        assertFalse(anime.isMovie)
    }

    @Test
    fun `default mediaType is tv`() {
        val anime = AnimeDto(
            id = 1,
            overview = "Overview"
        )
        assertEquals("tv", anime.mediaType)
    }
}

class AnimeDetailsDtoTest {

    @Test
    fun `name property returns movieTitle when available`() {
        val details = AnimeDetailsDto(
            id = 1,
            movieTitle = "Movie Title",
            tvName = "TV Name",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5
        )
        assertEquals("Movie Title", details.name)
    }

    @Test
    fun `name property returns tvName when movieTitle is null`() {
        val details = AnimeDetailsDto(
            id = 1,
            movieTitle = null,
            tvName = "TV Name",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5
        )
        assertEquals("TV Name", details.name)
    }

    @Test
    fun `name property returns empty string when both titles are null`() {
        val details = AnimeDetailsDto(
            id = 1,
            movieTitle = null,
            tvName = null,
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5
        )
        assertEquals("", details.name)
    }

    @Test
    fun `date property returns releaseDate when available`() {
        val details = AnimeDetailsDto(
            id = 1,
            releaseDate = "2024-01-01",
            firstAirDate = "2024-02-01",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5
        )
        assertEquals("2024-01-01", details.date)
    }

    @Test
    fun `date property returns firstAirDate when releaseDate is null`() {
        val details = AnimeDetailsDto(
            id = 1,
            releaseDate = null,
            firstAirDate = "2024-02-01",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5
        )
        assertEquals("2024-02-01", details.date)
    }

    @Test
    fun `date property returns empty string when both dates are null`() {
        val details = AnimeDetailsDto(
            id = 1,
            releaseDate = null,
            firstAirDate = null,
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.5
        )
        assertEquals("", details.date)
    }
}

class AnimeDetailsDtoExtensionsTest {

    @Test
    fun `toAnimeDto converts all fields correctly for tv show`() {
        val details = AnimeDetailsDto(
            id = 123,
            tvName = "Test TV Show",
            movieTitle = null,
            overview = "Test overview",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            firstAirDate = "2024-01-01",
            releaseDate = null,
            voteAverage = 8.5,
            numberOfSeasons = 2,
            numberOfEpisodes = 24
        )

        val anime = details.toAnimeDto("tv")

        assertEquals(123, anime.id)
        assertEquals("Test TV Show", anime.tvName)
        assertEquals(null, anime.movieTitle)
        assertEquals("Test overview", anime.overview)
        assertEquals("/poster.jpg", anime.posterPath)
        assertEquals("/backdrop.jpg", anime.backdropPath)
        assertEquals("2024-01-01", anime.firstAirDate)
        assertEquals(null, anime.releaseDate)
        assertEquals(8.5, anime.voteAverage, 0.001)
        assertEquals("tv", anime.mediaType)
    }

    @Test
    fun `toAnimeDto converts all fields correctly for movie`() {
        val details = AnimeDetailsDto(
            id = 456,
            tvName = null,
            movieTitle = "Test Movie",
            overview = "Movie overview",
            posterPath = "/movie_poster.jpg",
            backdropPath = "/movie_backdrop.jpg",
            firstAirDate = null,
            releaseDate = "2024-06-15",
            voteAverage = 9.0,
            runtime = 120
        )

        val anime = details.toAnimeDto("movie")

        assertEquals(456, anime.id)
        assertEquals(null, anime.tvName)
        assertEquals("Test Movie", anime.movieTitle)
        assertEquals("Movie overview", anime.overview)
        assertEquals("/movie_poster.jpg", anime.posterPath)
        assertEquals("/movie_backdrop.jpg", anime.backdropPath)
        assertEquals(null, anime.firstAirDate)
        assertEquals("2024-06-15", anime.releaseDate)
        assertEquals(9.0, anime.voteAverage, 0.001)
        assertEquals("movie", anime.mediaType)
    }

    @Test
    fun `toAnimeDto preserves null values`() {
        val details = AnimeDetailsDto(
            id = 789,
            tvName = null,
            movieTitle = null,
            overview = "Minimal details",
            posterPath = null,
            backdropPath = null,
            voteAverage = 7.0
        )

        val anime = details.toAnimeDto("tv")

        assertEquals(789, anime.id)
        assertEquals(null, anime.tvName)
        assertEquals(null, anime.movieTitle)
        assertEquals("Minimal details", anime.overview)
        assertEquals(null, anime.posterPath)
        assertEquals(null, anime.backdropPath)
        assertEquals(null, anime.firstAirDate)
        assertEquals(null, anime.releaseDate)
        assertEquals(7.0, anime.voteAverage, 0.001)
        assertEquals("tv", anime.mediaType)
    }

    @Test
    fun `toAnimeDto handles edge case with both title types present`() {
        val details = AnimeDetailsDto(
            id = 999,
            tvName = "TV Version",
            movieTitle = "Movie Version",
            overview = "Has both titles",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            firstAirDate = "2024-01-01",
            releaseDate = "2024-02-01",
            voteAverage = 8.0
        )

        val anime = details.toAnimeDto("tv")

        assertEquals("TV Version", anime.tvName)
        assertEquals("Movie Version", anime.movieTitle)
        assertEquals("2024-01-01", anime.firstAirDate)
        assertEquals("2024-02-01", anime.releaseDate)
        assertEquals("tv", anime.mediaType)
    }
}