package com.ivor.openstream.presentation.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.downloads.DownloadStorageExporter
import com.ivor.openstream.data.downloads.ExportException
import com.ivor.openstream.data.downloads.ExportState
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.local.entity.LocalMediaEntity
import com.ivor.openstream.data.repository.LocalMediaRepository
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DownloadTab {
    DOWNLOADS,
    LOCAL_FILES
}

/** Finished downloads of one show or movie. */
data class DownloadGroup(
    val key: String,
    val title: String,
    val posterPath: String?,
    val isMovie: Boolean,
    val items: List<DownloadEntity>,
    val totalBytes: Long
)

data class DownloadsUiState(
    val isLoading: Boolean = true,
    /** Finding a source, queued, downloading, paused or failed. */
    val inProgress: List<DownloadEntity> = emptyList(),
    val library: List<DownloadGroup> = emptyList(),
    val completedCount: Int = 0,
    val storedBytes: Long = 0L,
    /** Free space on the volume downloads are written to. */
    val freeBytes: Long = 0L,
    /** Saved subtitles per movie or episode, by [SavedSubtitleRepository.keyFor]. */
    val subtitleCounts: Map<String, Int> = emptyMap(),
    val selectedTab: DownloadTab = DownloadTab.DOWNLOADS,
    val localFiles: List<LocalMediaEntity> = emptyList(),
    val sortOrder: LocalMediaSortOrder = LocalMediaSortOrder.DATE_ADDED_DESC,
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val exportStates: Map<String, ExportState> = emptyMap()
) {
    fun subtitleCount(download: DownloadEntity): Int = subtitleCounts[
        SavedSubtitleRepository.keyFor(download.mediaType, download.tmdbId, download.season, download.episode)
    ] ?: 0

    val isEmpty: Boolean get() = !isLoading && inProgress.isEmpty() && library.isEmpty()
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DownloadRepository,
    private val localMediaRepository: LocalMediaRepository,
    val storageExporter: DownloadStorageExporter,
    savedSubtitles: SavedSubtitleRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(DownloadTab.DOWNLOADS)
    private val _sortOrder = MutableStateFlow(LocalMediaSortOrder.DATE_ADDED_DESC)
    private val _searchQuery = MutableStateFlow("")
    private val _isImporting = MutableStateFlow(false)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private data class LocalFilter(
        val tab: DownloadTab,
        val sortOrder: LocalMediaSortOrder,
        val query: String,
        val importing: Boolean
    )

    private val localFilter = combine(
        _selectedTab,
        _sortOrder,
        _searchQuery,
        _isImporting
    ) { tab, sortOrder, query, importing ->
        LocalFilter(tab, sortOrder, query, importing)
    }

    val uiState: StateFlow<DownloadsUiState> = combine(
        repository.getAllDownloads(),
        savedSubtitles.observeCounts(),
        localMediaRepository.getAllLocalMedia(),
        localFilter,
        storageExporter.exportStates
    ) { downloads, subtitleCounts, localMedia, filter, exportStates ->
        val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }

        val filteredLocal = if (filter.query.isBlank()) {
            localMedia
        } else {
            localMedia.filter { it.displayName.contains(filter.query, ignoreCase = true) }
        }
        val sortedLocal = filter.sortOrder.sort(filteredLocal)

        DownloadsUiState(
            isLoading = false,
            inProgress = downloads
                .filter { it.status != DownloadStatus.COMPLETED }
                .sortedWith(compareBy<DownloadEntity> { it.status == DownloadStatus.FAILED }.thenBy { it.dateAdded }),
            library = completed
                .groupBy { "${it.mediaType}:${it.tmdbId}" }
                .map { (key, items) ->
                    val sorted = items.sortedWith(compareBy({ it.season }, { it.episode }))
                    DownloadGroup(
                        key = key,
                        title = sorted.first().displayTitle,
                        posterPath = sorted.first().posterPath,
                        isMovie = sorted.first().mediaType == "movie",
                        items = sorted,
                        totalBytes = sorted.sumOf { it.sizeBytes }
                    )
                }
                .sortedByDescending { group -> group.items.maxOf { it.dateAdded } },
            completedCount = completed.size,
            storedBytes = completed.sumOf { it.sizeBytes },
            freeBytes = (context.getExternalFilesDir(null) ?: context.filesDir).usableSpace,
            subtitleCounts = subtitleCounts,
            selectedTab = filter.tab,
            localFiles = sortedLocal,
            sortOrder = filter.sortOrder,
            searchQuery = filter.query,
            isImporting = filter.importing,
            exportStates = exportStates
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun setSelectedTab(tab: DownloadTab) {
        _selectedTab.value = tab
    }

    fun setSortOrder(order: LocalMediaSortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val imported = localMediaRepository.importUris(uris)
                _events.tryEmit(context.resources.getQuantityString(R.plurals.local_import_success, imported.size, imported.size))
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: context.getString(R.string.dl_could_not_read_file))
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun exportToStorage(download: DownloadEntity) {
        viewModelScope.launch {
            storageExporter.exportToStorage(download)
                .onSuccess { _events.tryEmit(context.getString(R.string.dl_saved_to_storage)) }
                .onFailure { error ->
                    // The exporter's own reason when it has one (protected stream, permission...).
                    _events.tryEmit((error as? ExportException)?.message ?: context.getString(R.string.dl_save_to_storage_failed))
                }
        }
    }

    fun onStoragePermissionDenied() {
        _events.tryEmit(context.getString(R.string.dl_storage_permission_needed))
    }

    /** Takes the file off the list; the file itself stays where the user keeps it. */
    fun removeLocalMedia(id: Long) {
        viewModelScope.launch { localMediaRepository.remove(id) }
    }

    fun pause(download: DownloadEntity) = repository.pause(download.downloadId)

    fun resume(download: DownloadEntity) = repository.resume(download.downloadId)

    fun retry(download: DownloadEntity) = repository.retry(download.downloadId)

    fun remove(download: DownloadEntity) {
        viewModelScope.launch { repository.removeDownload(download.downloadId) }
    }

    /** Removes every download, finished or not. */
    fun removeAll() {
        viewModelScope.launch {
            repository.getAllDownloads().first().forEach { repository.removeDownload(it.downloadId) }
        }
    }

    fun removeGroup(group: DownloadGroup) {
        viewModelScope.launch { group.items.forEach { repository.removeDownload(it.downloadId) } }
    }
}
