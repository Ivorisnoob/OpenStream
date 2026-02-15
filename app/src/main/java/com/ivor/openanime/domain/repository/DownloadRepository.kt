package com.ivor.openanime.domain.repository

import com.ivor.openanime.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    suspend fun downloadVideo(url: String, title: String, fileName: String, posterPath: String?, mediaType: String, tmdbId: Int): Long
    suspend fun removeDownload(downloadId: Long)
    suspend fun updateDownloadStatus(downloadId: Long, status: Int, progress: Int)
}
