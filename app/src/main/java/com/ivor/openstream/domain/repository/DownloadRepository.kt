package com.ivor.openstream.domain.repository

import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    suspend fun downloadVideo(server: VideoServer, title: String, fileName: String, posterPath: String?, mediaType: String, tmdbId: Int, season: Int, episode: Int): String
    suspend fun removeDownload(downloadId: String)
    suspend fun updateDownloadStatus(downloadId: String, status: Int, progress: Int, downloadedBytes: Long, totalBytes: Long)
    suspend fun getPlaybackUri(downloadId: String): String?
    fun getDownloadByContent(tmdbId: Int, season: Int, episode: Int, mediaType: String): Flow<DownloadEntity?>
}
