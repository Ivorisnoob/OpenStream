package com.ivor.openstream.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.repository.HiddenTitlesRepository
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RailStyle {
    /** Big rank numerals beside posters, for a top-ten list. */
    RANKED,

    /** Wide backdrop cards, for what is airing now. */
    LANDSCAPE,

    POSTER
}

data class HomeRail(
    val key: String,
    val title: String,
    val style: RailStyle,
    val items: List<AnimeDto>
)

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val hero: List<AnimeDto>,
        val rails: List<HomeRail>,
        val isRefreshing: Boolean = false
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val hiddenTitlesRepository: HiddenTitlesRepository
) : ViewModel() {

    /** The feed as loaded; [uiState] is this minus the titles the user hid. */
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = combine(_uiState, hiddenTitlesRepository.hiddenKeys) { state, hidden ->
        if (state is HomeUiState.Success && hidden.isNotEmpty()) state.without(hidden) else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    val continueWatching: StateFlow<List<WatchProgress>> = watchProgressRepository.continueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadData()
    }

    fun hideTitle(anime: AnimeDto) {
        viewModelScope.launch { hiddenTitlesRepository.hide(anime.homeMediaType, anime.id, anime.name) }
    }

    fun unhideTitle(anime: AnimeDto) {
        viewModelScope.launch { hiddenTitlesRepository.unhide(anime.homeMediaType, anime.id) }
    }

    fun removeFromContinueWatching(item: WatchProgress) {
        viewModelScope.launch { watchProgressRepository.dismiss(item.mediaType, item.tmdbId) }
    }

    /** Pull-to-refresh keeps the current feed on screen while the new one loads. */
    fun refresh() {
        val current = _uiState.value
        if (current is HomeUiState.Success) _uiState.value = current.copy(isRefreshing = true)
        loadData(showLoading = current !is HomeUiState.Success, forceRefresh = true)
    }

    fun loadData(showLoading: Boolean = true, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (showLoading) _uiState.value = HomeUiState.Loading
            val catalogs = coroutineScope {
                AnimeCatalog.entries.associateWith { catalog -> async { repository.getCatalog(catalog, forceRefresh) } }
                    .mapValues { (_, request) -> request.await().getOrDefault(emptyList()) }
            }
            if (catalogs.values.all { it.isEmpty() }) {
                _uiState.value = HomeUiState.Error("Couldn't reach the catalog")
                return@launch
            }

            val trending = catalogs.getValue(AnimeCatalog.TRENDING)
            val rails = listOf(
                HomeRail("top10", "Top 10 this week", RailStyle.RANKED, trending.take(10)),
                HomeRail(
                    "new-episodes",
                    "New episodes this week",
                    RailStyle.LANDSCAPE,
                    catalogs.getValue(AnimeCatalog.NEW_EPISODES).filter { it.backdropPath != null }
                ),
                HomeRail("popular-movies", "Popular movies", RailStyle.POSTER, catalogs.getValue(AnimeCatalog.POPULAR_MOVIES)),
                HomeRail("popular-series", "Popular series", RailStyle.POSTER, catalogs.getValue(AnimeCatalog.POPULAR_SERIES)),
                HomeRail("anime", "Trending anime", RailStyle.POSTER, catalogs.getValue(AnimeCatalog.TRENDING_ANIME)),
                HomeRail("top-rated", "Critically acclaimed", RailStyle.POSTER, catalogs.getValue(AnimeCatalog.TOP_RATED_MOVIES)),
                HomeRail("anime-movies", "Anime movies", RailStyle.POSTER, catalogs.getValue(AnimeCatalog.ANIME_MOVIES))
            ).filter { it.items.isNotEmpty() }

            _uiState.value = HomeUiState.Success(
                hero = trending.filter { it.posterPath != null }.take(8),
                rails = rails
            )
        }
    }
}

private val AnimeDto.homeMediaType: String get() = if (isMovie) "movie" else "tv"

private fun HomeUiState.Success.without(hidden: Set<String>): HomeUiState.Success {
    fun List<AnimeDto>.visible() = filterNot { HiddenTitlesRepository.key(it.homeMediaType, it.id) in hidden }
    return copy(
        hero = hero.visible(),
        rails = rails.map { it.copy(items = it.items.visible()) }.filter { it.items.isNotEmpty() }
    )
}
