package com.ivor.openanime.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openanime.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY dateAdded DESC")
    fun getAllWatchLaterItems(): Flow<List<WatchLaterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watch_later WHERE id = :id)")
    fun isWatchLater(id: Int): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLaterItem(item: WatchLaterEntity)

    @Delete
    suspend fun deleteWatchLaterItem(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE id = :id")
    suspend fun deleteWatchLaterItemById(id: Int)
}
