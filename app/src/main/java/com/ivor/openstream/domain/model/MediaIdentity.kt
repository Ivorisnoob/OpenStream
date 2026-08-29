package com.ivor.openstream.domain.model

data class MediaIdentity(
    val tmdbId: Int,
    val tmdbType: String,
    val imdbId: String? = null,
    val anilistId: Int? = null,
    val malId: Int? = null,
    val title: String,
    val originalTitle: String? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val year: Int? = null
) {
    val cacheKey: String
        get() = "$tmdbType:$tmdbId:$season:$episode"
}
