package com.ivor.openstream.presentation.navigation

import android.content.Intent

/** A title to open from outside the app; [nonce] makes opening the same link twice count. */
data class DeepLinkRequest(val mediaType: String, val tmdbId: Int, val nonce: Long = System.nanoTime())

object DeepLinks {
    private val TMDB_TITLE = Regex("""themoviedb\.org/(movie|tv)/(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * A TMDB title from a link opened in the app ("https://www.themoviedb.org/tv/1399-game-of-thrones")
     * or text shared to it (OpenStream's own share text included). Null when there's no such link.
     */
    fun from(intent: Intent?): DeepLinkRequest? {
        intent ?: return null
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return null
        val match = TMDB_TITLE.find(text) ?: return null
        val (type, id) = match.destructured
        return id.toIntOrNull()?.let { DeepLinkRequest(type.lowercase(), it) }
    }

    /** True for a share that isn't a TMDB link, so the app can say why nothing opened. */
    fun isUnrecognisedShare(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND && from(intent) == null
}
