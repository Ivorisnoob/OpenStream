package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenTitleDao {
    @Query("SELECT * FROM hidden_titles ORDER BY hiddenAt DESC")
    fun observeAll(): Flow<List<HiddenTitleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(title: HiddenTitleEntity)

    @Query("DELETE FROM hidden_titles WHERE mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun delete(mediaType: String, tmdbId: Int)

    @Query("DELETE FROM hidden_titles")
    suspend fun clear()
}
