package com.ivor.openstream.presentation.settings

import java.text.Normalizer

/**
 * Matching for the Settings search. Pure Kotlin so it can be tested on the JVM.
 *
 * Text is compared folded: lower case and without accents ("Résumé" matches "resume"). Folding keeps
 * one character per character, so match positions map straight back onto the original text for
 * highlighting. Every word of the query has to appear somewhere in an entry (its title, summary,
 * section or option labels), in any order.
 */
internal object SettingsSearch {

    /** One searchable setting: what it is called and everything else it can be found by. */
    data class Candidate(
        val key: String,
        val title: String,
        val summary: String?,
        val section: String,
        val terms: List<String> = emptyList()
    )

    /** A candidate that matched, with how well; lower [rank] sorts first. */
    data class Match(val key: String, val rank: Int)

    /** The query's words, folded; empty when there is nothing to search for. */
    fun tokens(query: String): List<String> =
        fold(query).split(WHITESPACE).filter { it.isNotEmpty() }.distinct()

    /**
     * The candidates matching [query], best first: titles that start with the query, then titles
     * with a word starting with it, then titles containing it, then matches anywhere else. Ties
     * keep the order the settings appear in.
     */
    fun search(candidates: List<Candidate>, query: String): List<Match> {
        val words = tokens(query)
        if (words.isEmpty()) return emptyList()
        val whole = words.joinToString(" ")
        return candidates.mapIndexedNotNull { order, candidate ->
            val title = fold(candidate.title)
            val everything = buildString {
                append(title)
                candidate.summary?.let { append('\n').append(fold(it)) }
                append('\n').append(fold(candidate.section))
                candidate.terms.forEach { append('\n').append(fold(it)) }
            }
            if (!words.all { everything.contains(it) }) return@mapIndexedNotNull null
            val rank = when {
                title.startsWith(whole) -> 0
                startsWord(title, whole) -> 1
                words.all { title.contains(it) } -> 2
                else -> 3
            }
            Triple(candidate.key, rank, order)
        }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .map { (key, rank, _) -> Match(key, rank) }
    }

    /**
     * Where the query's words occur in [text], merged and in order, for highlighting. Empty when
     * nothing matches or the query is blank.
     */
    fun highlights(text: String, query: String): List<IntRange> {
        val words = tokens(query)
        if (words.isEmpty() || text.isEmpty()) return emptyList()
        val folded = fold(text)
        val ranges = words.flatMap { word ->
            generateSequence(folded.indexOf(word).takeIf { it >= 0 }) { previous ->
                folded.indexOf(word, previous + 1).takeIf { it >= 0 }
            }.map { start -> start until start + word.length }.toList()
        }.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (range in ranges) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    /** Lower case without accents, one character for each character of [text]. */
    fun fold(text: String): String = buildString(text.length) {
        for (char in text) append(foldChar(char))
    }

    private fun foldChar(char: Char): Char {
        if (char.code < 0x80) return char.lowercaseChar()
        // NFD splits an accented letter into its base letter and the accent; keep the base.
        val base = Normalizer.normalize(char.toString(), Normalizer.Form.NFD).firstOrNull() ?: char
        return base.lowercaseChar()
    }

    private fun startsWord(text: String, prefix: String): Boolean {
        var index = text.indexOf(prefix)
        while (index >= 0) {
            if (index == 0 || !text[index - 1].isLetterOrDigit()) return true
            index = text.indexOf(prefix, index + 1)
        }
        return false
    }

    private val WHITESPACE = Regex("\\s+")
}
