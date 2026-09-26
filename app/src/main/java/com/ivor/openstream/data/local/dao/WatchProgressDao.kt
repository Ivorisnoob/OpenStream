package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE id = :id")
    suspend fun get(id: String): WatchProgressEntity?

    /** The most recently touched unfinished entry of every title, newest first. */
    @Query(
        """
        SELECT * FROM watch_progress AS p
        WHERE p.completed = 0
          AND p.updatedAt = (
              SELECT MAX(q.updatedAt) FROM watch_progress AS q
              WHERE q.tmdbId = p.tmdbId AND q.mediaType = p.mediaType
          )
        ORDER BY p.updatedAt DESC
        LIMIT :limit
        """
    )
    fun observeContinueWatching(limit: Int): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE mediaType = :mediaType AND tmdbId = :tmdbId ORDER BY updatedAt DESC")
    fun observeForTitle(mediaType: String, tmdbId: Int): Flow<List<WatchProgressEntity>>

    /** Drops in-progress rows but keeps finished ones, so watched marks survive. */
    @Query("DELETE FROM watch_progress WHERE mediaType = :mediaType AND tmdbId = :tmdbId AND completed = 0")
    suspend fun deleteUnfinished(mediaType: String, tmdbId: Int)

    @Query("DELETE FROM watch_progress WHERE mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun deleteForTitle(mediaType: String, tmdbId: Int)

    @Query("DELETE FROM watch_progress")
    suspend fun clear()
}
