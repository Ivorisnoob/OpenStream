package com.ivor.openstream.data.settings

import com.ivor.openstream.domain.model.StreamingService

/**
 * The rows Home can show, by key, in their default order. The user's order and the rows they hid
 * live in [AppSettings.homeRowOrder] and [AppSettings.hiddenHomeRows].
 */
object HomeRows {
    const val CONTINUE_WATCHING = "continue_watching"
    const val TOP_MATCHES = "top-matches"
    const val TOP10 = "top10"
    const val NEW_EPISODES = "new-episodes"
    const val POPULAR_MOVIES = "popular-movies"
    const val POPULAR_SERIES = "popular-series"
    const val ANIME = "anime"
    const val TOP_RATED = "top-rated"
    const val ANIME_MOVIES = "anime-movies"

    /** "Popular on Netflix" and the like: this prefix plus [StreamingService.key]. */
    const val SERVICE_PREFIX = "service-"

    fun serviceKey(service: StreamingService) = SERVICE_PREFIX + service.key

    val DEFAULT_ORDER: List<String> = listOf(
        CONTINUE_WATCHING,
        TOP_MATCHES,
        TOP10,
        NEW_EPISODES,
        POPULAR_MOVIES,
        POPULAR_SERIES,
        ANIME,
        TOP_RATED,
        ANIME_MOVIES
    ) + StreamingService.entries.map(::serviceKey)

    /**
     * Every row in the user's [order]; rows the order does not mention (new ones after an update)
     * go after it, in their default order. Unknown keys are dropped.
     */
    fun ordered(order: List<String>): List<String> =
        order.filter { it in DEFAULT_ORDER }.distinct() + DEFAULT_ORDER.filterNot { it in order }

    /** [key] moved by [delta] places in the full order. */
    fun move(order: List<String>, key: String, delta: Int): List<String> {
        val rows = ordered(order).toMutableList()
        val from = rows.indexOf(key)
        if (from < 0) return rows
        val to = (from + delta).coerceIn(0, rows.lastIndex)
        rows.add(to, rows.removeAt(from))
        return rows
    }
}
