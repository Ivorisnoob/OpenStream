package com.ivor.openstream.presentation.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.subtitles.SavedSubtitle
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.data.subtitles.StreamSubtitleCandidate
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.repository.StreamingRepository
import com.ivor.openstream.domain.repository.SubtitleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class DownloadSubtitlesUiState(
    val download: DownloadEntity? = null,
    val saved: List<SavedSubtitle> = emptyList(),
    val online: List<SubtitleDto> = emptyList(),
    val isSearching: Boolean = false,
    /** Looking the title up in the stream sources for the subtitles the video itself carries. */
    val isCheckingSource: Boolean = false,
    /** Languages the user asked for by name, still being searched. */
    val searchingLanguages: Set<String> = emptySet(),
    /** Null shows every language. */
    val languageFilter: String? = null,
    /** Hides hearing-impaired (SDH) releases from the online list. */
    val hideHearingImpaired: Boolean = false,
    /** Ids of online subtitles ticked for saving. */
    val selected: Set<String> = emptySet(),
    /** Ids of online subtitles being downloaded right now. */
    val saving: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val message: String? = null
) {
    val savedOriginIds: Set<String> get() = saved.mapNotNullTo(HashSet()) { it.originId }

    /** Languages in the results, English first, then by name. */
    val languages: List<String>
        get() = online.mapNotNull { it.language }.distinct()
            .sortedWith(compareBy<String> { it != "en" }.thenBy { SavedSubtitleRepository.languageName(it) })

    val visibleOnline: List<SubtitleDto>
        get() = online
            .filter { languageFilter == null || it.language == languageFilter }
            .filter { !hideHearingImpaired || !it.isHearingImpaired }

    /** The visible results by language, in [languages] order; unknown languages last. */
    val groupedOnline: List<Pair<String?, List<SubtitleDto>>>
        get() {
            val byLanguage = visibleOnline.groupBy { it.language }
            return (languages + listOf<String?>(null)).mapNotNull { language ->
                byLanguage[language]?.let { language to it }
            }
        }
}

/**
 * Saving subtitles for a finished download: the ones the video's own stream carries (what the
 * player lists under the source's name), the subtitle sites, or a file on the device.
 */
