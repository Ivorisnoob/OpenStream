package com.ivor.openstream.presentation.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val subtitleCounts: Map<String, Int> = emptyMap()
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
    savedSubtitles: SavedSubtitleRepository
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = combine(
        repository.getAllDownloads(),
        savedSubtitles.observeCounts()
    ) { downloads, subtitleCounts -> downloads to subtitleCounts }
        .map { (downloads, subtitleCounts) ->
            val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
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
                subtitleCounts = subtitleCounts
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

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
