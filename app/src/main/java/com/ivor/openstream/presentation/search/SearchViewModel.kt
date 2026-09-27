package com.ivor.openstream.presentation.search

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.model.BrowseGenre
import com.ivor.openstream.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class SearchFilter(val label: String) { ALL("All"), MOVIES("Movies"), SERIES("Series"), ANIME("Anime") }

enum class SortOption(val label: String, val apiValue: String) {
    BEST_MATCH("Best match", "relevance"),
    POPULAR("Popular", "popularity.desc"),
    TOP_RATED("Top rated", "vote_average.desc"),
    NEWEST("Newest", "first_air_date.desc")
}

enum class YearRange(val label: String, val years: IntRange?) {
    ANY("Any", null),
    Y2020S("2020s", 2020..2029),
    Y2010S("2010s", 2010..2019),
    Y2000S("2000s", 2000..2009),
    Y1990S("1990s", 1990..1999),
    OLDER("Older", 0..1989)
}

enum class MinRating(val label: String, val minimum: Double) {
    ANY("Any", 0.0),
    SIX("6+", 6.0),
    SEVEN("7+", 7.0),
    EIGHT("8+", 8.0)
}

/** Original languages offered as filters, as TMDB's ISO 639-1 codes. */
val FILTER_LANGUAGES = listOf(
    "en" to "English",
    "ja" to "Japanese",
    "ko" to "Korean",
    "hi" to "Hindi",
    "zh" to "Chinese",
    "es" to "Spanish",
    "fr" to "French",
    "ta" to "Tamil",
    "te" to "Telugu",
    "de" to "German"
)

/**
 * Year, rating and language. TMDB's search endpoints can't filter on these, so they apply to the
 * results on the device (and more pages are fetched when they leave too few).
 */
data class ResultFilters(
    val year: YearRange = YearRange.ANY,
    val minRating: MinRating = MinRating.ANY,
    val language: String? = null
) {
    val activeCount: Int
        get() = listOf(year != YearRange.ANY, minRating != MinRating.ANY, language != null).count { it }

    fun matches(item: AnimeDto): Boolean {
        year.years?.let { range ->
            val itemYear = item.date.take(4).toIntOrNull() ?: return false
            if (itemYear !in range) return false
        }
        if (minRating != MinRating.ANY && (item.voteAverage ?: 0.0) < minRating.minimum) return false
        if (language != null && !item.originalLanguage.equals(language, ignoreCase = true)) return false
        return true
    }
}