@HiltViewModel
class DownloadSubtitlesViewModel @Inject constructor(
    private val subtitleRepository: SubtitleRepository,
    private val savedSubtitles: SavedSubtitleRepository,
    private val streamingRepository: StreamingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadSubtitlesUiState())
    val uiState: StateFlow<DownloadSubtitlesUiState> = _uiState.asStateFlow()

    private var sessionJob: Job? = null

    /** Request headers (Referer...) the stream's hosts want, by subtitle id. */
    private val streamHeaders = ConcurrentHashMap<String, Map<String, String>>()

    fun open(download: DownloadEntity) {
        if (_uiState.value.download?.downloadId == download.downloadId && sessionJob?.isActive == true) return
        sessionJob?.cancel()
        streamHeaders.clear()
        _uiState.value = DownloadSubtitlesUiState(download = download, isSearching = true)
        sessionJob = viewModelScope.launch {
            launch {
                savedSubtitles.observe(download.mediaType, download.tmdbId, download.season, download.episode)
                    .collect { saved -> _uiState.update { it.copy(saved = saved) } }
            }
            launch { loadStreamSubtitles(download, lookUpIfMissing = true) }
            launch { search(download) }
        }
    }

    /**
     * Subtitles recorded when the download started. Downloads made before that was recorded (or
     * whose source had none then) look the title up in the stream sources again.
     */
    private suspend fun loadStreamSubtitles(download: DownloadEntity, lookUpIfMissing: Boolean) {
        val recorded = savedSubtitles.streamSubtitles(download.mediaType, download.tmdbId, download.season, download.episode)
        addStreamSubtitles(download, recorded)
        if (recorded.isNotEmpty() || !lookUpIfMissing) return

        _uiState.update { if (it.download?.downloadId == download.downloadId) it.copy(isCheckingSource = true) else it }
        val found = mutableListOf<StreamSubtitleCandidate>()
        try {
            withTimeoutOrNull(SOURCE_LOOKUP_TIMEOUT_MS) {
                streamingRepository.resolveServers(download.identity())
                    .onEach { resolution ->
                        val candidates = resolution.servers.flatMap { server ->
                            SavedSubtitleRepository.streamCandidates(server.providerName, server.subtitles, server.headers)
                        }
                        found += candidates
                        addStreamSubtitles(download, candidates)
                    }
                    .first { it.isComplete }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The sites' results still show; the source lookup is a bonus.
        } finally {
            _uiState.update { if (it.download?.downloadId == download.downloadId) it.copy(isCheckingSource = false) else it }
        }
        runCatching {
            savedSubtitles.rememberStreamSubtitles(
                download.mediaType, download.tmdbId, download.season, download.episode,
                found.distinctBy { it.subtitle.url }
            )
        }
    }

    private fun addStreamSubtitles(download: DownloadEntity, candidates: List<StreamSubtitleCandidate>) {
        if (candidates.isEmpty()) return
        candidates.forEach { streamHeaders[it.subtitle.id] = it.headers }
        _uiState.update { state ->
            if (state.download?.downloadId != download.downloadId) return@update state
            // The video's own subtitles go first: they are timed for this exact file.
            state.copy(online = (candidates.map { it.subtitle } + state.online).distinctBy { it.url })
        }
    }

    fun close() {
        sessionJob?.cancel()
        sessionJob = null
        _uiState.value = DownloadSubtitlesUiState()
    }

    fun retrySearch() {
        val download = _uiState.value.download ?: return
        _uiState.update { it.copy(isSearching = true, message = null) }
        viewModelScope.launch { search(download) }
        if (!_uiState.value.isCheckingSource && _uiState.value.online.none { it.id.startsWith("stream_") }) {
            viewModelScope.launch { loadStreamSubtitles(download, lookUpIfMissing = true) }
        }
    }

    fun setLanguageFilter(language: String?) = _uiState.update { it.copy(languageFilter = language) }

    fun setHideHearingImpaired(hide: Boolean) = _uiState.update { it.copy(hideHearingImpaired = hide) }

    fun toggle(subtitle: SubtitleDto) = _uiState.update { state ->
        if (subtitle.id in state.savedOriginIds || subtitle.id in state.saving) return@update state
        state.copy(selected = if (subtitle.id in state.selected) state.selected - subtitle.id else state.selected + subtitle.id)
    }

    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }

    /**
     * Saves every ticked subtitle, two at a time so the subtitle sites are not flooded. Keeps
     * going if the sheet is closed; they land in the folder all the same.
     */
    fun saveSelected() {
        val state = _uiState.value
        val download = state.download ?: return
        val picked = state.online.filter { it.id in state.selected }
        if (picked.isEmpty()) return
        _uiState.update { it.copy(selected = emptySet(), saving = it.saving + picked.map { s -> s.id }, message = null) }
        viewModelScope.launch {
            picked.chunked(2).forEach { batch ->
                batch.map { subtitle -> launch { saveNow(download, subtitle) } }.joinAll()
            }
        }
    }

    /** Looks for more releases in [language], which the general search may have left out. */
    fun searchLanguage(language: String) {
        val download = _uiState.value.download ?: return
        _uiState.update { it.copy(languageFilter = language, searchingLanguages = it.searchingLanguages + language, message = null) }
        viewModelScope.launch {
            val found = runCatching { subtitleRepository.searchLanguage(download.identity(), language) }
                .getOrDefault(emptyList())
            _uiState.update { state ->
                if (state.download?.downloadId != download.downloadId) return@update state
                state.copy(
                    online = (state.online + found).distinctBy { it.url },
                    searchingLanguages = state.searchingLanguages - language,
                    message = if (found.isEmpty() && state.online.none { it.language == language }) {
                        context.getString(
                            R.string.sub_no_lang_found,
                            SavedSubtitleRepository.languageName(language)
                        )
                    } else {
                        null
                    }
                )
            }
        }
    }

    private suspend fun saveNow(download: DownloadEntity, subtitle: SubtitleDto) {
        val result = runCatching {
            savedSubtitles.save(
                download.mediaType, download.tmdbId, download.season, download.episode, subtitle,
                headers = streamHeaders[subtitle.id].orEmpty()
            )
        }
        if (result.exceptionOrNull() is CancellationException) return
        _uiState.update { state ->
            if (state.download?.downloadId != download.downloadId) return@update state
            state.copy(
                saving = state.saving - subtitle.id,
                message = result.exceptionOrNull()?.let { error ->
                    context.getString(
                        R.string.misc_could_not_save_detail,
                        subtitle.display ?: context.getString(R.string.misc_a_subtitle),
                        error.message ?: context.getString(R.string.misc_download_failed)
                    )
                } ?: state.message
            )
        }
    }

    fun importFile(uri: Uri) {
        val download = _uiState.value.download ?: return
        _uiState.update { it.copy(isImporting = true, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                savedSubtitles.import(download.mediaType, download.tmdbId, download.season, download.episode, uri)
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    message = result.fold(
                        onSuccess = { saved -> if (saved.language == null) context.getString(R.string.dl_saved_unknown_lang) else null },
                        onFailure = { error -> error.message ?: context.getString(R.string.dl_could_not_read_file) }
                    )
                )
            }
        }
    }

    fun delete(saved: SavedSubtitle) {
        val download = _uiState.value.download ?: return
        viewModelScope.launch {
            savedSubtitles.delete(download.mediaType, download.tmdbId, download.season, download.episode, saved.id)
        }
    }

    private suspend fun search(download: DownloadEntity) {
        val found = runCatching { subtitleRepository.search(download.identity()) }.getOrDefault(emptyList())
        _uiState.update { state ->
            if (state.download?.downloadId != download.downloadId) return@update state
            state.copy(online = (state.online + found).distinctBy { it.url }, isSearching = false)
        }
    }

    private fun DownloadEntity.identity() = MediaIdentity(
        tmdbId = tmdbId,
        tmdbType = mediaType,
        title = displayTitle,
        season = season,
        episode = episode,
        year = year
    )

    private companion object {
        const val SOURCE_LOOKUP_TIMEOUT_MS = 30_000L
    }
}
