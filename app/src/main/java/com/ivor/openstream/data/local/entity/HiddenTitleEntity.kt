package com.ivor.openstream.data.local.entity

import androidx.room.Entity

/** A title the user marked "Not interested"; it's left out of Home rows. */
@Entity(tableName = "hidden_titles", primaryKeys = ["profileId", "mediaType", "tmdbId"])
data class HiddenTitleEntity(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val hiddenAt: Long = System.currentTimeMillis(),
    val profileId: Long = ProfileEntity.DEFAULT_ID
)
