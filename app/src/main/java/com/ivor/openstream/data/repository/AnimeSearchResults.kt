package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.model.AnimeDto

/** Merges TV and movie search results into one list, tagged with their media type and sorted. */
internal object AnimeSearchResults {
    fun prepare(
        tvShows: List<AnimeDto>,
        movies: List<AnimeDto>,
        sortBy: String,
        query: String = ""
    ): List<AnimeDto> {
        val anime = buildList {
            addAll(tvShows.map { it.copy(mediaType = "tv") })
            addAll(movies.map { it.copy(mediaType = "movie") })
        }
            .distinctBy { "${it.mediaType}:${it.id}" }

        return when (sortBy) {
            "popularity.desc" -> anime.sortedByDescending { it.popularity ?: 0.0 }
            "popularity.asc" -> anime.sortedBy { it.popularity ?: 0.0 }
            "vote_average.desc" -> anime.sortedByDescending { it.voteAverage ?: 0.0 }
            "vote_average.asc" -> anime.sortedBy { it.voteAverage ?: 0.0 }
            "first_air_date.desc", "primary_release_date.desc" ->
                anime.sortedByDescending(AnimeDto::date)
            "first_air_date.asc", "primary_release_date.asc" ->
                anime.sortedBy(AnimeDto::date)
            // Best match: exact titles, then titles starting with the query, then the rest by popularity.
            "relevance" -> anime.sortedWith(
                compareByDescending<AnimeDto> { matchScore(it.name, query) }.thenByDescending { it.popularity ?: 0.0 }
            )
            else -> anime
        }
    }

    private fun matchScore(title: String, query: String): Int {
        val t = title.trim().lowercase()
        val q = query.trim().lowercase()
        return when {
            q.isEmpty() -> 0
            t == q -> 3
            t.startsWith(q) -> 2
            q in t -> 1
            else -> 0
        }
    }
}
