package com.ivor.openstream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val downloadId: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val mediaType: String,
    val season: Int = 1,
    val episode: Int = 1,
    val uri: String,
    val status: Int,
    val progress: Int,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val dateAdded: Long = System.currentTimeMillis(),
    val providerId: String? = null,
    val serverId: String? = null,
    val serverName: String? = null,
    val requestHeadersJson: String? = null,
    val resolvedAt: Long? = null
)
