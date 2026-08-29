package com.ivor.openstream.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.service.HlsDownloadService
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.DownloadRequestHeaderStore
import com.ivor.openstream.domain.repository.DownloadRepository
import com.ivor.openstream.domain.model.VideoServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val media3DownloadManager: androidx.media3.exoplayer.offline.DownloadManager,
    private val headerStore: DownloadRequestHeaderStore,
    private val json: Json
) : DownloadRepository {

    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun getAllDownloads(): Flow<List<DownloadEntity>> {
        return dao.getAllDownloads()
    }

    override suspend fun downloadVideo(
        server: VideoServer,
        title: String,
        fileName: String,
        posterPath: String?,
        mediaType: String,
        tmdbId: Int,
        season: Int,
        episode: Int
    ): String {
        check(server.isDownloadable) { "${server.name} does not support downloads" }
        return if (server.url.contains(".m3u8") || server.url.contains("/manifest")) {
            downloadHls(server, title, posterPath, mediaType, tmdbId, season, episode)
        } else {
            downloadSystem(server, title, fileName, posterPath, mediaType, tmdbId, season, episode)
        }
    }

    private suspend fun downloadSystem(server: VideoServer, title: String, fileName: String, posterPath: String?, mediaType: String, tmdbId: Int, season: Int, episode: Int): String {
        val request = DownloadManager.Request(Uri.parse(server.url))
            .setTitle(title)
            .setDescription("Downloading $title")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "OpenStream/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val requestHeaders = if (server.headers.keys.none { it.equals("User-Agent", true) }) {
            server.headers + ("User-Agent" to BROWSER_USER_AGENT)
        } else {
            server.headers
        }
        requestHeaders.forEach { (name, value) -> request.addRequestHeader(name, value) }

        val id = systemDownloadManager.enqueue(request).toString()
        val entity = DownloadEntity(
            downloadId = id,
            tmdbId = tmdbId,
            title = title,
            posterPath = posterPath,
            mediaType = mediaType,
            season = season,
            episode = episode,
            uri = server.url,
            status = DownloadManager.STATUS_PENDING,
            progress = 0,
            providerId = server.providerId,
            serverId = server.id,
            serverName = server.name,
            requestHeadersJson = json.encodeToString(server.headers),
            resolvedAt = server.resolvedAt
        )
        dao.insertDownload(entity)
        return id
    }

    private suspend fun downloadHls(server: VideoServer, title: String, posterPath: String?, mediaType: String, tmdbId: Int, season: Int, episode: Int): String {
        val id = "hls_${tmdbId}_${season}_${episode}_${server.id.hashCode()}"
        headerStore.register(server.url, server.headers)
        
        val downloadRequest = DownloadRequest.Builder(id, Uri.parse(server.url))
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        DownloadService.sendAddDownload(
            context,
            HlsDownloadService::class.java,
            downloadRequest,
            /* foreground= */ true
        )

        val entity = DownloadEntity(
            downloadId = id,
            tmdbId = tmdbId,
            title = title,
            posterPath = posterPath,
            mediaType = mediaType,
            season = season,
            episode = episode,
            uri = server.url,
            status = DownloadManager.STATUS_PENDING,
            progress = 0,
            providerId = server.providerId,
            serverId = server.id,
            serverName = server.name,
            requestHeadersJson = json.encodeToString(server.headers),
            resolvedAt = server.resolvedAt
        )
        dao.insertDownload(entity)
        return id
    }

    override suspend fun removeDownload(downloadId: String) {
        if (downloadId.startsWith("hls_")) {
            DownloadService.sendRemoveDownload(
                context,
                HlsDownloadService::class.java,
                downloadId,
                false
            )
        } else {
            try {
                systemDownloadManager.remove(downloadId.toLong())
            } catch (e: Exception) {}
        }
        dao.deleteDownloadById(downloadId)
    }

    override suspend fun updateDownloadStatus(downloadId: String, status: Int, progress: Int, downloadedBytes: Long, totalBytes: Long) {
        val existing = dao.getDownloadById(downloadId)
        if (existing != null) {
            dao.insertDownload(existing.copy(
                status = status, 
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            ))
        }
    }

    override suspend fun getPlaybackUri(downloadId: String): String? {
        val entity = dao.getDownloadById(downloadId) ?: return null
        
        return if (downloadId.startsWith("hls_")) {
            // For HLS, we return the original URI. 
            // The player will use the CacheDataSource to play from downloaded segments.
            entity.uri
        } else {
            // For system downloads, get the local content URI
            try {
                systemDownloadManager.getUriForDownloadedFile(downloadId.toLong())?.toString() ?: entity.uri
            } catch (e: Exception) {
                entity.uri
            }
        }
    }

    suspend fun syncProgress() {
        val allEntities = dao.getAllDownloads().first()
        if (allEntities.isEmpty()) return

        // 1. Sync System Downloads
        val systemIds = allEntities.filter { !it.downloadId.startsWith("hls_") }
        if (systemIds.isNotEmpty()) {
            val query = DownloadManager.Query().setFilterById(*systemIds.map { it.downloadId.toLong() }.toLongArray())
            try {
                systemDownloadManager.query(query).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)).toString()
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val prog = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        
                        updateDownloadStatus(id, status, prog, downloaded, total)
                    }
                }
            } catch (e: Exception) {}
        }

        // 2. Sync HLS Downloads
        val hlsEntities = allEntities.filter { it.downloadId.startsWith("hls_") }
        if (hlsEntities.isNotEmpty()) {
            // Ensure the service is active and downloads are not stopped
            DownloadService.sendResumeDownloads(context, HlsDownloadService::class.java, false)
            
            for (hls in hlsEntities) {
                val download = media3DownloadManager.downloadIndex.getDownload(hls.downloadId)
                if (download != null) {
                    val status = when (download.state) {
                        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> DownloadManager.STATUS_SUCCESSFUL
                        androidx.media3.exoplayer.offline.Download.STATE_FAILED -> DownloadManager.STATUS_FAILED
                        androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING -> DownloadManager.STATUS_RUNNING
                        androidx.media3.exoplayer.offline.Download.STATE_QUEUED -> DownloadManager.STATUS_PENDING
                        androidx.media3.exoplayer.offline.Download.STATE_STOPPED, 
                        androidx.media3.exoplayer.offline.Download.STATE_REMOVING,
                        androidx.media3.exoplayer.offline.Download.STATE_RESTARTING -> DownloadManager.STATUS_PAUSED
                        else -> DownloadManager.STATUS_PENDING
                    }
                    
                    android.util.Log.d("DownloadSync", "HLS Sync [${hls.title}]: State ${download.state}, ${download.bytesDownloaded} bytes / ${download.contentLength} total (${download.percentDownloaded}%)")

                    updateDownloadStatus(
                        hls.downloadId, 
                        status, 
                        download.percentDownloaded.toInt().coerceIn(-1, 100),
                        download.bytesDownloaded,
                        download.contentLength
                    )
                }
            }
        }
    }

    override fun getDownloadByContent(tmdbId: Int, season: Int, episode: Int, mediaType: String): Flow<DownloadEntity?> {
        return dao.getDownloadByContent(tmdbId, season, episode, mediaType)
    }
}
