package com.ivor.openstream.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.service.HlsDownloadService
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.streaming.DownloadRequestHeaderStore
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.domain.model.DownloadStatus
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import android.app.DownloadManager as SystemDownloadManager

/**
 * Every download goes through Media3's [DownloadManager]: HLS and progressive MP4 alike, so there is
 * one queue, one notification and one source of progress. Offline playback reads the same cache.
 *
 * Rows written by older builds (ids starting with `hls_`, or numeric Android DownloadManager ids)
 * keep working for playback and removal.
 */
@OptIn(UnstableApi::class)
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val media3: DownloadManager,
    private val headerStore: DownloadRequestHeaderStore,
    private val streamingRepository: StreamingRepository,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json,
    private val appSettings: AppSettingsStore,
    private val savedSubtitles: SavedSubtitleRepository
) : DownloadRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolutionJobs = ConcurrentHashMap<String, Job>()
    private val resolutionSlots = Semaphore(MAX_PARALLEL_RESOLUTIONS)
    private val systemDownloads by lazy {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as SystemDownloadManager
    }

    init {
        // Media3 calls listeners on the thread that created the manager (the main thread).
        media3.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                scope.launch { writeProgress(download, finalException) }
            }
        })
        scope.launch(Dispatchers.Main) { trackProgress() }
        scope.launch {
            runCatching { reconcileWithMedia3() }.onFailure { Log.w(TAG, "Could not reconcile downloads", it) }
            recoverInterruptedResolutions()
        }
    }

    override fun getAllDownloads(): Flow<List<DownloadEntity>> = dao.getAllDownloads()

    override fun getDownloadByContent(tmdbId: Int, season: Int, episode: Int, mediaType: String): Flow<DownloadEntity?> =
        dao.getDownloadByContent(tmdbId, season, episode, mediaType)

    override fun getDownloadsForTitle(tmdbId: Int, mediaType: String): Flow<List<DownloadEntity>> =
        dao.getDownloadsForTitle(tmdbId, mediaType)

    override suspend fun download(server: VideoServer, target: DownloadTarget) {
        check(server.isDownloadable) { "${server.name} does not support downloads" }
        resolutionJobs.remove(target.id)?.cancel()
        // The app scope, not the caller's: leaving the player halfway must not leave a row that
        // says "Queued" with nothing behind it.
        val job = scope.async { start(server, target) }
        resolutionJobs[target.id] = job
        job.invokeOnCompletion { resolutionJobs.remove(target.id, job) }
        job.await()
    }

    override fun enqueue(targets: List<DownloadTarget>) {
        targets.forEach { target ->
            scope.launch {
                val existing = dao.getDownloadById(target.id)
                if (existing != null && (existing.status == DownloadStatus.COMPLETED || DownloadStatus.isActive(existing.status))) {
                    return@launch
                }
                dao.insertDownload(placeholder(target))
                resolveAndStart(target)
            }
        }
    }

    override fun pause(downloadId: String) {
        if (!isMedia3(downloadId)) return
        DownloadService.sendSetStopReason(context, HlsDownloadService::class.java, downloadId, STOP_REASON_PAUSED, false)
    }

    override fun resume(downloadId: String) {
        if (!isMedia3(downloadId)) return
        DownloadService.sendSetStopReason(context, HlsDownloadService::class.java, downloadId, Download.STOP_REASON_NONE, false)
    }

    override fun retry(downloadId: String) {
        scope.launch {
            val entity = dao.getDownloadById(downloadId) ?: return@launch
            val target = entity.toTarget()
            if (downloadId != target.id) removeEntry(downloadId)
            removeFromMedia3(target.id)
            dao.insertDownload(placeholder(target))
            resolveAndStart(target)
        }
    }

    /** Also deletes the subtitles saved for it; they were saved to go with this download. */
    override suspend fun removeDownload(downloadId: String) {
        val entity = dao.getDownloadById(downloadId)
        removeEntry(downloadId)
        if (entity != null) {
            runCatching { savedSubtitles.deleteAll(entity.mediaType, entity.tmdbId, entity.season, entity.episode) }
                .onFailure { Log.w(TAG, "Could not delete saved subtitles for $downloadId", it) }
        }
    }

    private suspend fun removeEntry(downloadId: String) {
        resolutionJobs.remove(downloadId)?.cancel()
        if (isMedia3(downloadId)) {
            removeFromMedia3(downloadId)
        } else {
            runCatching { systemDownloads.remove(downloadId.toLong()) }
        }
        dao.deleteDownloadById(downloadId)
    }

    override suspend fun getPlaybackUri(downloadId: String): String? {
        val entity = dao.getDownloadById(downloadId) ?: return null
        if (isMedia3(downloadId)) return entity.uri // Served from the download cache.
        return runCatching { systemDownloads.getUriForDownloadedFile(downloadId.toLong())?.toString() }
            .getOrNull() ?: entity.uri
    }

    // region Starting downloads

    private fun resolveAndStart(target: DownloadTarget) {
        resolutionJobs.remove(target.id)?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            resolutionSlots.withPermit {
                val server = findDownloadableServer(target)
                if (server == null) {
                    dao.updateProgress(target.id, DownloadStatus.FAILED, 0, 0, 0, "No downloadable source found")
                } else {
                    try {
                        start(server, target)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        dao.updateProgress(target.id, DownloadStatus.FAILED, 0, 0, 0, error.message ?: "Could not start")
                    }
                }
            }
        }
        resolutionJobs[target.id] = job
        // Only drop this job's entry; a retry may already have registered a newer one.
        job.invokeOnCompletion { resolutionJobs.remove(target.id, job) }
        job.start()
    }

    private suspend fun findDownloadableServer(target: DownloadTarget): VideoServer? {
        var latest = emptyList<VideoServer>()
        withTimeoutOrNull(RESOLUTION_TIMEOUT_MS) {
            streamingRepository.resolveServers(target.toIdentity())
                .onEach { latest = it.servers }
                .first { it.isComplete }
        }
        // Servers arrive ranked best-first.
        return latest.firstOrNull { it.isDownloadable }
    }

    private suspend fun start(server: VideoServer, target: DownloadTarget) {
        val streamUrl = withContext(Dispatchers.IO) { singleRenditionUrl(server) }
        headerStore.register(streamUrl, server.headers)
        headerStore.register(server.url, server.headers)

        val request = DownloadRequest.Builder(target.id, Uri.parse(streamUrl))
            .setMimeType(if (server.isHls || streamUrl.isHls()) MimeTypes.APPLICATION_M3U8 else null)
            .build()

        dao.insertDownload(
            placeholder(target).copy(
                uri = streamUrl,
                status = DownloadStatus.QUEUED,
                providerId = server.providerId,
                serverId = server.id,
                serverName = server.name,
                requestHeadersJson = json.encodeToString(server.headers),
                resolvedAt = server.resolvedAt
            )
        )
        // The stream's own subtitles, so Downloads can offer them for saving later.
        runCatching {
            savedSubtitles.rememberStreamSubtitles(
                target.mediaType, target.tmdbId, target.season, target.episode,
                SavedSubtitleRepository.streamCandidates(server.providerName, server.subtitles, server.headers)
            )
        }.onFailure { Log.w(TAG, "Could not record stream subtitles for ${target.id}", it) }
        withContext(Dispatchers.Main) {
            // Replace any older copy so segments from two different links never mix.
            if (media3.downloadIndex.getDownload(target.id) != null) media3.removeDownload(target.id)
            val started = runCatching {
                DownloadService.sendAddDownload(context, HlsDownloadService::class.java, request, true)
            }.isSuccess
            // Starting a foreground service from the background is blocked on Android 12+;
            // the manager still downloads while the app process is alive.
            if (!started) media3.addDownload(request)
        }
    }

    /**
     * Master playlists list every quality; downloading one would fetch all of them. Pick the best
     * rendition up to the quality set in Settings (1080p by default) instead, unless audio lives in
     * separate renditions (then keep the master).
     */
    private fun singleRenditionUrl(server: VideoServer): String {
        if (!server.isHls && !server.url.isHls()) return server.url
        val body = runCatching {
            client.newCall(
                Request.Builder()
                    .url(server.url)
                    .apply { server.headers.forEach { (name, value) -> header(name, value) } }
                    .build()
            ).execute().use { response -> if (response.isSuccessful) response.body?.string() else null }
        }.getOrNull() ?: return server.url

        if ("#EXT-X-STREAM-INF" !in body) return server.url
        if (Regex("#EXT-X-MEDIA:[^\\n]*TYPE=AUDIO[^\\n]*URI=").containsMatchIn(body)) return server.url

        val lines = body.lines().map(String::trim)
        val variants = lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF")) return@mapIndexedNotNull null
            val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                ?: return@mapIndexedNotNull null
            val height = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            Triple(uri, height, bandwidth)
        }
        val maxHeight = appSettings.current.downloadMaxHeight
        val chosen = variants.filter { it.second in 1..maxHeight }.maxByOrNull { it.second * 10_000_000L + it.third }
            // Nothing that small: take the smallest listed size rather than the largest.
            ?: variants.filter { it.second > 0 }.minByOrNull { it.second }
            ?: variants.maxByOrNull { it.third }
            ?: return server.url
        return server.url.toHttpUrlOrNull()?.resolve(chosen.first)?.toString() ?: server.url
    }

    // endregion

    // region Progress

    private suspend fun trackProgress() {
        var tick = 0
        while (true) {
            // Listeners fire on state changes only; byte progress has to be polled while running.
            media3.currentDownloads
                .filter { it.state == Download.STATE_DOWNLOADING }
                .forEach { download -> scope.launch { writeProgress(download, null) } }
            if (tick++ % 3 == 0) scope.launch { syncLegacySystemDownloads() }
            delay(PROGRESS_INTERVAL_MS)
        }
    }

    private suspend fun writeProgress(download: Download, finalException: Exception?) {
        val status = when (download.state) {
            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
            Download.STATE_FAILED -> DownloadStatus.FAILED
            Download.STATE_DOWNLOADING -> DownloadStatus.RUNNING
            Download.STATE_STOPPED -> DownloadStatus.PAUSED
            Download.STATE_QUEUED -> if (download.stopReason != Download.STOP_REASON_NONE) DownloadStatus.PAUSED else DownloadStatus.QUEUED
            else -> DownloadStatus.QUEUED
        }
        val progress = when {
            status == DownloadStatus.COMPLETED -> 100
            download.percentDownloaded < 0 -> 0
            else -> download.percentDownloaded.toInt().coerceIn(0, 100)
        }
        val error = finalException?.let { "Source stopped responding" }
            ?: if (status == DownloadStatus.FAILED) "Download failed" else null
        if (finalException != null) Log.w(TAG, "Download ${download.request.id} failed", finalException)
        dao.updateProgress(
            downloadId = download.request.id,
            status = status,
            progress = progress,
            downloadedBytes = download.bytesDownloaded,
            // HLS has no length up front (C.LENGTH_UNSET); once finished, what was fetched is the size.
            totalBytes = when {
                download.contentLength > 0 -> download.contentLength
                status == DownloadStatus.COMPLETED -> download.bytesDownloaded
                else -> 0L
            },
            errorMessage = error
        )
    }

    private suspend fun syncLegacySystemDownloads() {
        val legacy = dao.getAllDownloads().first().filter { !isMedia3(it.downloadId) }
        if (legacy.isEmpty()) return
        val query = SystemDownloadManager.Query().setFilterById(*legacy.map { it.downloadId.toLong() }.toLongArray())
        runCatching {
            systemDownloads.query(query).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_ID)).toString()
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_STATUS))
                    val done = cursor.getLong(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(SystemDownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val progress = if (total > 0) ((done * 100) / total).toInt() else 0
                    dao.updateProgress(id, status, progress, done, total, null)
                }
            }
        }
    }

    /**
     * Room rows and Media3's download index can drift apart: the process dies between writing a
     * row and handing the request to Media3, or a row is deleted while Media3 still holds the
     * download. Left alone, those show as "Queued" forever, or keep downloading and using storage
     * with nothing in the list. Runs once per launch, before anything new is queued against it.
     */
    private suspend fun reconcileWithMedia3() {
        awaitMedia3Initialized()
        val startedAt = System.currentTimeMillis()
        // Read the index before the rows: a download added in between then has its row too.
        val indexed = HashMap<String, Download>()
        media3.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) cursor.download.let { indexed[it.request.id] = it }
        }
        val rows = dao.getAllDownloads().first().filter { isMedia3(it.downloadId) }
        val rowIds = rows.mapTo(HashSet()) { it.downloadId }

        indexed.values
            .filter { it.request.id !in rowIds && it.startTimeMs < startedAt }
            .forEach { orphan ->
                Log.i(TAG, "Removing download ${orphan.request.id} that has no entry in the list")
                removeFromMedia3(orphan.request.id)
            }

        rows.filter { it.status != DownloadStatus.RESOLVING && it.dateAdded < startedAt && !resolutionJobs.containsKey(it.downloadId) }
            .forEach { row ->
                val download = indexed[row.downloadId]
                when {
                    download != null -> writeProgress(download, null)
                    row.status == DownloadStatus.FAILED -> Unit
                    else -> dao.updateProgress(
                        row.downloadId, DownloadStatus.FAILED, 0, 0, 0,
                        if (row.status == DownloadStatus.COMPLETED) "Downloaded files are missing" else "Download was interrupted"
                    )
                }
            }
    }

    private suspend fun awaitMedia3Initialized() = withContext(Dispatchers.Main) {
        if (media3.isInitialized) return@withContext
        suspendCancellableCoroutine { continuation ->
            media3.addListener(object : DownloadManager.Listener {
                override fun onInitialized(downloadManager: DownloadManager) {
                    downloadManager.removeListener(this)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            })
        }
    }

    /** Resolution jobs die with the process; pick those rows back up on the next launch. */
    private suspend fun recoverInterruptedResolutions() {
        dao.getAllDownloads().first()
            .filter { it.status == DownloadStatus.RESOLVING }
            .forEach { resolveAndStart(it.toTarget()) }
    }

    // endregion

    private suspend fun removeFromMedia3(downloadId: String) = withContext(Dispatchers.Main) {
        runCatching {
            DownloadService.sendRemoveDownload(context, HlsDownloadService::class.java, downloadId, false)
        }.onFailure { media3.removeDownload(downloadId) }
    }

    private fun placeholder(target: DownloadTarget) = DownloadEntity(
        downloadId = target.id,
        tmdbId = target.tmdbId,
        title = target.showTitle,
        posterPath = target.posterPath,
        mediaType = target.mediaType,
        season = target.season,
        episode = target.episode,
        uri = "",
        status = DownloadStatus.RESOLVING,
        progress = 0,
        showTitle = target.showTitle,
        episodeTitle = target.episodeTitle,
        stillPath = target.stillPath,
        year = target.year
    )

    private fun DownloadEntity.toTarget() = DownloadTarget(
        tmdbId = tmdbId,
        mediaType = mediaType,
        season = season,
        episode = episode,
        showTitle = displayTitle,
        episodeTitle = episodeTitle,
        posterPath = posterPath,
        stillPath = stillPath,
        year = year
    )

    private fun isMedia3(downloadId: String) = downloadId.startsWith("dl_") || downloadId.startsWith("hls_")

    private fun String.isHls(): Boolean = ".m3u8" in lowercase() || "/manifest" in lowercase()

    private companion object {
        const val TAG = "Downloads"
        const val STOP_REASON_PAUSED = 1
        const val MAX_PARALLEL_RESOLUTIONS = 2
        const val RESOLUTION_TIMEOUT_MS = 30_000L
        const val PROGRESS_INTERVAL_MS = 1_000L
    }
}
