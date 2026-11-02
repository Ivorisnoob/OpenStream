package com.ivor.openstream.domain.model

/**
 * Subscription services that get a "Popular on ..." row on Home.
 *
 * [providerIds] are TMDB (JustWatch) watch provider ids. Some services are listed under a
 * different id depending on the country (Prime Video is 9 in some regions and 119 in others), so
 * every id is asked for at once; a service that isn't offered in the region simply has no row.
 * [displayName] is the brand name and is not translated.
 */
enum class StreamingService(val key: String, val displayName: String, val providerIds: List<Int>) {
    NETFLIX("netflix", "Netflix", listOf(8)),
    PRIME_VIDEO("prime-video", "Prime Video", listOf(9, 119)),
    DISNEY_PLUS("disney-plus", "Disney+", listOf(337)),
    APPLE_TV("apple-tv", "Apple TV", listOf(350)),
    HBO_MAX("hbo-max", "HBO Max", listOf(1899, 384)),
    CRUNCHYROLL("crunchyroll", "Crunchyroll", listOf(283))
}
