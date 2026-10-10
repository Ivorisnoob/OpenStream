package com.ivor.openstream.presentation.watch_history

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

enum class HistoryFilter(val label: String, @StringRes val labelRes: Int) {
    ALL("All", R.string.search_filter_all),
    MOVIES("Movies", R.string.search_filter_movies),
    SERIES("Series", R.string.search_filter_series),
    IN_PROGRESS("In progress", R.string.hist_filter_in_progress),
    FINISHED("Finished", R.string.hist_filter_finished)
}

/** Entries watched in one period ("Today", "Yesterday", "March 2026"…). */
data class HistoryGroup(val label: String, val items: List<WatchProgress>)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val groups: List<HistoryGroup> = emptyList(),
    /** Everything recorded, before search and filters; drives the empty state. */
    val totalCount: Int = 0,
    val watchedThisWeekMs: Long = 0L
) {
    val hasResults: Boolean get() = groups.isNotEmpty()
}

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val repository: WatchProgressRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(repository.allProgress(), query, filter) { all, q, f ->
        // "Up next" placeholders were queued, not watched.
        val watched = all.filterNot { it.isUpNext }
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val matches = watched
            .filter { entry ->
                when (f) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.MOVIES -> entry.isMovie
                    HistoryFilter.SERIES -> !entry.isMovie
                    HistoryFilter.IN_PROGRESS -> !entry.completed
                    HistoryFilter.FINISHED -> entry.completed
                }
            }
            .filter { entry ->
                q.isBlank() || entry.title.contains(q, ignoreCase = true) ||
                    entry.episodeTitle?.contains(q, ignoreCase = true) == true
            }
        HistoryUiState(
            isLoading = false,
            query = q,
            filter = f,
            groups = matches.groupBy { dayLabel(it.updatedAt) }.map { (label, items) -> HistoryGroup(label, items) },
            totalCount = watched.size,
            watchedThisWeekMs = watched.filter { it.updatedAt >= weekAgo }.sumOf { it.positionMs }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: HistoryFilter) {
        filter.value = value
    }

    fun remove(entry: WatchProgress) {
        viewModelScope.launch { repository.clearEpisode(entry.mediaType, entry.tmdbId, entry.season, entry.episode) }
    }

    /** Puts back an entry removed a moment ago (Undo). */
    fun restore(entry: WatchProgress) = repository.record(entry)

    fun clearAll() {
        viewModelScope.launch { repository.clearAll() }
    }

    private fun dayLabel(timestamp: Long): String {
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val daysAgo = ChronoUnit.DAYS.between(day, today)
        return when {
            daysAgo <= 0L -> context.getString(R.string.hi_today)
            daysAgo == 1L -> context.getString(R.string.hi_yesterday)
            daysAgo < 7L -> context.getString(R.string.hi_this_week)
            day.year == today.year && day.month == today.month -> context.getString(R.string.hi_earlier_month)
            else -> day.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
        }
    }
}
