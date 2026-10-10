package com.ivor.openstream.domain.matching

import kotlin.math.sqrt

/**
 * Content + local-collaborative taste matching (pure, fully unit-tested).
 *
 * Every candidate is compared against the profile's liked titles with
 * nearest-neighbour genre Jaccard similarity, tempered by dislikes, language
 * affinity and list co-occurrence ("kept with things you liked").
 *
 * Weights are tuned so that one strong genre twin outranks a pile of weak
 * signals, while a disliked twin vetoes: genre 0.55, lists 0.25, language
 * 0.15, quality tiebreak 0.05.
 */
object TitleMatcher {

    data class RatedTitle(
        val key: Pair<String, Int>,
        val name: String,
        val genres: Set<Int>,
        val language: String?,
        val rating: Int
    )

    data class Candidate(
        val key: Pair<String, Int>,
        val genres: Set<Int>,
        val language: String?,
        val voteAverage: Double?
    )

    data class Match(
        val key: Pair<String, Int>,
        /** 0..1 confidence. */
        val score: Double,
        /** 0..100 for display. */
        val percent: Int,
        /** The liked title most responsible, for "because you liked X". */
        val becauseKey: Pair<String, Int>?,
        val becauseName: String?
    )

    /**
     * @param candidates titles that may be recommended.
     * @param ratings the profile's explicit likes/dislikes (with cached features).
     * @param lists each custom list plus Watch Later, as sets of title keys.
     * @param excluded already watched/completed, saved, hidden or disliked keys.
     * @param minScore floor for inclusion.
     * @param limit max results, best first (ties broken deterministically by key).
     */
    fun match(
        candidates: List<Candidate>,
        ratings: List<RatedTitle>,
        lists: List<Set<Pair<String, Int>>>,
        excluded: Set<Pair<String, Int>> = emptySet(),
        minScore: Double = 0.40,
        limit: Int = 20
    ): List<Match> {
        val likes = ratings.filter { it.rating > 0 }
        if (likes.isEmpty()) return emptyList()
        val dislikes = ratings.filter { it.rating < 0 }
        val topLanguage = likes.mapNotNull { it.language?.lowercase() }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.takeIf { it.value >= 2 }?.key

        // Lists that contain at least one liked title lend their other members weight.
        val affinityLists = lists.filter { list -> list.any { key -> likes.any { it.key == key } } }

        return candidates
            .asSequence()
            .filter { it.key !in excluded }
            .mapNotNull { candidate ->
                score(candidate, likes, dislikes, topLanguage, affinityLists)?.takeIf { it.score >= minScore }
            }
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.key.first }.thenBy { it.key.second })
            .take(limit)
            .toList()
    }

    private fun score(
        candidate: Candidate,
        likes: List<RatedTitle>,
        dislikes: List<RatedTitle>,
        topLanguage: String?,
        affinityLists: List<Set<Pair<String, Int>>>
    ): Match? {
        var bestGenre = 0.0
        var because: RatedTitle? = null
        for (liked in likes) {
            val j = jaccard(candidate.genres, liked.genres)
            if (j > bestGenre) {
                bestGenre = j
                because = liked
            }
        }
        if (bestGenre <= 0.0) return null

        var worstDislike = 0.0
        for (disliked in dislikes) {
            val j = jaccard(candidate.genres, disliked.genres)
            if (j > worstDislike) worstDislike = j
        }
        // A disliked near-twin vetoes outright: looking almost exactly like
        // something you disliked buries the candidate no matter what else fits.
        if (worstDislike >= 0.85 && worstDislike >= bestGenre) return null
        val genrePart = (bestGenre - 0.8 * worstDislike).coerceAtLeast(0.0)

        val languagePart =
            if (topLanguage != null && candidate.language?.lowercase() == topLanguage) 1.0 else 0.0

        val listPart =
            if (affinityLists.any { candidate.key in it }) 1.0 else 0.0

        val qualityPart = candidate.voteAverage
            ?.takeIf { it > 0.0 }
            ?.let { (it / 10.0).coerceIn(0.0, 1.0) }
            ?: 0.5

        val raw = 0.55 * genrePart + 0.25 * listPart + 0.15 * languagePart + 0.05 * qualityPart
        val score = raw.coerceIn(0.0, 1.0)
        return Match(
            key = candidate.key,
            score = score,
            percent = (score * 100).toInt().coerceIn(0, 99),
            becauseKey = because?.key,
            becauseName = because?.name?.takeIf { it.isNotBlank() }
        )
    }

    /** Jaccard similarity of two genre sets; empty candidate genres never match. */
    internal fun jaccard(a: Set<Int>, b: Set<Int>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        if (intersection == 0.0) return 0.0
        val union = (a.size + b.size - intersection).coerceAtLeast(1.0)
        // Small sets match harder: a 1-genre twin of a 1-genre like is a bullseye.
        val sizeBonus = 1.0 / sqrt(minOf(a.size, b.size).toDouble())
        return (intersection / union * (0.75 + 0.25 * sizeBonus)).coerceIn(0.0, 1.0)
    }
}
