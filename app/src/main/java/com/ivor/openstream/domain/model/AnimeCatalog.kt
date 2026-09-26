package com.ivor.openstream.domain.model

/** Curated anime lists shown on Home. Every list is filtered to Japanese animation. */
enum class AnimeCatalog {
    /** What is trending across TMDB this week, narrowed to anime. */
    TRENDING,

    /** Series with an episode airing this week. */
    AIRING_NOW,

    /** Series that premiered in the last few months. */
    NEW_THIS_SEASON,

    TOP_RATED,
    POPULAR,
    MOVIES
}
