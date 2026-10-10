package com.ivor.openstream.presentation.watch_later

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.local.dao.CustomListSummary
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.repository.CustomListRepository
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchLaterRepository
import com.ivor.openstream.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SavedFilter(val label: String, @StringRes val labelRes: Int) {
    ALL("All", R.string.li_saved_all),
    MOVIES("Movies", R.string.li_saved_movies),
    SERIES("Series", R.string.li_saved_series),
    STARTED("Started", R.string.li_saved_started)
}

enum class SavedSort(val label: String, @StringRes val labelRes: Int) {
    RECENTLY_ADDED("Recently added", R.string.li_sort_recently_added),
    RECENTLY_WATCHED("Recently watched", R.string.li_sort_recently_watched),
    TITLE("A–Z", R.string.li_sort_az),
    RATING("Top rated", R.string.li_sort_rating)
}

/** A saved title plus the most recent thing watched from it, if anything. */
data class SavedItem(val entry: WatchLaterEntity, val latest: WatchProgress?)

data class SavedUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val filter: SavedFilter = SavedFilter.ALL,
    val sort: SavedSort = SavedSort.RECENTLY_ADDED,
    val items: List<SavedItem> = emptyList(),
    val totalCount: Int = 0
)

@HiltViewModel
class WatchLaterViewModel @Inject constructor(
    private val repository: WatchLaterRepository,
    progressRepository: WatchProgressRepository,
    private val listRepository: CustomListRepository
) : ViewModel() {

    /** The user's own lists, most recently changed first. */
    val lists: StateFlow<List<CustomListSummary>> = listRepository.summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates a list and hands back its id (to open it). */
    fun createList(name: String, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(listRepository.create(name)) }
    }

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SavedFilter.ALL)
    private val sort = MutableStateFlow(SavedSort.RECENTLY_ADDED)

    val uiState: StateFlow<SavedUiState> = combine(
        repository.getWatchLaterList(),
        progressRepository.allProgress(),
        query,
        filter,
        sort
    ) { saved, progress, q, f, s ->
        // Progress is newest first, so the first row per title is its latest.
        val latestByTitle = progress.filterNot { it.isUpNext }.groupBy { it.mediaType to it.tmdbId }.mapValues { it.value.first() }
        val items = saved
            .map { SavedItem(it, latestByTitle[it.mediaType to it.id]) }
            .filter { item ->
                when (f) {
                    SavedFilter.ALL -> true
                    SavedFilter.MOVIES -> item.entry.mediaType == "movie"
                    SavedFilter.SERIES -> item.entry.mediaType != "movie"
                    SavedFilter.STARTED -> item.latest != null
                }
            }
            .filter { q.isBlank() || it.entry.title.contains(q, ignoreCase = true) }
            .let { list ->
                when (s) {
                    SavedSort.RECENTLY_ADDED -> list.sortedByDescending { it.entry.dateAdded }
                    SavedSort.RECENTLY_WATCHED -> list.sortedByDescending { it.latest?.updatedAt ?: 0L }
                    SavedSort.TITLE -> list.sortedBy { it.entry.title.lowercase() }
                    SavedSort.RATING -> list.sortedByDescending { it.entry.voteAverage }
                }
            }
        SavedUiState(isLoading = false, query = q, filter = f, sort = s, items = items, totalCount = saved.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: SavedFilter) {
        filter.value = value
    }

    fun onSortChange(value: SavedSort) {
        sort.value = value
    }

    fun remove(entry: WatchLaterEntity) {
        viewModelScope.launch { repository.removeFromWatchLater(entry) }
    }

    /** Undo: puts the title back with its original date so its position doesn't change. */
    fun restore(entry: WatchLaterEntity) {
        viewModelScope.launch { repository.addToWatchLater(entry) }
    }
}
