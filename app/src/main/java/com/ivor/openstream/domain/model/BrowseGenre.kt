package com.ivor.openstream.domain.model

import androidx.annotation.StringRes
import com.ivor.openstream.R

/**
 * Genres offered on the Search page. TMDB numbers movie and TV genres differently, and some genres
 * exist on only one side (a null id skips that side).
 */
enum class BrowseGenre(
    val label: String,
    @StringRes val labelRes: Int,
    val movieGenreId: Int?,
    val tvGenreId: Int?,
    /** Anime: Japanese animation rather than a TMDB genre. */
    val isAnime: Boolean = false
) {
    ANIME("Anime", R.string.genre_anime, 16, 16, isAnime = true),
    ACTION("Action", R.string.genre_action, 28, 10759),
    COMEDY("Comedy", R.string.genre_comedy, 35, 35),
    DRAMA("Drama", R.string.genre_drama, 18, 18),
    SCI_FI("Sci-fi & fantasy", R.string.genre_scifi_fantasy, 878, 10765),
    ROMANCE("Romance", R.string.genre_romance, 10749, null),
    HORROR("Horror", R.string.genre_horror, 27, null),
    MYSTERY("Mystery", R.string.genre_mystery, 9648, 9648),
    CRIME("Crime", R.string.genre_crime, 80, 80),
    THRILLER("Thriller", R.string.genre_thriller, 53, null),
    ANIMATION("Animation", R.string.genre_animation, 16, 16),
    FAMILY("Family", R.string.genre_family, 10751, 10751),
    DOCUMENTARY("Documentary", R.string.genre_documentary, 99, 99)
}
