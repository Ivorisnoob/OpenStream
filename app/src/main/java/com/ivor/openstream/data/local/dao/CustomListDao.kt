package com.ivor.openstream.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ivor.openstream.data.local.entity.CustomListEntity
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import kotlinx.coroutines.flow.Flow

/** A list with its size and the posters of its newest titles, for list cards. */
data class CustomListSummary(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    val itemCount: Int,
    /** Up to four poster paths, newest first, joined with '|'. */
    val posters: String?
)

@Dao
interface CustomListDao {
    @Query(
        """
        SELECT l.id, l.name, l.updatedAt,
            (SELECT COUNT(*) FROM custom_list_items i WHERE i.listId = l.id) AS itemCount,
            (SELECT GROUP_CONCAT(posterPath, '|') FROM (
                SELECT posterPath FROM custom_list_items i2
                WHERE i2.listId = l.id AND i2.posterPath IS NOT NULL
                ORDER BY i2.addedAt DESC LIMIT 4
            )) AS posters
        FROM custom_lists l
        ORDER BY l.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<CustomListSummary>>

    @Query("SELECT * FROM custom_lists WHERE id = :listId")
    fun observeList(listId: Long): Flow<CustomListEntity?>

    @Query("SELECT * FROM custom_lists ORDER BY updatedAt DESC")
    suspend fun allLists(): List<CustomListEntity>

    @Query("SELECT * FROM custom_list_items WHERE listId = :listId ORDER BY addedAt DESC")
    fun observeItems(listId: Long): Flow<List<CustomListItemEntity>>

    @Query("SELECT * FROM custom_list_items")
    suspend fun allItems(): List<CustomListItemEntity>

    /** Ids of the lists that contain a title. */
    @Query("SELECT listId FROM custom_list_items WHERE mediaType = :mediaType AND tmdbId = :tmdbId")
    fun observeListIdsFor(mediaType: String, tmdbId: Int): Flow<List<Long>>

    @Insert
    suspend fun insertList(list: CustomListEntity): Long

    @Query("UPDATE custom_lists SET name = :name, updatedAt = :now WHERE id = :listId")
    suspend fun rename(listId: Long, name: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE custom_lists SET updatedAt = :now WHERE id = :listId")
    suspend fun touch(listId: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM custom_lists WHERE id = :listId")
    suspend fun deleteListRow(listId: Long)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId")
    suspend fun deleteItemsOf(listId: Long)

    @Transaction
    suspend fun deleteList(listId: Long) {
        deleteItemsOf(listId)
        deleteListRow(listId)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: CustomListItemEntity)

    @Transaction
    suspend fun addItem(item: CustomListItemEntity) {
        insertItem(item)
        touch(item.listId)
    }

    @Query("DELETE FROM custom_list_items WHERE listId = :listId AND mediaType = :mediaType AND tmdbId = :tmdbId")
    suspend fun removeItem(listId: Long, mediaType: String, tmdbId: Int)

    @Query("SELECT id FROM custom_lists WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): Long?
}
