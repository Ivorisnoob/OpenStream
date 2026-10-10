package com.ivor.openstream.data.local.entity

import androidx.room.Entity

/**
 * A "remind me" request: notify once when the title releases (movie) or when it
 * (or a specific episode) has aired. Checked by the daily episode alarm.
 */
@Entity(tableName = "reminders", primaryKeys = ["profileId", "mediaType", "tmdbId"])
data class ReminderEntity(
    val profileId: Long,
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    /** Null = the title itself; set = a specific episode to wait for. */
    val season: Int? = null,
    val episode: Int? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** Explicit taste signal: +1 like, -1 dislike. Features are cached so matching works offline. */
@Entity(tableName = "title_ratings", primaryKeys = ["profileId", "mediaType", "tmdbId"])
data class TitleRatingEntity(
    val profileId: Long,
    val mediaType: String,
    val tmdbId: Int,
    val rating: Int,
    val title: String = "",
    /** Comma-separated TMDB genre ids, e.g. "18,35". */
    val genreIds: String = "",
    val language: String? = null,
    val voteAverage: Double? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val LIKE = 1
        const val DISLIKE = -1
    }

    val genreSet: Set<Int> get() =
        genreIds.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
}
