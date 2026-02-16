package com.ivor.openanime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openanime.data.local.entity.DownloadEntity
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

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND season = :season AND episode = :episode AND mediaType = :mediaType LIMIT 1")
    fun getDownloadByContent(tmdbId: Int, season: Int, episode: Int, mediaType: String): Flow<DownloadEntity?>
}
