package com.ivor.openstream.data.subtitles

import android.util.Log
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.remote.model.SubtitleDto
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.repository.SubtitleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves subtitles alongside each download, in the languages chosen in Settings: every one the
 * video's own stream carries in those languages, plus (if enabled) every OpenSubtitles and SubSource
 * release in them. Runs in the app's scope when a download starts, so they are there offline.
 */
@Singleton
class DownloadSubtitleSaver @Inject constructor(
    private val savedSubtitles: SavedSubtitleRepository,
    private val subtitleRepository: SubtitleRepository,
    private val appSettings: AppSettingsStore,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    fun saveFor(target: DownloadTarget, stream: List<StreamSubtitleCandidate>) {
        val settings = appSettings.current
        val languages = settings.subtitleDownloadLanguages.toSet()
        if (languages.isEmpty()) return
        jobs.remove(target.id)?.cancel()
        val job = scope.launch {
            val picks = mutableListOf<Pair<SubtitleDto, Map<String, String>>>()
            stream.filter { it.subtitle.language in languages }.forEach { picks += it.subtitle to it.headers }
            if (settings.subtitleDownloadFromSites) picks += fromSites(target, languages).map { it to emptyMap() }

            picks.distinctBy { it.first.url }.forEach { (subtitle, headers) ->
                try {
                    savedSubtitles.save(
                        target.mediaType, target.tmdbId, target.season, target.episode, subtitle, headers
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Could not save ${subtitle.display} for ${target.id}: ${error.message}")
                }
            }
            // Deleted while these were being fetched: don't leave them behind.
            if (downloadDao.getDownloadById(target.id) == null) {
                savedSubtitles.deleteAll(target.mediaType, target.tmdbId, target.season, target.episode)
            }
        }
        jobs[target.id] = job
        job.invokeOnCompletion { jobs.remove(target.id, job) }
    }

    /** Stops saving for a download that is being removed. */
    fun cancel(downloadId: String) {
        jobs.remove(downloadId)?.cancel()
    }

    private suspend fun fromSites(target: DownloadTarget, languages: Set<String>): List<SubtitleDto> {
        val identity = target.toIdentity()
        val general = runCatching { subtitleRepository.search(identity) }.getOrDefault(emptyList())
        // The general search covers English and a few others; the rest have to be asked for by name.
        val missing = languages.filter { language -> general.none { it.language == language } }
        val asked = missing.flatMap { language ->
            runCatching { subtitleRepository.searchLanguage(identity, language) }.getOrDefault(emptyList())
        }
        return (general + asked).filter { it.language in languages }
    }

    private companion object {
        const val TAG = "DownloadSubtitles"
    }
}
