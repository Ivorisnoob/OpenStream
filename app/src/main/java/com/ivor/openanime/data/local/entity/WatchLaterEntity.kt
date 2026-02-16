package com.ivor.openanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val posterPath: String?,
    val mediaType: String,
    val voteAverage: Double,
    val dateAdded: Long = System.currentTimeMillis()
)