data class SearchUiState(
    val query: String = "",
    val genre: BrowseGenre? = null,
    val recent: List<String> = emptyList(),
    val trending: List<AnimeDto> = emptyList(),
    val results: List<AnimeDto> = emptyList(),
    val filter: SearchFilter = SearchFilter.ALL,
    val sort: SortOption = SortOption.BEST_MATCH,
    val filters: ResultFilters = ResultFilters(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null
) {
    /** Nothing typed and no genre picked: show recent searches, trending and genres. */
    val isBrowsing: Boolean get() = query.isBlank() && genre == null

    /** Results after the client-side filters (anime, year, rating and language aren't TMDB search filters). */
    val visibleResults: List<AnimeDto>
        get() = results.filter { item ->
            val typeMatches = when (filter) {
                SearchFilter.ALL -> true
                SearchFilter.MOVIES -> item.isMovie
                SearchFilter.SERIES -> !item.isMovie
                SearchFilter.ANIME -> item.isAnime()
            }
            typeMatches && filters.matches(item)
        }
}

private fun AnimeDto.isAnime(): Boolean =
    16 in genreIds.orEmpty() && originalLanguage.equals("ja", ignoreCase = true)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val sharedPreferences: SharedPreferences,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(recent = loadRecent()))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var page = 1
    /** Pages fetched only because filters hid most results; capped so a rare filter can't page forever. */
    private var autoLoadedPages = 0

    init {
        viewModelScope.launch {
            repository.getCatalog(AnimeCatalog.TRENDING).onSuccess { list ->
                _uiState.update { it.copy(trending = list.take(8)) }
            }
        }
    }

    /** Typing searches automatically after a short pause. */
    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, genre = null) }
        searchJob?.cancel()
        if (value.trim().length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, error = null, endReached = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(TYPING_DEBOUNCE_MS)
            loadPage(1)
        }
    }

    /** Keyboard search, or tapping a recent search: runs now and remembers the query. */
    fun submit(value: String = _uiState.value.query) {
        val query = value.trim()
        if (query.isEmpty()) return
        _uiState.update { it.copy(query = query, genre = null) }
        rememberQuery(query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { loadPage(1) }
    }

    fun selectGenre(genre: BrowseGenre) {
        _uiState.update {
            it.copy(
                query = "",
                genre = genre,
                filter = if (genre.isAnime) SearchFilter.ALL else it.filter
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { loadPage(1) }
    }

    fun clear() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = "", genre = null, results = emptyList(), isLoading = false, error = null, endReached = false)
        }
    }

    fun onFilterSelected(filter: SearchFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
        // Movie/series filters narrow the TMDB request itself; anime is applied on the device.
        if (!_uiState.value.isBrowsing && _uiState.value.genre == null) retry()
    }

    fun onSortSelected(sort: SortOption) {
        if (_uiState.value.sort == sort) return
        _uiState.update { it.copy(sort = sort) }
        if (!_uiState.value.isBrowsing) retry()
    }

    fun onFiltersChange(filters: ResultFilters) {
        if (_uiState.value.filters == filters) return
        _uiState.update { it.copy(filters = filters) }
        autoLoadedPages = 0
        fillFilteredResults()
    }

    fun resetFilters() = onFiltersChange(ResultFilters())

    fun retry() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { loadPage(1) }
    }

    /** Called when the grid nears its end. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.endReached || state.isBrowsing) return
        searchJob = viewModelScope.launch { loadPage(page + 1) }
    }

    private suspend fun loadPage(target: Int) {
        val state = _uiState.value
        _uiState.update {
            if (target == 1) it.copy(isLoading = true, error = null, endReached = false) else it.copy(isLoadingMore = true)
        }
        val genre = state.genre
        val result = if (genre != null) {
            repository.discoverByGenre(genre, target).map { list ->
                when (state.sort) {
                    SortOption.TOP_RATED -> list.sortedByDescending { it.voteAverage ?: 0.0 }
                    SortOption.NEWEST -> list.sortedByDescending(AnimeDto::date)
                    else -> list
                }
            }
        } else {
            val type = when (state.filter) {
                SearchFilter.MOVIES -> "movie"
                SearchFilter.SERIES -> "tv"
                else -> "all"
            }
            repository.searchAnime(state.query.trim(), target, type, state.sort.apiValue)
        }
        if (target == 1) autoLoadedPages = 0
        result.fold(
            onSuccess = { list ->
                page = target
                _uiState.update { current ->
                    val merged = if (target == 1) list else (current.results + list).distinctBy { "${it.mediaType}:${it.id}" }
                    current.copy(
                        results = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        endReached = list.isEmpty() || (target > 1 && merged.size == current.results.size)
                    )
                }
                fillFilteredResults()
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(isLoading = false, isLoadingMore = false, error = if (target == 1) error.message ?: "Search failed" else it.error)
                }
            }
        )
    }

    /** With filters on, keeps fetching (a few pages at most) until there's a screenful to show. */
    private fun fillFilteredResults() {
        val state = _uiState.value
        if (state.filters.activeCount == 0 || state.isBrowsing) return
        if (state.isLoading || state.isLoadingMore || state.endReached) return
        if (state.visibleResults.size >= MIN_FILTERED_RESULTS || autoLoadedPages >= MAX_AUTO_PAGES) return
        autoLoadedPages++
        searchJob = viewModelScope.launch { loadPage(page + 1) }
    }

    fun removeRecent(query: String) {
        val updated = _uiState.value.recent - query
        _uiState.update { it.copy(recent = updated) }
        persistRecent(updated)
    }

    fun clearRecent() {
        _uiState.update { it.copy(recent = emptyList()) }
        sharedPreferences.edit().remove(RECENT_KEY).apply()
    }

    private fun rememberQuery(query: String) {
        val updated = (listOf(query) + _uiState.value.recent.filterNot { it.equals(query, ignoreCase = true) }).take(MAX_RECENT)
        _uiState.update { it.copy(recent = updated) }
        persistRecent(updated)
    }

    private fun loadRecent(): List<String> = runCatching {
        json.decodeFromString<List<String>>(sharedPreferences.getString(RECENT_KEY, "[]") ?: "[]")
    }.getOrDefault(emptyList())

    private fun persistRecent(list: List<String>) {
        sharedPreferences.edit().putString(RECENT_KEY, json.encodeToString(list)).apply()
    }

    private companion object {
        const val RECENT_KEY = "search_history_list"
        const val MAX_RECENT = 10
        const val MIN_QUERY_LENGTH = 2
        const val TYPING_DEBOUNCE_MS = 400L
        const val MIN_FILTERED_RESULTS = 12
        const val MAX_AUTO_PAGES = 5
    }
}
