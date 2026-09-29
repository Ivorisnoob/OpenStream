package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ivor.openstream.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: Long): ProfileEntity?

    @Query("SELECT id FROM profiles WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Long?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profiles SET name = :name, avatar = :avatar, isKids = :isKids WHERE id = :id")
    suspend fun update(id: Long, name: String, avatar: String, isKids: Boolean)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM watch_later WHERE profileId = :id")
    suspend fun deleteWatchLater(id: Long)

    @Query("DELETE FROM watch_progress WHERE profileId = :id")
    suspend fun deleteProgress(id: Long)

    @Query("DELETE FROM hidden_titles WHERE profileId = :id")
    suspend fun deleteHidden(id: Long)

    @Query("DELETE FROM custom_list_items WHERE listId IN (SELECT id FROM custom_lists WHERE profileId = :id)")
    suspend fun deleteListItems(id: Long)

    @Query("DELETE FROM custom_lists WHERE profileId = :id")
    suspend fun deleteLists(id: Long)

    /** Deletes a profile and everything that belonged to it (downloads are device-wide and stay). */
    @Transaction
    suspend fun deleteWithData(id: Long) {
        deleteWatchLater(id)
        deleteProgress(id)
        deleteHidden(id)
        deleteListItems(id)
        deleteLists(id)
        deleteRow(id)
    }
}
