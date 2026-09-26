package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY dateAdded DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadEntity)

    @Delete
    suspend fun deleteDownload(item: DownloadEntity)

    @Query("DELETE FROM downloads WHERE downloadId = :downloadId")
    suspend fun deleteDownloadById(downloadId: String)

    @Query("SELECT * FROM downloads WHERE downloadId = :downloadId")
    suspend fun getDownloadById(downloadId: String): DownloadEntity?

    @Query("UPDATE downloads SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, errorMessage = :errorMessage WHERE downloadId = :downloadId")
    suspend fun updateProgress(
        downloadId: String,
        status: Int,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        errorMessage: String?
    )

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    fun getDownloadsForTitle(tmdbId: Int, mediaType: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND season = :season AND episode = :episode AND mediaType = :mediaType LIMIT 1")
    fun getDownloadByContent(tmdbId: Int, season: Int, episode: Int, mediaType: String): Flow<DownloadEntity?>
}
