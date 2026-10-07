package com.ivor.openstream.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.TitleRatingEntity
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.repository.HiddenTitlesRepository
import com.ivor.openstream.data.repository.ProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import com.ivor.openstream.data.repository.CustomListRepository
import com.ivor.openstream.data.repository.TitleRatingRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.matching.TitleMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

/** A taste match for the "Top matches for you" rail. */
data class MatchUi(
    val item: AnimeDto,
    val percent: Int,
    val becauseName: String?
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val hiddenTitlesRepository: HiddenTitlesRepository,
    profileRepository: ProfileRepository,
    private val ratingRepository: TitleRatingRepository,
    private val listRepository: CustomListRepository,
    private val watchLaterRepository: WatchLaterRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** The feed as loaded; [uiState] is this minus the titles the user hid. */
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = combine(_uiState, hiddenTitlesRepository.hiddenKeys) { state, hidden ->
        if (state is HomeUiState.Success && hidden.isNotEmpty()) state.without(hidden) else state
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    val continueWatching: StateFlow<List<WatchProgress>> = watchProgressRepository.continueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Watch Later plus every custom list, as title-key sets for the match engine. */
    private val listKeySets: StateFlow<List<Set<Pair<String, Int>>>> =
        listRepository.summaries().flatMapLatest { summaries ->
            if (summaries.isEmpty()) flowOf(emptyList())
            else combine(summaries.map { summary -> listRepository.items(summary.id) }) { arrays ->
                arrays.map { items -> items.map { it.mediaType to it.tmdbId }.toSet() }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class MatchInputs(
        val ratings: List<TitleRatingEntity>,
        val lists: List<Set<Pair<String, Int>>>,
        val saved: List<WatchLaterEntity>,
        val progress: List<WatchProgress>,
        val hidden: Set<String>
    )

    // Only 2-flow combines: the wider overloads don't resolve on this classpath.
    private val matchInputs: StateFlow<MatchInputs> = combine(
        combine(ratingRepository.entities, listKeySets) { ratings, lists -> ratings to lists },
        combine(
            combine(
                watchLaterRepository.getWatchLaterList(),
                watchProgressRepository.allProgress()
            ) { saved, progress -> saved to progress },
            hiddenTitlesRepository.hiddenKeys
        ) { savedProgress, hidden ->
            Triple(savedProgress.first, savedProgress.second, hidden)
        }
    ) { ratingsLists, savedProgressHidden ->
        MatchInputs(
            ratings = ratingsLists.first,
            lists = ratingsLists.second,
            saved = savedProgressHidden.first,
            progress = savedProgressHidden.second,
            hidden = savedProgressHidden.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MatchInputs(emptyList(), emptyList(), emptyList(), emptyList(), emptySet()))

    /** Taste-matched titles from the loaded catalogs; empty until the first like. */
    val matches: StateFlow<List<MatchUi>> = combine(_uiState, matchInputs) { state, inputs ->
        matchForState(state, inputs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun matchForState(state: HomeUiState, inputs: MatchInputs): List<MatchUi> {
        val success = state as? HomeUiState.Success ?: return emptyList()
        val likes = inputs.ratings.filter { it.rating > 0 }
        if (likes.isEmpty()) return emptyList()
        val rated = inputs.ratings.map {
            TitleMatcher.RatedTitle(
                key = it.mediaType to it.tmdbId,
                name = it.title,
                genres = it.genreSet,
                language = it.language,
                rating = it.rating
            )
        }
        val savedKeys = inputs.saved.map { it.mediaType to it.id }.toSet()
        val listedKeys = inputs.lists.flatten().toSet()
        val completedKeys = inputs.progress.filter { it.completed }.map { it.mediaType to it.tmdbId }.toSet()
        val hiddenParsed = inputs.hidden.mapNotNull { key ->
            val (type, id) = key.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            type to (id.toIntOrNull() ?: return@mapNotNull null)
        }.toSet()
        val candidates = (success.rails.flatMap { it.items } + success.hero)
            .distinctBy { (it.mediaType ?: if (it.isMovie) "movie" else "tv") to it.id }
        val byKey = candidates.associateBy { item ->
            (item.mediaType ?: if (item.isMovie) "movie" else "tv") to item.id
        }
        val pool = byKey.map { (key, item) ->
            TitleMatcher.Candidate(
                key = key,
                genres = item.genreIds.orEmpty().toSet(),
                language = item.originalLanguage,
                voteAverage = item.voteAverage
            )
        }
        val affinity = inputs.lists + listOf(savedKeys)
        return TitleMatcher.match(
            candidates = pool,
            ratings = rated,
            lists = affinity,
            excluded = completedKeys + hiddenParsed + savedKeys + listedKeys +
                rated.filter { it.rating < 0 }.map { it.key }.toSet(),
            minScore = 0.40,
            limit = 20
        ).filter { it.key !in savedKeys && it.key !in listedKeys }
            .take(10)
            .mapNotNull { match ->
                byKey[match.key]?.let { item ->
                    MatchUi(item = item, percent = match.percent, becauseName = match.becauseName)
                }
            }
    }

    init {
        // First load, and again whenever the feed has to change: switching to or from a kids profile.
        viewModelScope.launch {
            profileRepository.activeProfile
                .map { it?.isKids }
                .distinctUntilChanged()
                .collect { kids -> if (kids != null) loadData() }
        }
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
                _uiState.value = HomeUiState.Error(context.getString(R.string.st_could_not_catalog))
                return@launch
            }

            val trending = catalogs.getValue(AnimeCatalog.TRENDING)
            val rails = listOf(
                HomeRail("top10", context.getString(R.string.home_top10_week), RailStyle.RANKED, trending.take(10)),
                HomeRail(
                    "new-episodes",
                    context.getString(R.string.home_new_episodes_week),
                    RailStyle.LANDSCAPE,
                    catalogs.getValue(AnimeCatalog.NEW_EPISODES).filter { it.backdropPath != null }
                ),
                HomeRail("popular-movies", context.getString(R.string.home_popular_movies), RailStyle.POSTER, catalogs.getValue(AnimeCatalog.POPULAR_MOVIES)),
                HomeRail("popular-series", context.getString(R.string.home_popular_series), RailStyle.POSTER, catalogs.getValue(AnimeCatalog.POPULAR_SERIES)),
                HomeRail("anime", context.getString(R.string.home_trending_anime), RailStyle.POSTER, catalogs.getValue(AnimeCatalog.TRENDING_ANIME)),
                HomeRail("top-rated", context.getString(R.string.home_critically_acclaimed), RailStyle.POSTER, catalogs.getValue(AnimeCatalog.TOP_RATED_MOVIES)),
                HomeRail("anime-movies", context.getString(R.string.home_anime_movies), RailStyle.POSTER, catalogs.getValue(AnimeCatalog.ANIME_MOVIES))
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
