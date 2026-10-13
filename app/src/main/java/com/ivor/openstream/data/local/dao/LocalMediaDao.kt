package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivor.openstream.data.local.entity.LocalMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMediaDao {
    @Query("SELECT * FROM local_media ORDER BY dateAdded DESC")
    fun getAllLocalMedia(): Flow<List<LocalMediaEntity>>

    @Query("SELECT * FROM local_media WHERE id = :id LIMIT 1")
    suspend fun getLocalMediaById(id: Long): LocalMediaEntity?

    @Query("SELECT * FROM local_media WHERE uri = :uri LIMIT 1")
    suspend fun getLocalMediaByUri(uri: String): LocalMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocalMediaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<LocalMediaEntity>): List<Long>

    @Update
    suspend fun update(entity: LocalMediaEntity)

    @Query("DELETE FROM local_media WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_media WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM local_media")
    suspend fun deleteAll()
}
