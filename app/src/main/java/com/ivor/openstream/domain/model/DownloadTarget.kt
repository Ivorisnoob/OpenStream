package com.ivor.openstream.domain.model

/** One episode (or movie) the user wants offline, with enough context to find a source later. */
data class DownloadTarget(
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val showTitle: String,
    val episodeTitle: String?,
    val posterPath: String?,
    val stillPath: String?,
    val year: Int?,
    /** True when Smart Downloads owns this row and may auto-manage it. */
    val isSmart: Boolean = false
) {
    val id: String get() = downloadIdFor(mediaType, tmdbId, season, episode)

    fun toIdentity(): MediaIdentity = MediaIdentity(
        tmdbId = tmdbId,
        tmdbType = mediaType,
        title = showTitle,
        season = season,
        episode = episode,
        year = year
    )

    companion object {
        /** Stable per title and episode, so downloading the same episode twice replaces it. */
        fun downloadIdFor(mediaType: String, tmdbId: Int, season: Int, episode: Int): String =
            "dl_${mediaType}_${tmdbId}_${season}_$episode"
    }
}

/**
 * Download states stored in Room. The first five share values with Android's `DownloadManager`
 * status constants, so rows written by older builds keep their meaning.
 */
object DownloadStatus {
    const val QUEUED = 1
    const val RUNNING = 2
    const val PAUSED = 4
    const val COMPLETED = 8
    const val FAILED = 16

    /** Looking for a downloadable source before the transfer can start. */
    const val RESOLVING = 100

    fun isActive(status: Int): Boolean = status == QUEUED || status == RUNNING || status == RESOLVING
}
