package com.ivor.openanime.domain.repository

import com.ivor.openanime.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    suspend fun downloadVideo(url: String, title: String, fileName: String, posterPath: String?, mediaType: String, tmdbId: Int): String
    suspend fun removeDownload(downloadId: String)
    suspend fun updateDownloadStatus(downloadId: String, status: Int, progress: Int, downloadedBytes: Long, totalBytes: Long)
}
