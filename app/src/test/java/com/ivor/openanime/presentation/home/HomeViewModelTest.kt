package com.ivor.openanime.presentation.home

import app.cash.turbine.test
import com.ivor.openanime.data.remote.model.AnimeDto
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: AnimeRepository
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest(testDispatcher) {
        coEvery { repository.getTrendingAnime() } returns Result.success(emptyList())
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(any()) } returns Result.success(emptyList())
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadData success emits Success state with all data`() = runTest(testDispatcher) {
        val trendingList = listOf(
            AnimeDto(id = 1, tvName = "Trending 1", overview = "Overview 1")
        )
        val topRatedList = listOf(
            AnimeDto(id = 2, tvName = "Top Rated 1", overview = "Overview 2")
        )
        val popularList = listOf(
            AnimeDto(id = 3, tvName = "Popular 1", overview = "Overview 3")
        )
        val airingTodayList = listOf(
            AnimeDto(id = 4, tvName = "Airing 1", overview = "Overview 4")
        )

        coEvery { repository.getTrendingAnime() } returns Result.success(trendingList)
        coEvery { repository.getTopRatedAnime() } returns Result.success(topRatedList)
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(popularList)
        coEvery { repository.getAiringTodayAnime() } returns Result.success(airingTodayList)

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
            val successState = state as HomeUiState.Success
            assertEquals(trendingList, successState.trending)
            assertEquals(topRatedList, successState.topRated)
            assertEquals(popularList, successState.popular)
            assertEquals(airingTodayList, successState.airingToday)
        }
    }

    @Test
    fun `loadData with all empty lists emits Error state`() = runTest(testDispatcher) {
        coEvery { repository.getTrendingAnime() } returns Result.success(emptyList())
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(emptyList())
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Error)
            assertEquals("Failed to load data", (state as HomeUiState.Error).message)
        }
    }

    @Test
    fun `loadData with partial data emits Success state`() = runTest(testDispatcher) {
        val popularList = listOf(
            AnimeDto(id = 5, tvName = "Popular Only", overview = "Overview 5")
        )

        coEvery { repository.getTrendingAnime() } returns Result.success(emptyList())
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(popularList)
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
            val successState = state as HomeUiState.Success
            assertEquals(emptyList<AnimeDto>(), successState.trending)
            assertEquals(emptyList<AnimeDto>(), successState.topRated)
            assertEquals(popularList, successState.popular)
            assertEquals(emptyList<AnimeDto>(), successState.airingToday)
        }
    }

    @Test
    fun `loadData with exception emits Error state with exception message`() = runTest(testDispatcher) {
        coEvery { repository.getTrendingAnime() } throws RuntimeException("Network error")
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(emptyList())
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Error)
            assertEquals("Network error", (state as HomeUiState.Error).message)
        }
    }

    @Test
    fun `loadData fetches all categories in parallel`() = runTest(testDispatcher) {
        val trendingList = listOf(AnimeDto(id = 10, tvName = "Trending", overview = "Overview"))
        val topRatedList = listOf(AnimeDto(id = 11, tvName = "Top Rated", overview = "Overview"))
        val popularList = listOf(AnimeDto(id = 12, tvName = "Popular", overview = "Overview"))
        val airingTodayList = listOf(AnimeDto(id = 13, tvName = "Airing", overview = "Overview"))

        coEvery { repository.getTrendingAnime() } returns Result.success(trendingList)
        coEvery { repository.getTopRatedAnime() } returns Result.success(topRatedList)
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(popularList)
        coEvery { repository.getAiringTodayAnime() } returns Result.success(airingTodayList)

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getTrendingAnime() }
        coVerify(exactly = 1) { repository.getTopRatedAnime() }
        coVerify(exactly = 1) { repository.getPopularAnime(page = 1) }
        coVerify(exactly = 1) { repository.getAiringTodayAnime() }
    }

    @Test
    fun `loadData can be called multiple times`() = runTest(testDispatcher) {
        val animeList = listOf(AnimeDto(id = 20, tvName = "Test Anime", overview = "Overview"))

        coEvery { repository.getTrendingAnime() } returns Result.success(animeList)
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(emptyList())
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.loadData()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.getTrendingAnime() }
    }

    @Test
    fun `loadData with failure result emits Success with empty list for that category`() = runTest(testDispatcher) {
        val popularList = listOf(AnimeDto(id = 30, tvName = "Popular", overview = "Overview"))

        coEvery { repository.getTrendingAnime() } returns Result.failure(RuntimeException("Failed"))
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(popularList)
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
            val successState = state as HomeUiState.Success
            assertEquals(emptyList<AnimeDto>(), successState.trending)
            assertEquals(popularList, successState.popular)
        }
    }

    @Test
    fun `loadData emits Loading before fetching data`() = runTest(testDispatcher) {
        val animeList = listOf(AnimeDto(id = 40, tvName = "Test", overview = "Overview"))

        coEvery { repository.getTrendingAnime() } returns Result.success(animeList)
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(emptyList())
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)

        viewModel.uiState.test {
            assertEquals(HomeUiState.Loading, awaitItem())
            advanceUntilIdle()
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
        }
    }

    @Test
    fun `loadData handles null exception message gracefully`() = runTest(testDispatcher) {
        coEvery { repository.getTrendingAnime() } throws RuntimeException()
        coEvery { repository.getTopRatedAnime() } returns Result.success(emptyList())
        coEvery { repository.getPopularAnime(page = 1) } returns Result.success(emptyList())
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Error)
            assertEquals("Unknown error", (state as HomeUiState.Error).message)
        }
    }

    @Test
    fun `loadData with mixed success and failure still shows success if at least one category has data`() = runTest(testDispatcher) {
        val topRatedList = listOf(AnimeDto(id = 50, tvName = "Top Rated", overview = "Overview"))

        coEvery { repository.getTrendingAnime() } returns Result.failure(RuntimeException("Failed"))
        coEvery { repository.getTopRatedAnime() } returns Result.success(topRatedList)
        coEvery { repository.getPopularAnime(page = 1) } returns Result.failure(RuntimeException("Failed"))
        coEvery { repository.getAiringTodayAnime() } returns Result.success(emptyList())

        viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
            val successState = state as HomeUiState.Success
            assertEquals(emptyList<AnimeDto>(), successState.trending)
            assertEquals(topRatedList, successState.topRated)
            assertEquals(emptyList<AnimeDto>(), successState.popular)
            assertEquals(emptyList<AnimeDto>(), successState.airingToday)
        }
    }
}