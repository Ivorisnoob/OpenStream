package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

/** Every query is scoped to one profile. */
@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later WHERE profileId = :profileId ORDER BY dateAdded DESC")
    fun getAllWatchLaterItems(profileId: Long): Flow<List<WatchLaterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE profileId = :profileId AND id = :id)")
    fun isWatchLater(profileId: Long, id: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLaterItem(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE profileId = :profileId AND id = :id")
    suspend fun deleteWatchLaterItemById(profileId: Long, id: Int)
}
