package com.ivor.openanime.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val downloadId: Long,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val mediaType: String,
    val uri: String,
    val status: Int,
    val progress: Int,
    val dateAdded: Long = System.currentTimeMillis()
)
