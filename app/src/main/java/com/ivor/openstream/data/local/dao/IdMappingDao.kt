package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ivor.openstream.data.local.entity.IdMappingEntity

@Dao
interface IdMappingDao {
    @Query("SELECT * FROM id_mappings WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun get(cacheKey: String): IdMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: IdMappingEntity)

    @Query("DELETE FROM id_mappings WHERE resolvedAt < :oldestAllowed")
    suspend fun deleteOlderThan(oldestAllowed: Long)
}
