package com.ivor.openstream.domain.model

data class WatchProgress(
    val tmdbId: Int,
    val mediaType: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val episodeTitle: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val stillPath: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isMovie: Boolean get() = mediaType == "movie"

    /** 0..1, or 0 when the duration is not known yet. */
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Queued by the player when the previous episode finished; not started yet. */
    val isUpNext: Boolean get() = !completed && positionMs <= 0L

    /** Where playback should start. Near-finished entries restart from the top. */
    val resumePositionMs: Long
        get() = if (completed || fraction >= COMPLETION_FRACTION) 0L else positionMs

    companion object {
        /** Past this point an episode counts as watched; credits rarely need to be seen. */
        const val COMPLETION_FRACTION = 0.9f

        /** Shorter watches are treated as accidental taps and not saved. */
        const val MIN_SAVED_POSITION_MS = 15_000L
    }
}
