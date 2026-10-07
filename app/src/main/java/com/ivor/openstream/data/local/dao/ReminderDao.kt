package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.ReminderEntity
import com.ivor.openstream.data.local.entity.TitleRatingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observe(profileId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReminderEntity)

    @Query("DELETE FROM reminders WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun delete(profileId: Long, mediaType: String, tmdbId: Int)

    @Query("DELETE FROM reminders WHERE profileId = :id")
    suspend fun deleteForProfile(id: Long)
}

@Dao
interface TitleRatingDao {
    @Query("SELECT * FROM title_ratings WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<List<TitleRatingEntity>>

    @Query("SELECT * FROM title_ratings WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<TitleRatingEntity>

    @Query("SELECT rating FROM title_ratings WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId")
    fun observeRating(profileId: Long, mediaType: String, tmdbId: Int): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TitleRatingEntity)

    @Query("DELETE FROM title_ratings WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun delete(profileId: Long, mediaType: String, tmdbId: Int)

    @Query("DELETE FROM title_ratings WHERE profileId = :id")
    suspend fun deleteForProfile(id: Long)
}
