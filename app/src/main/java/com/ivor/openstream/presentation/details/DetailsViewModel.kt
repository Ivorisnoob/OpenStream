package com.ivor.openstream.presentation.details

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.images.ArtworkPrefetcher
import com.ivor.openstream.data.images.TmdbImages
import com.ivor.openstream.data.remote.model.AnimeDetailsDto
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.local.entity.ReminderEntity
import com.ivor.openstream.data.remote.model.SeasonDetailsDto
import com.ivor.openstream.data.remote.model.EpisodeDto
import com.ivor.openstream.data.remote.model.toAnimeDto
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.local.dao.CustomListSummary
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import com.ivor.openstream.data.repository.CustomListRepository
import com.ivor.openstream.data.repository.ReminderRepository
import com.ivor.openstream.data.repository.TitleRatingRepository
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.SourcePreference
import com.ivor.openstream.domain.repository.StreamingRepository
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val watchLaterRepository: WatchLaterRepository,
    private val downloadRepository: DownloadRepository,
    private val streamingRepository: StreamingRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val listRepository: CustomListRepository,
    private val reminderRepository: ReminderRepository,
    private val ratingRepository: TitleRatingRepository,
    private val settings: AppSettingsStore,
    private val artworkPrefetcher: ArtworkPrefetcher,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val animeId: Int = checkNotNull(savedStateHandle["animeId"])
    private val mediaType: String = checkNotNull(savedStateHandle["mediaType"])
    
    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()


    val isWatchLater: StateFlow<Boolean> = watchLaterRepository.isWatchLater(animeId)
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** True when this title hasn't released yet, so "Remind me" makes sense. */
    val remindable: StateFlow<Boolean> = uiState
        .map { (it as? DetailsUiState.Success)?.details?.let { d -> isUnreleased(d) } ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the active profile has a reminder set for this title. */
    val reminderSet: StateFlow<Boolean> = settings.activeProfileId
        .flatMapLatest { id -> reminderRepository.isReminder(id, mediaType, animeId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Toggles the "Remind me" request for this title, confirming via [messages]. */
    fun toggleReminder() {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        viewModelScope.launch {
            val profileId = settings.activeProfileId.value
            if (reminderSet.value) {
                reminderRepository.remove(profileId, mediaType, animeId)
                _messages.tryEmit(context.getString(R.string.remind_removed))
            } else {
                reminderRepository.add(
                    ReminderEntity(
                        profileId = profileId,
                        mediaType = mediaType,
                        tmdbId = animeId,
                        title = details.name,
                        posterPath = details.posterPath
                    )
                )
                _messages.tryEmit(context.getString(R.string.remind_set))
            }
        }
    }

    /** Every episode the user has touched, newest first. */
    private val titleProgress = watchProgressRepository.progressForTitle(mediaType, animeId)

    val episodeProgress: StateFlow<Map<Pair<Int, Int>, WatchProgress>> = titleProgress
        .map { rows -> rows.associateBy { it.season to it.episode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** What the primary action continues with, or null to start from the beginning. */
    val resumeTarget: StateFlow<WatchProgress?> = titleProgress
        .map { rows -> rows.firstOrNull()?.takeUnless { it.completed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailsUiState.Loading
            repository.getMediaDetails(animeId, mediaType)
                .onSuccess { details ->
                    _uiState.value = DetailsUiState.Success(details)
                    // The rails further down the page, in the sizes DetailsScreen draws them.
                    artworkPrefetcher.prefetch(
                        details.cast.take(20).map { TmdbImages.url("w185", it.profilePath) } +
                            details.recommendations?.results.orEmpty().map { TmdbImages.url("w342", it.posterPath) }
                    )
                    // Add to watch history
                    viewModelScope.launch {
                        repository.addToWatchHistory(details.toAnimeDto(mediaType))
                    }
                    // Open on the season the user was last watching, otherwise season 1.
                    details.seasons?.let { seasons ->
                        val lastWatchedSeason = titleProgress.first().firstOrNull()?.season
                        val defaultSeason = seasons.find { it.seasonNumber == lastWatchedSeason }
                            ?: seasons.find { it.seasonNumber == 1 }
                            ?: seasons.firstOrNull()
                        defaultSeason?.let { season ->
                            loadSeason(season.seasonNumber)
                        }
                    }
                }
                .onFailure { exception ->
                    _uiState.value = DetailsUiState.Error(exception.message ?: context.getString(R.string.er_unknown))
                }
        }
    }

    fun toggleWatchLater() {
        val currentState = _uiState.value
        if (currentState is DetailsUiState.Success) {
            viewModelScope.launch {
                val details = currentState.details
                val item = WatchLaterEntity(
                    id = details.id,
                    title = details.name,
                    posterPath = details.posterPath,
                    mediaType = mediaType,
                    voteAverage = details.voteAverage
                )
                if (isWatchLater.value) {
                    watchLaterRepository.removeFromWatchLaterById(details.id)
                } else {
                    watchLaterRepository.addToWatchLater(item)
                }
            }
        }
    }

    fun loadSeason(seasonNumber: Int) {
        val currentState = _uiState.value
        if (currentState is DetailsUiState.Success) {
            viewModelScope.launch {
                _uiState.value = currentState.copy(isLoadingEpisodes = true)
                repository.getSeasonDetails(animeId, seasonNumber)
                    .onSuccess { seasonDetails ->
                        artworkPrefetcher.prefetch(seasonDetails.episodes.map { TmdbImages.url("w300", it.stillPath) })
                        (_uiState.value as? DetailsUiState.Success)?.let { successState ->
                            _uiState.value = successState.copy(
                                selectedSeasonDetails = seasonDetails,
                                isLoadingEpisodes = false
                            )
                        }
                    }
                    .onFailure {
                        (_uiState.value as? DetailsUiState.Success)?.let { successState ->
                            _uiState.value = successState.copy(isLoadingEpisodes = false)
                        }
                    }
            }
        }
    }

    /** Marks an episode (or the movie) finished without playing it, e.g. watched elsewhere. */
    fun setWatched(episode: EpisodeDto, watched: Boolean) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        viewModelScope.launch {
            if (!watched) {
                watchProgressRepository.clearEpisode(mediaType, animeId, episode.seasonNumber, episode.episodeNumber)
                return@launch
            }
            val durationMs = (episode.runtime ?: details.typicalRuntime ?: DEFAULT_RUNTIME_MIN) * 60_000L
            watchProgressRepository.record(
                WatchProgress(
                    tmdbId = animeId,
                    mediaType = mediaType,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    title = details.name,
                    episodeTitle = episode.name.takeIf { mediaType != "movie" },
                    posterPath = details.posterPath,
                    backdropPath = details.backdropPath,
                    stillPath = episode.stillPath,
                    positionMs = durationMs,
                    durationMs = durationMs,
                    completed = true
                )
            )
        }
    }

    /** Marks every given episode watched or unwatched, e.g. a whole season. */
    fun setSeasonWatched(episodes: List<EpisodeDto>, watched: Boolean) {
        episodes.forEach { setWatched(it, watched) }
    }

    /**
     * True when the movie, or every aired episode of the show (specials aside), is finished.
     * Counted from TMDB's season sizes, so it doesn't need every season's episode list.
     */
    val titleWatched: StateFlow<Boolean> = combine(titleProgress, _uiState) { rows, state ->
        val details = (state as? DetailsUiState.Success)?.details ?: return@combine false
        if (mediaType == "movie") return@combine rows.any { it.completed }
        val aired = details.airedEpisodeCount()
        aired > 0 && rows.count { it.completed && it.season > 0 } >= aired
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _titleWatchedBusy = MutableStateFlow(false)
    /** True while every season is being fetched to mark a show watched. */
    val titleWatchedBusy: StateFlow<Boolean> = _titleWatchedBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Marks the whole title watched (every aired episode of every season, specials aside) or
     * unwatched (forgets all its progress).
     */
    fun setTitleWatched(watched: Boolean) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        if (_titleWatchedBusy.value) return
        viewModelScope.launch {
            if (!watched) {
                watchProgressRepository.clearTitle(mediaType, animeId)
                _messages.tryEmit(context.getString(R.string.details_marked_unwatched, details.name))
                return@launch
            }
            if (mediaType == "movie") {
                setWatched(details.movieEpisode(), true)
                return@launch
            }
            _titleWatchedBusy.value = true
            try {
                val done = titleProgress.first().filter { it.completed }.map { it.season to it.episode }.toSet()
                var marked = 0
                var failedSeasons = 0
                details.seasons.orEmpty()
                    .filter { it.seasonNumber > 0 && it.episodeCount > 0 && isReleased(it.airDate) }
                    .sortedBy { it.seasonNumber }
                    .forEach { season ->
                        val episodes = repository.getSeasonDetails(animeId, season.seasonNumber).getOrNull()?.episodes
                        if (episodes == null) {
                            failedSeasons++
                            return@forEach
                        }
                        episodes
                            .filter { isReleased(it.airDate) && (it.seasonNumber to it.episodeNumber) !in done }
                            .forEach { episode ->
                                setWatched(episode, true)
                                marked++
                            }
                    }
                _messages.tryEmit(
                    when {
                        failedSeasons > 0 -> context.getString(R.string.details_seasons_failed, failedSeasons)
                        marked == 0 -> context.getString(R.string.er_everything_watched)
                        marked == 1 -> context.getString(R.string.details_marked_episodes_one)
                        else -> context.getString(R.string.details_marked_episodes_other, marked)
                    }
                )
            } finally {
                _titleWatchedBusy.value = false
            }
        }
    }

    /** The user's lists, for the "Add to list" sheet. */
    val lists: StateFlow<List<CustomListSummary>> = listRepository.summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lists this title is in. */
    val memberOf: StateFlow<Set<Long>> = listRepository.listIdsFor(mediaType, animeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setInList(listId: Long, add: Boolean) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        viewModelScope.launch {
            if (add) listRepository.add(details.asListItem(listId)) else listRepository.remove(listId, mediaType, animeId)
        }
    }

    /** The user's explicit taste signal for this title, if any. */
    val rating: StateFlow<Int?> = ratingRepository.ratingFor(mediaType, animeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rate(value: Int) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        viewModelScope.launch {
            ratingRepository.toggle(
                mediaType = mediaType,
                tmdbId = animeId,
                rating = value,
                title = details.name,
                genreIds = details.genres.orEmpty().map { it.id }.toSet(),
                language = details.originalLanguage,
                voteAverage = details.voteAverage
            )
        }
    }

    /** Makes a list (or finds one with that name) and puts this title in it. */
    fun createListWithTitle(name: String) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val listId = listRepository.create(name)
            listRepository.add(details.asListItem(listId))
            _messages.tryEmit(context.getString(R.string.details_added_to, name.trim()))
        }
    }

    private fun AnimeDetailsDto.asListItem(listId: Long) = CustomListItemEntity(
        listId = listId,
        tmdbId = animeId,
        mediaType = mediaType,
        title = name,
        posterPath = posterPath,
        voteAverage = voteAverage
    )

    /** Download state per episode of this title, keyed by (season, episode). */
    val episodeDownloads: StateFlow<Map<Pair<Int, Int>, DownloadEntity>> =
        downloadRepository.getDownloadsForTitle(animeId, mediaType)
            .map { rows -> rows.associateBy { it.season to it.episode } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun isUnreleased(details: AnimeDetailsDto): Boolean =
        if (mediaType == "movie") isFuture(details.date)
        else isFuture(details.firstAirDate) || details.status in setOf("Planned", "In Production", "Pilot")

    /** Hands episodes to the app-wide download queue; it keeps going after this screen closes. */
    fun downloadEpisodes(episodes: List<EpisodeDto>) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        downloadRepository.enqueue(
            episodes.map { episode ->
                DownloadTarget(
                    tmdbId = animeId,
                    mediaType = mediaType,
                    season = episode.seasonNumber,
                    episode = episode.episodeNumber,
                    showTitle = details.name,
                    episodeTitle = episode.name.takeIf { mediaType != "movie" },
                    posterPath = details.posterPath,
                    stillPath = episode.stillPath ?: details.backdropPath,
                    year = details.date.take(4).toIntOrNull()
                )
            }
        )
    }

    // region Download source picker (long-press a download button)

    private val _downloadOptions = MutableStateFlow<DownloadOptionsUiState?>(null)
    /** Sources to download from for the episodes [openDownloadOptions] was called with. */
    val downloadOptions: StateFlow<DownloadOptionsUiState?> = _downloadOptions.asStateFlow()
    private var downloadOptionsJob: Job? = null

    /**
     * Opens the source picker for one episode/movie (exact server download) or a whole season
     * (the tapped row's provider and quality become the preference for every episode).
     */
    fun openDownloadOptions(episodes: List<EpisodeDto>) {
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        if (episodes.isEmpty()) return
        downloadOptionsJob?.cancel()
        val representative = episodes.first()
        val label = when {
            episodes.size > 1 -> null
            mediaType == "movie" -> null
            else -> context.getString(
                R.string.season_episode_format,
                representative.seasonNumber,
                representative.episodeNumber
            )
        }
        _downloadOptions.value = DownloadOptionsUiState(
            title = details.name,
            episodeLabel = label,
            episodes = episodes,
            servers = emptyList(),
            isLoading = true,
            isBatch = episodes.size > 1
        )
        val identity = MediaIdentity(
            tmdbId = animeId,
            tmdbType = mediaType,
            title = details.name,
            season = representative.seasonNumber,
            episode = representative.episodeNumber,
            year = details.date.take(4).toIntOrNull()
        )
        downloadOptionsJob = viewModelScope.launch {
            streamingRepository.resolveServers(identity).collect { progress ->
                val current = _downloadOptions.value ?: return@collect
                _downloadOptions.value = current.copy(
                    servers = progress.servers.filter { it.isDownloadable },
                    isLoading = !progress.isComplete,
                    completedProviders = progress.completedProviders,
                    totalProviders = progress.totalProviders,
                    isComplete = progress.isComplete
                )
            }
        }
    }

    fun closeDownloadOptions() {
        downloadOptionsJob?.cancel()
        _downloadOptions.value = null
    }

    /** Re-runs source resolution for the picker's episodes (empty-state retry). */
    fun retryDownloadOptions() {
        val episodes = _downloadOptions.value?.episodes ?: return
        openDownloadOptions(episodes)
    }

    /**
     * Downloads the picker's episode from [server]. For a season, the episode the picker searched
     * downloads from [server] and the rest go to the app-wide queue preferring its source and
     * quality, so leaving this screen doesn't stop them.
     */
    fun downloadFromServer(server: VideoServer) {
        val options = _downloadOptions.value ?: return
        val details = (_uiState.value as? DetailsUiState.Success)?.details ?: return
        closeDownloadOptions()
        // A season pick skips what is already downloaded or on its way; a single pick re-downloads.
        val episodes = if (!options.isBatch) options.episodes else options.episodes.filterNot { episode ->
            episodeDownloads.value[episode.seasonNumber to episode.episodeNumber]
                ?.let { it.status == DownloadStatus.COMPLETED || DownloadStatus.isActive(it.status) } == true
        }
        val first = episodes.firstOrNull() ?: run {
            _messages.tryEmit(context.getString(R.string.dl_season_already))
            return
        }
        val rest = episodes.drop(1)
        if (rest.isNotEmpty()) {
            downloadRepository.enqueue(
                rest.map { targetFor(details, it) },
                SourcePreference(providerId = server.providerId, qualityRank = server.quality.rank)
            )
        }
        viewModelScope.launch {
            runCatching { downloadRepository.download(server, targetFor(details, first)) }
                .onSuccess {
                    _messages.tryEmit(
                        if (rest.isEmpty()) context.getString(R.string.er_download_started)
                        else context.getString(R.string.dl_batch_queued, episodes.size)
                    )
                }
                .onFailure { error ->
                    // That link was refused: let the queue find this one a source too.
                    if (rest.isNotEmpty()) {
                        downloadRepository.enqueue(listOf(targetFor(details, first)))
                        _messages.tryEmit(context.getString(R.string.dl_batch_queued, episodes.size))
                    } else {
                        _messages.tryEmit(error.message ?: context.getString(R.string.dl_could_not_start))
                    }
                }
        }
    }

    private fun targetFor(details: AnimeDetailsDto, episode: EpisodeDto) = DownloadTarget(
        tmdbId = animeId,
        mediaType = mediaType,
        season = episode.seasonNumber,
        episode = episode.episodeNumber,
        showTitle = details.name,
        episodeTitle = episode.name.takeIf { mediaType != "movie" },
        posterPath = details.posterPath,
        stillPath = episode.stillPath ?: details.backdropPath,
        year = details.date.take(4).toIntOrNull()
    )

    // endregion
}

private const val DEFAULT_RUNTIME_MIN = 24

private fun isReleased(airDate: String?): Boolean {
    val date = airDate?.takeIf { it.isNotBlank() } ?: return true
    return runCatching { !LocalDate.parse(date.take(10)).isAfter(LocalDate.now()) }.getOrDefault(true)
}

private fun isFuture(date: String?): Boolean {
    val parsed = date?.takeIf { it.isNotBlank() } ?: return false
    return runCatching { LocalDate.parse(parsed.take(10)).isAfter(LocalDate.now()) }.getOrDefault(false)
}

/** Episodes aired so far across the regular seasons, from season sizes and the next air date. */
private fun AnimeDetailsDto.airedEpisodeCount(): Int {
    val next = nextEpisodeToAir?.takeIf { it.seasonNumber > 0 && !isReleased(it.airDate) }
    return seasons.orEmpty()
        .filter { it.seasonNumber > 0 && isReleased(it.airDate) }
        .sumOf { season ->
            when {
                next == null || season.seasonNumber < next.seasonNumber -> season.episodeCount
                season.seasonNumber == next.seasonNumber -> (next.episodeNumber - 1).coerceIn(0, season.episodeCount)
                else -> 0
            }
        }
}

/** The movie as the single "episode" progress is stored under (season 1, episode 1). */
private fun AnimeDetailsDto.movieEpisode() = EpisodeDto(
    id = id,
    name = name,
    overview = overview,
    voteAverage = voteAverage,
    voteCount = voteCount ?: 0,
    airDate = date,
    episodeNumber = 1,
    seasonNumber = 1,
    stillPath = backdropPath,
    productionCode = "",
    runtime = runtime,
    showId = id
)

sealed interface DetailsUiState {
    data object Loading : DetailsUiState
    data class Success(
        val details: AnimeDetailsDto,
        val selectedSeasonDetails: SeasonDetailsDto? = null,
        val isLoadingEpisodes: Boolean = false
    ) : DetailsUiState
    data class Error(val message: String) : DetailsUiState
}

/** Live source results for the long-press download picker. */
data class DownloadOptionsUiState(
    val title: String,
    val episodeLabel: String?,
    val episodes: List<EpisodeDto>,
    val servers: List<VideoServer>,
    val isLoading: Boolean,
    val completedProviders: Int = 0,
    val totalProviders: Int = 0,
    val isComplete: Boolean = false,
    val isBatch: Boolean = false
)

