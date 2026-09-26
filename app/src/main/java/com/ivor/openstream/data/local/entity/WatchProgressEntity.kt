package com.ivor.openstream.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per episode (or movie) the user has started. Rows with `positionMs == 0` and
 * `completed == false` are "up next" markers queued when the previous episode finished.
 */
@Entity(
    tableName = "watch_progress",
    indices = [Index(value = ["mediaType", "tmdbId"])]
)
data class WatchProgressEntity(
    @PrimaryKey
    val id: String,
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val episodeTitle: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val stillPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAt: Long
) {
    companion object {
        fun idFor(mediaType: String, tmdbId: Int, season: Int, episode: Int): String =
            "$mediaType:$tmdbId:$season:$episode"
    }
}
