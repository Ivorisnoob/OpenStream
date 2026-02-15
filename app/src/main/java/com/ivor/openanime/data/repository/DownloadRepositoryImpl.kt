package com.ivor.openanime.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.ivor.openanime.data.local.dao.DownloadDao
import com.ivor.openanime.data.local.entity.DownloadEntity
import com.ivor.openanime.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao
) : DownloadRepository {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun getAllDownloads(): Flow<List<DownloadEntity>> {
        return dao.getAllDownloads()
    }

    override suspend fun downloadVideo(
        url: String,
        title: String,
        fileName: String,
        posterPath: String?,
        mediaType: String,
        tmdbId: Int
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Downloading $title")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "OpenStream/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        val downloadId = downloadManager.enqueue(request)

        val entity = DownloadEntity(
            downloadId = downloadId,
            tmdbId = tmdbId,
            title = title,
            posterPath = posterPath,
            mediaType = mediaType,
            uri = Uri.parse(url).toString(),
            status = DownloadManager.STATUS_PENDING,
            progress = 0
        )
        dao.insertDownload(entity)
        return downloadId
    }

    override suspend fun removeDownload(downloadId: Long) {
        downloadManager.remove(downloadId)
        dao.deleteDownloadById(downloadId)
    }

    override suspend fun updateDownloadStatus(downloadId: Long, status: Int, progress: Int) {
        val existing = dao.getDownloadById(downloadId)
        if (existing != null) {
            dao.insertDownload(existing.copy(status = status, progress = progress))
        }
    }

    suspend fun syncProgress() {
        val downloads = dao.getAllDownloads().first()
        val query = DownloadManager.Query().setFilterById(*downloads.map { it.downloadId }.toLongArray())

        try {
            val cursor = downloadManager.query(query)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                val progress = if (bytesTotal > 0) {
                    ((bytesDownloaded * 100) / bytesTotal).toInt()
                } else 0

                updateDownloadStatus(id, status, progress)
            }
            cursor.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
