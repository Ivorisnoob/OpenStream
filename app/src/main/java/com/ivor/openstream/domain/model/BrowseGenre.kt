package com.ivor.openstream.domain.model

/**
 * Genres offered on the Search page. TMDB numbers movie and TV genres differently, and some genres
 * exist on only one side (a null id skips that side).
 */
enum class BrowseGenre(
    val label: String,
    val movieGenreId: Int?,
    val tvGenreId: Int?,
    /** Anime: Japanese animation rather than a TMDB genre. */
    val isAnime: Boolean = false
) {
    ANIME("Anime", 16, 16, isAnime = true),
    ACTION("Action", 28, 10759),
    COMEDY("Comedy", 35, 35),
    DRAMA("Drama", 18, 18),
    SCI_FI("Sci-fi & fantasy", 878, 10765),
    ROMANCE("Romance", 10749, null),
    HORROR("Horror", 27, null),
    MYSTERY("Mystery", 9648, 9648),
    CRIME("Crime", 80, 80),
    THRILLER("Thriller", 53, null),
    ANIMATION("Animation", 16, 16),
    FAMILY("Family", 10751, 10751),
    DOCUMENTARY("Documentary", 99, 99)
}
