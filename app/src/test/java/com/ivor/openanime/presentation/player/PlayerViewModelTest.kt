package com.ivor.openanime.presentation.player

import app.cash.turbine.test
import com.ivor.openanime.data.remote.SubtitleApi
import com.ivor.openanime.data.remote.TmdbApi
import com.ivor.openanime.data.remote.model.AnimeDetailsDto
import com.ivor.openanime.data.remote.model.EpisodeDto
import com.ivor.openanime.data.remote.model.SeasonDetailsDto
import com.ivor.openanime.data.remote.model.SubtitleDto
import com.ivor.openanime.domain.repository.AnimeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tmdbApi: TmdbApi
    private lateinit var subtitleApi: SubtitleApi
    private lateinit var repository: AnimeRepository
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        tmdbApi = mockk()
        subtitleApi = mockk()
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty next episodes`() = runTest(testDispatcher) {
        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)

        viewModel.nextEpisodes.test {
            assertEquals(emptyList<EpisodeDto>(), awaitItem())
        }
    }

    @Test
    fun `initial state has loading episodes false`() = runTest(testDispatcher) {
        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)

        viewModel.isLoadingEpisodes.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `initial state has empty remote subtitles`() = runTest(testDispatcher) {
        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)

        viewModel.remoteSubtitles.test {
            assertEquals(emptyList<SubtitleDto>(), awaitItem())
        }
    }

    @Test
    fun `loadSeasonDetails for movie does not load episodes`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 1,
            movieTitle = "Test Movie",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        coEvery { repository.getMovieDetails(1) } returns Result.success(details)
        coEvery { subtitleApi.searchSubtitles(1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("movie", 1, 1, 1)
        advanceUntilIdle()

        viewModel.nextEpisodes.test {
            assertEquals(emptyList<EpisodeDto>(), awaitItem())
        }

        coVerify(exactly = 0) { tmdbApi.getSeasonDetails(any(), any()) }
    }

    @Test
    fun `loadSeasonDetails for tv show loads episodes after current episode`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 2,
            tvName = "Test TV Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val episodes = listOf(
            EpisodeDto(
                id = 101,
                name = "Episode 1",
                overview = "Overview 1",
                episodeNumber = 1,
                seasonNumber = 1,
                airDate = null,
                stillPath = null,
                voteAverage = 8.0,
                runtime = 24,
                productionCode = "",
                showId = 2,
                voteCount = 100
            ),
            EpisodeDto(
                id = 102,
                name = "Episode 2",
                overview = "Overview 2",
                episodeNumber = 2,
                seasonNumber = 1,
                airDate = null,
                stillPath = null,
                voteAverage = 8.5,
                runtime = 24,
                productionCode = "",
                showId = 2,
                voteCount = 100
            ),
            EpisodeDto(
                id = 103,
                name = "Episode 3",
                overview = "Overview 3",
                episodeNumber = 3,
                seasonNumber = 1,
                airDate = null,
                stillPath = null,
                voteAverage = 9.0,
                runtime = 24,
                productionCode = "",
                showId = 2,
                voteCount = 100
            )
        )

        val seasonDetails = SeasonDetailsDto(
            _id = "season_1",
            id = 1001,
            name = "Season 1",
            overview = "Season overview",
            seasonNumber = 1,
            airDate = null,
            posterPath = null,
            episodes = episodes
        )

        coEvery { repository.getAnimeDetails(2) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(2, 1) } returns seasonDetails
        coEvery { subtitleApi.searchSubtitles(2, 1, 1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 2, 1, 1)
        advanceUntilIdle()

        viewModel.nextEpisodes.test {
            val nextEpisodes = awaitItem()
            assertEquals(2, nextEpisodes.size)
            assertEquals(2, nextEpisodes[0].episodeNumber)
            assertEquals(3, nextEpisodes[1].episodeNumber)
        }
    }

    @Test
    fun `loadSeasonDetails adds to watch history for tv show`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 3,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val seasonDetails = SeasonDetailsDto(
            _id = "season_1",
            id = 1002,
            name = "Season 1",
            overview = "Overview",
            seasonNumber = 1,
            airDate = null,
            posterPath = null,
            episodes = emptyList()
        )

        coEvery { repository.getAnimeDetails(3) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(3, 1) } returns seasonDetails
        coEvery { subtitleApi.searchSubtitles(3, 1, 1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 3, 1, 1)
        advanceUntilIdle()

        coVerify { repository.addToWatchHistory(any()) }
    }

    @Test
    fun `loadSeasonDetails adds to watch history for movie`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 4,
            movieTitle = "Test Movie",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        coEvery { repository.getMovieDetails(4) } returns Result.success(details)
        coEvery { subtitleApi.searchSubtitles(4) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("movie", 4, 1, 1)
        advanceUntilIdle()

        coVerify { repository.addToWatchHistory(any()) }
    }

    @Test
    fun `loadSeasonDetails handles exception when fetching details`() = runTest(testDispatcher) {
        coEvery { repository.getAnimeDetails(5) } throws RuntimeException("Network error")
        coEvery { tmdbApi.getSeasonDetails(5, 1) } returns mockk()
        coEvery { subtitleApi.searchSubtitles(5, 1, 1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 5, 1, 1)
        advanceUntilIdle()

        // Should not crash and should continue loading episodes
        coVerify { tmdbApi.getSeasonDetails(5, 1) }
    }

    @Test
    fun `loadSeasonDetails handles exception when fetching episodes`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 6,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        coEvery { repository.getAnimeDetails(6) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(6, 1) } throws RuntimeException("Network error")
        coEvery { subtitleApi.searchSubtitles(6, 1, 1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 6, 1, 1)
        advanceUntilIdle()

        viewModel.nextEpisodes.test {
            assertEquals(emptyList<EpisodeDto>(), awaitItem())
        }

        viewModel.isLoadingEpisodes.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `loadSeasonDetails sets loading state during episode fetch`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 7,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val seasonDetails = SeasonDetailsDto(
            _id = "season_1",
            id = 1003,
            name = "Season 1",
            overview = "Overview",
            seasonNumber = 1,
            airDate = null,
            posterPath = null,
            episodes = emptyList()
        )

        coEvery { repository.getAnimeDetails(7) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(7, 1) } returns seasonDetails
        coEvery { subtitleApi.searchSubtitles(7, 1, 1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)

        viewModel.isLoadingEpisodes.test {
            assertEquals(false, awaitItem())
            viewModel.loadSeasonDetails("tv", 7, 1, 1)
            assertEquals(true, awaitItem())
            advanceUntilIdle()
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `loadSeasonDetails fetches subtitles for tv show`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 8,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val subtitles = buildJsonArray {
            add(buildJsonObject {
                put("id", "sub1")
                put("url", "http://example.com/sub1.vtt")
                put("display", "English")
            })
        }

        coEvery { repository.getAnimeDetails(8) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(8, 1) } returns mockk(relaxed = true)
        coEvery { subtitleApi.searchSubtitles(8, 1, 1) } returns subtitles

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 8, 1, 1)
        advanceUntilIdle()

        viewModel.remoteSubtitles.test {
            val subs = awaitItem()
            assertEquals(1, subs.size)
            assertEquals("sub1", subs[0].id)
            assertEquals("http://example.com/sub1.vtt", subs[0].url)
        }
    }

    @Test
    fun `loadSeasonDetails fetches subtitles for movie`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 9,
            movieTitle = "Test Movie",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val subtitles = buildJsonObject {
            put("en", buildJsonObject {
                put("id", "sub2")
                put("url", "http://example.com/sub2.vtt")
                put("display", "English")
            })
        }

        coEvery { repository.getMovieDetails(9) } returns Result.success(details)
        coEvery { subtitleApi.searchSubtitles(9) } returns subtitles

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("movie", 9, 1, 1)
        advanceUntilIdle()

        viewModel.remoteSubtitles.test {
            val subs = awaitItem()
            assertEquals(1, subs.size)
            assertEquals("sub2", subs[0].id)
        }
    }

    @Test
    fun `loadSeasonDetails handles subtitle fetch failure gracefully`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 10,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        coEvery { repository.getAnimeDetails(10) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(10, 1) } returns mockk(relaxed = true)
        coEvery { subtitleApi.searchSubtitles(10, 1, 1) } throws RuntimeException("Subtitle error")

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 10, 1, 1)
        advanceUntilIdle()

        viewModel.remoteSubtitles.test {
            assertEquals(emptyList<SubtitleDto>(), awaitItem())
        }
    }

    @Test
    fun `loadSeasonDetails filters episodes correctly when current episode is in middle`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 11,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val episodes = (1..10).map { episodeNum ->
            EpisodeDto(
                id = 200 + episodeNum,
                name = "Episode $episodeNum",
                overview = "Overview $episodeNum",
                episodeNumber = episodeNum,
                seasonNumber = 1,
                airDate = null,
                stillPath = null,
                voteAverage = 8.0,
                runtime = 24,
                productionCode = "",
                showId = 11,
                voteCount = 100
            )
        }

        val seasonDetails = SeasonDetailsDto(
            _id = "season_1",
            id = 1004,
            name = "Season 1",
            overview = "Overview",
            seasonNumber = 1,
            airDate = null,
            posterPath = null,
            episodes = episodes
        )

        coEvery { repository.getAnimeDetails(11) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(11, 1) } returns seasonDetails
        coEvery { subtitleApi.searchSubtitles(11, 1, 5) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 11, 1, 5)
        advanceUntilIdle()

        viewModel.nextEpisodes.test {
            val nextEpisodes = awaitItem()
            assertEquals(5, nextEpisodes.size)
            assertTrue(nextEpisodes.all { it.episodeNumber > 5 })
            assertEquals(6, nextEpisodes[0].episodeNumber)
            assertEquals(10, nextEpisodes[4].episodeNumber)
        }
    }

    @Test
    fun `loadSeasonDetails returns no episodes when current episode is last`() = runTest(testDispatcher) {
        val details = AnimeDetailsDto(
            id = 12,
            tvName = "Test Show",
            overview = "Overview",
            posterPath = null,
            backdropPath = null,
            voteAverage = 8.0
        )

        val episodes = listOf(
            EpisodeDto(
                id = 301,
                name = "Final Episode",
                overview = "The end",
                episodeNumber = 1,
                seasonNumber = 1,
                airDate = null,
                stillPath = null,
                voteAverage = 9.0,
                runtime = 24,
                productionCode = "",
                showId = 12,
                voteCount = 100
            )
        )

        val seasonDetails = SeasonDetailsDto(
            _id = "season_1",
            id = 1005,
            name = "Season 1",
            overview = "Overview",
            seasonNumber = 1,
            airDate = null,
            posterPath = null,
            episodes = episodes
        )

        coEvery { repository.getAnimeDetails(12) } returns Result.success(details)
        coEvery { tmdbApi.getSeasonDetails(12, 1) } returns seasonDetails
        coEvery { subtitleApi.searchSubtitles(12, 1, 1) } returns buildJsonArray {}

        viewModel = PlayerViewModel(tmdbApi, subtitleApi, repository)
        viewModel.loadSeasonDetails("tv", 12, 1, 1)
        advanceUntilIdle()

        viewModel.nextEpisodes.test {
            assertEquals(emptyList<EpisodeDto>(), awaitItem())
        }
    }
}