package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import kotlinx.coroutines.flow.Flow

/** Every query is scoped to one profile. */
@Dao
interface HiddenTitleDao {
    @Query("SELECT * FROM hidden_titles WHERE profileId = :profileId ORDER BY hiddenAt DESC")
    fun observeAll(profileId: Long): Flow<List<HiddenTitleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(title: HiddenTitleEntity)

    @Query("DELETE FROM hidden_titles WHERE profileId = :profileId AND mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun delete(profileId: Long, mediaType: String, tmdbId: Int)

    @Query("DELETE FROM hidden_titles WHERE profileId = :profileId")
    suspend fun clear(profileId: Long)
}
