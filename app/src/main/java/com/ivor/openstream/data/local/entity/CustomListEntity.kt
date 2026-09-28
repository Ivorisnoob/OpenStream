package com.ivor.openstream.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A list a profile made (beyond Watch Later), e.g. "Weekend movies". Items follow via listId. */
@Entity(tableName = "custom_lists", indices = [Index(value = ["profileId"])])
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val profileId: Long = ProfileEntity.DEFAULT_ID
)

/** A title in a custom list. Deleting a list deletes its items (see CustomListDao.deleteList). */
@Entity(
    tableName = "custom_list_items",
    primaryKeys = ["listId", "mediaType", "tmdbId"],
    indices = [Index(value = ["mediaType", "tmdbId"])]
)
data class CustomListItemEntity(
    val listId: Long,
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val voteAverage: Double,
    val addedAt: Long = System.currentTimeMillis()
)
