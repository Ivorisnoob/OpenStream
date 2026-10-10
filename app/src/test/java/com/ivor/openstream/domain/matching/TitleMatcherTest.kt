package com.ivor.openstream.domain.matching

import com.ivor.openstream.domain.matching.TitleMatcher.Candidate
import com.ivor.openstream.domain.matching.TitleMatcher.RatedTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatcherTest {

    private fun liked(
        id: Int,
        genres: Set<Int>,
        name: String = "Liked $id",
        language: String? = "en",
        type: String = "tv"
    ) = RatedTitle(type to id, name, genres, language, 1)

    private fun disliked(id: Int, genres: Set<Int>, name: String = "Disliked $id") =
        RatedTitle("tv" to id, name, genres, null, -1)

    private fun cand(
        id: Int,
        genres: Set<Int>,
        language: String? = "en",
        vote: Double? = 7.5,
        type: String = "tv"
    ) = Candidate(type to id, genres, language, vote)

    @Test
    fun emptyLikesMatchNothing() {
        val out = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18))),
            ratings = emptyList(),
            lists = emptyList()
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun genreTwinScoresHighAndNamesBecause() {
        val out = TitleMatcher.match(
            candidates = listOf(
                cand(1, setOf(18, 35)),
                cand(2, setOf(18)),
                cand(3, setOf(27))
            ),
            ratings = listOf(liked(10, setOf(18, 35), name = "Drama Club")),
            lists = emptyList(),
            minScore = 0.0
        )
        // Exact twin first, partial second, unrelated ({27}) excluded: no shared genres.
        assertEquals(listOf("tv" to 1, "tv" to 2), out.map { it.key })
        assertEquals("Drama Club", out[0].becauseName)
        assertEquals("tv" to 10, out[0].becauseKey)
        assertTrue(out[0].percent in 1..99)
    }

    @Test
    fun emptyCandidateGenresNeverMatch() {
        val out = TitleMatcher.match(
            candidates = listOf(cand(1, emptySet())),
            ratings = listOf(liked(10, setOf(18))),
            lists = emptyList(),
            minScore = 0.0
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun dislikedTwinIsVetoed() {
        val out = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(27, 9648))),
            ratings = listOf(
                liked(10, setOf(27, 9648)),
                disliked(11, setOf(27, 9648))
            ),
            lists = emptyList(),
            minScore = 0.0
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun partialDislikeOverlapOnlyDampens() {
        val withoutDislike = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18, 35))),
            ratings = listOf(liked(10, setOf(18, 35))),
            lists = emptyList(),
            minScore = 0.0
        ).single().score
        val withDislike = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18, 35))),
            ratings = listOf(
                liked(10, setOf(18, 35)),
                disliked(11, setOf(35, 10749))
            ),
            lists = emptyList(),
            minScore = 0.0
        ).single().score
        assertTrue(withDislike < withoutDislike)
        assertTrue(withDislike > 0.0)
    }

    @Test
    fun excludedKeysNeverAppear() {
        val out = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18))),
            ratings = listOf(liked(10, setOf(18))),
            lists = emptyList(),
            excluded = setOf("tv" to 1),
            minScore = 0.0
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun listCoOccurrenceBoosts() {
        val base = listOf(liked(10, setOf(18)))
        val lone = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18))),
            ratings = base,
            lists = emptyList(),
            minScore = 0.0
        ).single().score
        val listed = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18))),
            ratings = base,
            lists = listOf(setOf("tv" to 10, "tv" to 1)),
            minScore = 0.0
        ).single().score
        assertTrue(listed > lone)
    }

    @Test
    fun unrelatedListsDoNotBoost() {
        val base = listOf(liked(10, setOf(18)))
        val lone = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18))),
            ratings = base,
            lists = emptyList(),
            minScore = 0.0
        ).single().score
        val other = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18))),
            ratings = base,
            lists = listOf(setOf("tv" to 99, "tv" to 1)),
            minScore = 0.0
        ).single().score
        assertEquals(lone, other, 1e-9)
    }

    @Test
    fun sharedTopLanguageBoosts() {
        val ratings = listOf(
            liked(10, setOf(18), language = "ja"),
            liked(11, setOf(35), language = "ja")
        )
        val same = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18), language = "ja")),
            ratings = ratings,
            lists = emptyList(),
            minScore = 0.0
        ).single().score
        val other = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18), language = "en")),
            ratings = ratings,
            lists = emptyList(),
            minScore = 0.0
        ).single().score
        assertTrue(same > other)
    }

    @Test
    fun singleLikeNeedsNoLanguageConsensus() {
        // With one like there is no "top language": same-language gets no bonus,
        // so both candidates score identically on language.
        val ratings = listOf(liked(10, setOf(18), language = "ja"))
        val a = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18), language = "ja")),
            ratings = ratings, lists = emptyList(), minScore = 0.0
        ).single().score
        val b = TitleMatcher.match(
            candidates = listOf(cand(2, setOf(18), language = "fr")),
            ratings = ratings, lists = emptyList(), minScore = 0.0
        ).single().score
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun minScoreFiltersWeakMatches() {
        val out = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(99))),
            ratings = listOf(liked(10, setOf(18))),
            lists = emptyList(),
            minScore = 0.40
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun limitAndDeterministicOrder() {
        val cands = (1..30).map { cand(it, setOf(18)) }
        val out = TitleMatcher.match(
            candidates = cands,
            ratings = listOf(liked(10, setOf(18))),
            lists = emptyList(),
            minScore = 0.0,
            limit = 10
        )
        assertEquals(10, out.size)
        // Identical scores order by key: deterministic across runs.
        val again = TitleMatcher.match(
            candidates = cands.shuffled(),
            ratings = listOf(liked(10, setOf(18))),
            lists = emptyList(),
            minScore = 0.0,
            limit = 10
        )
        assertEquals(out.map { it.key }, again.map { it.key })
    }

    @Test
    fun jaccardProperties() {
        assertEquals(0.0, TitleMatcher.jaccard(emptySet(), setOf(1)), 1e-9)
        assertEquals(0.0, TitleMatcher.jaccard(setOf(1), setOf(2)), 1e-9)
        assertEquals(1.0, TitleMatcher.jaccard(setOf(5), setOf(5)), 1e-9)
        // Identical small sets outscore identical large ones (specificity).
        assertTrue(
            TitleMatcher.jaccard(setOf(5), setOf(5)) >
                TitleMatcher.jaccard(setOf(1, 2, 3, 4), setOf(1, 2, 3, 4))
        )
        // Partial overlap lands strictly between.
        val partial = TitleMatcher.jaccard(setOf(1, 2), setOf(2, 3))
        assertTrue(partial in 0.0..1.0)
    }

    @Test
    fun percentNeverHits100() {
        val out = TitleMatcher.match(
            candidates = listOf(cand(1, setOf(18), vote = 10.0)),
            ratings = listOf(liked(10, setOf(18))),
            lists = listOf(setOf("tv" to 10, "tv" to 1)),
            minScore = 0.0
        )
        assertTrue(out.single().percent <= 99)
    }
}
