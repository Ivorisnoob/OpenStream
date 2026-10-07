package com.ivor.openstream.presentation.components

import com.ivor.openstream.domain.model.WatchProgress

/**
 * The line over the artwork on a Continue Watching card: what is being watched and how much is
 * left. Shared with the home-screen widget so the two cannot drift apart.
 */
fun WatchProgress.badge(): String {
    val remaining = ((durationMs - positionMs) / 60_000L).coerceAtLeast(1L)
    return when {
        isUpNext && !isMovie -> "Up next · S$season E$episode"
        isMovie -> "${remaining}m left"
        else -> "S$season E$episode · ${remaining}m left"
    }
}