package com.ivor.openstream.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_media",
    indices = [Index(value = ["uri"], unique = true)]
)
data class LocalMediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = 0L,
    val mimeType: String? = null,
    val folderName: String? = null,
    val thumbnailUri: String? = null
)
