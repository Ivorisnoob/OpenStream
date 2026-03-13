package com.ivor.openanime.data.repository

import android.content.SharedPreferences
import com.ivor.openanime.data.remote.TmdbApi
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.data.remote.model.SeasonDetailsDto
import com.ivor.openanime.data.remote.model.TmdbResponse
import com.ivor.openanime.data.remote.model.EpisodeDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnimeRepositoryImplTest {

    private lateinit var api: TmdbApi
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var json: Json
    private lateinit var repository: AnimeRepositoryImpl

    @Before
    fun setup() {
        api = mockk()
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)
        json = Json { ignoreUnknownKeys = true }

        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { editor.apply() } returns Unit

        repository = AnimeRepositoryImpl(api, sharedPreferences, json)
    }

    @Test
    fun `getPopularAnime returns success with anime list`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 1, tvName = "Anime 1", overview = "Overview 1"),
            AnimeDto(id = 2, tvName = "Anime 2", overview = "Overview 2")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 2)

        coEvery { api.getPopularAnime(page = 1) } returns response

        val result = repository.getPopularAnime(page = 1)

        assertTrue(result.isSuccess)
        assertEquals(animeList, result.getOrNull())
        coVerify { api.getPopularAnime(page = 1) }
    }

    @Test
    fun `getPopularAnime returns failure when api throws exception`() = runTest {
        coEvery { api.getPopularAnime(page = 1) } throws RuntimeException("Network error")

        val result = repository.getPopularAnime(page = 1)

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getTrendingAnime returns success with anime list`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 3, tvName = "Trending 1", overview = "Overview 3")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 1)

        coEvery { api.getTrendingAnime("day", 1) } returns response

        val result = repository.getTrendingAnime(timeWindow = "day", page = 1)

        assertTrue(result.isSuccess)
        assertEquals(animeList, result.getOrNull())
    }

    @Test
    fun `getTopRatedAnime returns success with anime list`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 4, tvName = "Top Rated", overview = "Overview 4")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 1)

        coEvery { api.getTopRatedAnime(1) } returns response

        val result = repository.getTopRatedAnime(page = 1)

        assertTrue(result.isSuccess)
        assertEquals(animeList, result.getOrNull())
    }

    @Test
    fun `getAiringTodayAnime returns success with anime list`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 5, tvName = "Airing Today", overview = "Overview 5")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 1)

        coEvery { api.getAiringTodayAnime(1) } returns response

        val result = repository.getAiringTodayAnime(page = 1)

        assertTrue(result.isSuccess)
        assertEquals(animeList, result.getOrNull())
    }

    @Test
    fun `searchAnime with movie filter returns only movies`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 6, movieTitle = "Movie 1", overview = "Overview 6", mediaType = "movie")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 1)

        coEvery { api.searchMovie("query", 1) } returns response

        val result = repository.searchAnime(query = "query", page = 1, filter = "movie")

        assertTrue(result.isSuccess)
        val results = result.getOrNull()!!
        assertEquals(1, results.size)
        assertEquals("movie", results[0].mediaType)
    }

    @Test
    fun `searchAnime with tv filter returns only tv shows`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 7, tvName = "TV Show 1", overview = "Overview 7", mediaType = "tv")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 1)

        coEvery { api.searchTv("query", 1) } returns response

        val result = repository.searchAnime(query = "query", page = 1, filter = "tv")

        assertTrue(result.isSuccess)
        val results = result.getOrNull()!!
        assertEquals(1, results.size)
        assertEquals("tv", results[0].mediaType)
    }

    @Test
    fun `searchAnime with all filter returns filtered multi results`() = runTest {
        val animeList = listOf(
            AnimeDto(id = 8, tvName = "TV Show", overview = "Overview 8", mediaType = "tv"),
            AnimeDto(id = 9, movieTitle = "Movie", overview = "Overview 9", mediaType = "movie"),
            AnimeDto(id = 10, tvName = "Person", overview = "Overview 10", mediaType = "person")
        )
        val response = TmdbResponse(page = 1, results = animeList, totalPages = 1, totalResults = 3)

        coEvery { api.searchMulti("query", 1) } returns response

        val result = repository.searchAnime(query = "query", page = 1, filter = "all")

        assertTrue(result.isSuccess)
        val results = result.getOrNull()!!
        assertEquals(2, results.size) // Should filter out person
        assertTrue(results.all { it.mediaType == "tv" || it.mediaType == "movie" })
    }

    @Test
    fun `getAnimeDetails returns success with details`() = runTest {
        val details = AnimeDetailsDto(
            id = 11,
            tvName = "Anime Details",
            overview = "Overview 11",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 8.5
        )

        coEvery { api.getAnimeDetails(11) } returns details

        val result = repository.getAnimeDetails(id = 11)

        assertTrue(result.isSuccess)
        assertEquals(details, result.getOrNull())
    }

    @Test
    fun `getMovieDetails returns success with details`() = runTest {
        val details = AnimeDetailsDto(
            id = 12,
            movieTitle = "Movie Details",
            overview = "Overview 12",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            voteAverage = 9.0
        )

        coEvery { api.getMovieDetails(12) } returns details

        val result = repository.getMovieDetails(id = 12)

        assertTrue(result.isSuccess)
        assertEquals(details, result.getOrNull())
    }

    @Test
    fun `getMediaDetails with movie mediaType calls getMovieDetails`() = runTest {
        val details = AnimeDetailsDto(
            id = 13,
            movieTitle = "Movie",
            overview = "Overview 13",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        coEvery { api.getMovieDetails(13) } returns details

        val result = repository.getMediaDetails(id = 13, mediaType = "movie")

        assertTrue(result.isSuccess)
        assertEquals(details, result.getOrNull())
        coVerify { api.getMovieDetails(13) }
        coVerify(exactly = 0) { api.getAnimeDetails(any()) }
    }

    @Test
    fun `getMediaDetails with tv mediaType calls getAnimeDetails`() = runTest {
        val details = AnimeDetailsDto(
            id = 14,
            tvName = "TV Show",
            overview = "Overview 14",
            posterPath = null,
            backdropPath = null,
            voteAverage = 7.5
        )

        coEvery { api.getAnimeDetails(14) } returns details

        val result = repository.getMediaDetails(id = 14, mediaType = "tv")

        assertTrue(result.isSuccess)
        assertEquals(details, result.getOrNull())
        coVerify { api.getAnimeDetails(14) }
        coVerify(exactly = 0) { api.getMovieDetails(any()) }
    }

    @Test
    fun `getSeasonDetails returns success with season details`() = runTest {
        val seasonDetails = SeasonDetailsDto(
            id = 100,
            name = "Season 1",
            overview = "Season overview",
            seasonNumber = 1,
            airDate = "2024-01-01",
            posterPath = "/season_poster.jpg",
            episodes = listOf(
                EpisodeDto(
                    id = 101,
                    name = "Episode 1",
                    overview = "Episode overview",
                    episodeNumber = 1,
                    seasonNumber = 1,
                    airDate = "2024-01-01",
                    stillPath = "/still.jpg",
                    voteAverage = 8.0,
                    runtime = 24
                )
            )
        )

        coEvery { api.getSeasonDetails(id = 15, seasonNumber = 1) } returns seasonDetails

        val result = repository.getSeasonDetails(animeId = 15, seasonNumber = 1)

        assertTrue(result.isSuccess)
        assertEquals(seasonDetails, result.getOrNull())
    }

    @Test
    fun `addToWatchHistory adds new anime to the beginning of history`() = runTest {
        val anime = AnimeDto(id = 20, tvName = "New Anime", overview = "New")

        every { sharedPreferences.getString("watch_history_list", null) } returns null

        repository.addToWatchHistory(anime)

        val slot = slot<String>()
        verify { editor.putString("watch_history_list", capture(slot)) }

        val savedList = json.decodeFromString<List<AnimeDto>>(slot.captured)
        assertEquals(1, savedList.size)
        assertEquals(anime, savedList[0])
    }

    @Test
    fun `addToWatchHistory moves existing anime to the beginning`() = runTest {
        val anime1 = AnimeDto(id = 21, tvName = "Anime 1", overview = "Overview 1")
        val anime2 = AnimeDto(id = 22, tvName = "Anime 2", overview = "Overview 2")
        val existingHistory = listOf(anime1, anime2)

        every { sharedPreferences.getString("watch_history_list", null) } returns json.encodeToString(existingHistory)

        repository.addToWatchHistory(anime1)

        val slot = slot<String>()
        verify { editor.putString("watch_history_list", capture(slot)) }

        val savedList = json.decodeFromString<List<AnimeDto>>(slot.captured)
        assertEquals(2, savedList.size)
        assertEquals(anime1, savedList[0])
        assertEquals(anime2, savedList[1])
    }

    @Test
    fun `addToWatchHistory limits history to 50 items`() = runTest {
        val existingHistory = (1..50).map {
            AnimeDto(id = it, tvName = "Anime $it", overview = "Overview $it")
        }
        val newAnime = AnimeDto(id = 100, tvName = "New Anime", overview = "New")

        every { sharedPreferences.getString("watch_history_list", null) } returns json.encodeToString(existingHistory)

        repository.addToWatchHistory(newAnime)

        val slot = slot<String>()
        verify { editor.putString("watch_history_list", capture(slot)) }

        val savedList = json.decodeFromString<List<AnimeDto>>(slot.captured)
        assertEquals(50, savedList.size)
        assertEquals(newAnime, savedList[0])
        assertEquals(existingHistory[0], savedList[1])
        assertEquals(existingHistory[48], savedList[49])
    }

    @Test
    fun `getWatchHistory returns empty list when no history exists`() = runTest {
        every { sharedPreferences.getString("watch_history_list", null) } returns null

        val result = repository.getWatchHistory()

        assertEquals(emptyList<AnimeDto>(), result)
    }

    @Test
    fun `getWatchHistory returns list of anime from preferences`() = runTest {
        val history = listOf(
            AnimeDto(id = 30, tvName = "Anime 1", overview = "Overview 1"),
            AnimeDto(id = 31, tvName = "Anime 2", overview = "Overview 2")
        )

        every { sharedPreferences.getString("watch_history_list", null) } returns json.encodeToString(history)

        val result = repository.getWatchHistory()

        assertEquals(history, result)
    }

    @Test
    fun `getWatchHistory returns empty list when json parsing fails`() = runTest {
        every { sharedPreferences.getString("watch_history_list", null) } returns "invalid json"

        val result = repository.getWatchHistory()

        assertEquals(emptyList<AnimeDto>(), result)
    }

    @Test
    fun `clearWatchHistory removes history from preferences`() = runTest {
        repository.clearWatchHistory()

        verify { editor.remove("watch_history_list") }
        verify { editor.apply() }
    }

    @Test
    fun `addToWatchHistory handles concurrent additions correctly`() = runTest {
        val anime1 = AnimeDto(id = 40, tvName = "Anime 1", overview = "Overview 1")
        val anime2 = AnimeDto(id = 41, tvName = "Anime 2", overview = "Overview 2")

        every { sharedPreferences.getString("watch_history_list", null) } returns null

        repository.addToWatchHistory(anime1)

        val firstSlot = slot<String>()
        verify { editor.putString("watch_history_list", capture(firstSlot)) }

        every { sharedPreferences.getString("watch_history_list", null) } returns firstSlot.captured

        repository.addToWatchHistory(anime2)

        val secondSlot = slot<String>()
        verify(exactly = 2) { editor.putString("watch_history_list", capture(secondSlot)) }

        val savedList = json.decodeFromString<List<AnimeDto>>(secondSlot.captured)
        assertEquals(2, savedList.size)
        assertEquals(anime2, savedList[0])
        assertEquals(anime1, savedList[1])
    }
}