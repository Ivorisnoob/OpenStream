package com.ivor.openstream.data.local.entity

import androidx.room.Entity

/** A title a profile saved for later; [id] is the TMDB id. */
@Entity(tableName = "watch_later", primaryKeys = ["profileId", "id"])
data class WatchLaterEntity(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val mediaType: String,
    val voteAverage: Double,
    val dateAdded: Long = System.currentTimeMillis(),
    val profileId: Long = ProfileEntity.DEFAULT_ID
)
