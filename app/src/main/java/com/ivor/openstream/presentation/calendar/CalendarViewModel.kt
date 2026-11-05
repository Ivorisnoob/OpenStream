package com.ivor.openstream.presentation.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.ReminderEntity
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.repository.ReminderRepository
import com.ivor.openstream.data.repository.UpcomingItem
import com.ivor.openstream.data.repository.UpcomingKind
import com.ivor.openstream.data.repository.UpcomingRepository
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.AnimeCatalog
import com.ivor.openstream.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class CalendarFilter(@StringRes val labelRes: Int) {
    ALL(R.string.li_saved_all),
    SERIES(R.string.li_saved_series),
    ANIME(R.string.genre_anime),
    MOVIES(R.string.li_saved_movies);

    fun matches(item: UpcomingItem): Boolean = when (this) {
        ALL -> true
        SERIES -> item.kind == UpcomingKind.SERIES
        ANIME -> item.kind == UpcomingKind.ANIME
        MOVIES -> item.kind == UpcomingKind.MOVIE
    }
}

/** A stretch of the agenda: what is already out, or one day still to come (null for "out now"). */
data class CalendarSection(val day: LocalDate?, val items: List<UpcomingItem>)

data class CalendarUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadFailed: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val items: List<UpcomingItem> = emptyList(),
    val followedCount: Int = 0,
    val failedCount: Int = 0,
    val filter: CalendarFilter = CalendarFilter.ALL,
    val selectedDay: LocalDate? = null,
    val remindedMovies: Set<Int> = emptySet(),
    val suggestions: List<AnimeDto> = emptyList()
) {
    val visible: List<UpcomingItem> get() = items.filter(filter::matches)

    /** The film or episode closest ahead, for the banner. */
    val nextUp: UpcomingItem? get() = visible.firstOrNull { !it.airDate.isBefore(today) }

    val sections: List<CalendarSection>
        get() {
            val (out, ahead) = visible.partition { it.airDate.isBefore(today) }
            return buildList {
                if (out.isNotEmpty()) add(CalendarSection(null, out.sortedByDescending { it.airDate }))
                ahead.groupBy { it.airDate }.forEach { (day, items) -> add(CalendarSection(day, items)) }
            }
        }

    /** The days in the strip: today onwards, as many as the strip shows. */
    val stripDays: List<LocalDate> get() = List(STRIP_DAYS) { today.plusDays(it.toLong()) }

    val countsByDay: Map<LocalDate, Int> get() = visible.groupingBy { it.airDate }.eachCount()

    private companion object {
        const val STRIP_DAYS = 14
    }
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val upcomingRepository: UpcomingRepository,
    private val animeRepository: AnimeRepository,
    private val reminderRepository: ReminderRepository,
    private val settings: AppSettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            settings.activeProfileId.collectLatest {
                _state.update { state -> state.copy(isLoading = true) }
                loadNow(fresh = false)
            }
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            loadNow(fresh = true)
        }
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        refresh()
    }

    fun setFilter(filter: CalendarFilter) = _state.update { it.copy(filter = filter, selectedDay = null) }

    fun selectDay(day: LocalDate?) = _state.update { it.copy(selectedDay = day) }

    /** Films have one date and no episode alert to lean on; shows are already covered by those. */
    fun toggleMovieReminder(item: UpcomingItem) {
        viewModelScope.launch {
            val profileId = settings.activeProfileId.value
            if (item.tmdbId in _state.value.remindedMovies) {
                reminderRepository.remove(profileId, "movie", item.tmdbId)
                _state.update { it.copy(remindedMovies = it.remindedMovies - item.tmdbId) }
            } else {
                reminderRepository.add(
                    ReminderEntity(
                        profileId = profileId,
                        mediaType = "movie",
                        tmdbId = item.tmdbId,
                        title = item.title,
                        posterPath = item.posterPath
                    )
                )
                _state.update { it.copy(remindedMovies = it.remindedMovies + item.tmdbId) }
            }
        }
    }

    private suspend fun loadNow(fresh: Boolean) {
        val today = LocalDate.now()
        try {
            val schedule = upcomingRepository.load(today, fresh)
            val profileId = settings.activeProfileId.value
            val reminded = runCatching { reminderRepository.all() }.getOrDefault(emptyList())
                .filter { it.profileId == profileId && it.mediaType == "movie" }
                .mapTo(HashSet()) { it.tmdbId }
            val suggestions = if (schedule.followedCount == 0) {
                animeRepository.getCatalog(AnimeCatalog.NEW_EPISODES).getOrNull().orEmpty().take(MAX_SUGGESTIONS)
            } else {
                emptyList()
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    // A schedule with every title failed is an outage, not an empty calendar.
                    loadFailed = schedule.followedCount > 0 && schedule.failedCount == schedule.followedCount,
                    today = today,
                    items = schedule.items,
                    followedCount = schedule.followedCount,
                    failedCount = schedule.failedCount,
                    remindedMovies = reminded,
                    suggestions = suggestions
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(isLoading = false, isRefreshing = false, loadFailed = it.items.isEmpty()) }
        }
    }

    private companion object {
        const val MAX_SUGGESTIONS = 12
    }
}
