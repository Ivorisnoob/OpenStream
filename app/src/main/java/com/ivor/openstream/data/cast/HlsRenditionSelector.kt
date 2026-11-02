package com.ivor.openstream.data.cast

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Which rendition of an HLS stream a Cast receiver should play: the video height picked on the
 * phone (0 for automatic, then at most [maxHeight] when that is set) and the audio rendition, by
 * its NAME and LANGUAGE. Travels in the query of the proxied master playlist URL, so it stays as
 * stateless as the rest of the proxy URL.
 */
data class HlsSelection(
    val height: Int = 0,
    val maxHeight: Int = 0,
    val audioLanguage: String? = null,
    val audioName: String? = null
) {
    val isEmpty: Boolean
        get() = height <= 0 && maxHeight <= 0 && audioLanguage.isNullOrBlank() && audioName.isNullOrBlank()

    fun toQuery(): String = listOfNotNull(
        height.takeIf { it > 0 }?.let { "h=$it" },
        maxHeight.takeIf { it > 0 }?.let { "max=$it" },
        audioLanguage?.takeIf { it.isNotBlank() }?.let { "al=${encode(it)}" },
        audioName?.takeIf { it.isNotBlank() }?.let { "an=${encode(it)}" }
    ).joinToString("&")

    companion object {
        /** Reads what [toQuery] wrote; null when the query says nothing about renditions. */
        fun fromQuery(query: String?): HlsSelection? {
            if (query.isNullOrBlank()) return null
            val values = query.split('&').mapNotNull { pair ->
                val equals = pair.indexOf('=')
                if (equals <= 0) null else pair.substring(0, equals) to decode(pair.substring(equals + 1))
            }.toMap()
            return HlsSelection(
                height = values["h"]?.toIntOrNull() ?: 0,
                maxHeight = values["max"]?.toIntOrNull() ?: 0,
                audioLanguage = values["al"],
                audioName = values["an"]
            ).takeUnless { it.isEmpty }
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String): String =
            runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
    }
}

/** An audio rendition (`#EXT-X-MEDIA:TYPE=AUDIO`) declared by a master playlist. */
data class HlsAudioRendition(val groupId: String?, val name: String?, val language: String?)

/**
 * Narrows an HLS master playlist to the renditions in a [HlsSelection], so a receiver that picks
 * its own variant and audio track (the Default Media Receiver does) plays what was picked on the
 * phone: only the variants of the chosen height (in every codec, so the receiver still finds one
 * it can decode) or those up to the cap, and in every audio group only the chosen rendition,
 * marked DEFAULT and AUTOSELECT. Anything that matches nothing is left as it was, so a stream
 * never loses all its variants or all its audio.
 */
object HlsRenditionSelector {

    private data class Variant(val infoLine: Int, val uriLine: Int, val height: Int)

    fun isMaster(text: String): Boolean = text.contains("#EXT-X-STREAM-INF")

    /** Distinct heights of the playlist's variants, tallest first. */
    fun heights(text: String): List<Int> =
        variants(text.lines()).map { it.height }.filter { it > 0 }.distinct().sortedDescending()

    /** The playlist's audio renditions, one per NAME and LANGUAGE, in playlist order. */
    fun audioRenditions(text: String): List<HlsAudioRendition> =
        audioLines(text.lines()).map { it.second }.distinctBy { it.name to it.language?.let(::iso3) }

    /** Which of [renditions] best matches [selection]'s audio: NAME and LANGUAGE, then NAME, then LANGUAGE. */
    fun pickAudio(renditions: List<HlsAudioRendition>, selection: HlsSelection): HlsAudioRendition? {
        val name = selection.audioName?.takeIf { it.isNotBlank() }
        val language = selection.audioLanguage?.takeIf { it.isNotBlank() }
        if (name == null && language == null) return null
        fun nameMatches(rendition: HlsAudioRendition) = name != null && rendition.name.equals(name, ignoreCase = true)
        fun languageMatches(rendition: HlsAudioRendition) = language != null && sameLanguage(rendition.language, language)
        return renditions.firstOrNull { nameMatches(it) && languageMatches(it) }
            ?: renditions.firstOrNull { nameMatches(it) }
            ?: renditions.firstOrNull { languageMatches(it) }
    }

    /** The height [selection] ends up playing among [heights], or 0 for automatic. */
    fun resolveHeight(heights: List<Int>, selection: HlsSelection): Int {
        if (selection.height <= 0 || heights.isEmpty()) return 0
        return heights.filter { it <= selection.height }.maxOrNull() ?: heights.min()
    }

    fun select(text: String, selection: HlsSelection): String {
        if (selection.isEmpty || !isMaster(text)) return text
        val lines = text.lines()
        val dropped = mutableSetOf<Int>()
        val edited = mutableMapOf<Int, String>()

        // Video: the variants of the chosen height, or every variant under the cap.
        val variants = variants(lines)
        val sized = variants.filter { it.height > 0 }
        if (sized.isNotEmpty()) {
            val keep: List<Variant> = when {
                selection.height > 0 -> {
                    val target = resolveHeight(sized.map { it.height }, selection)
                    sized.filter { it.height == target }
                }
                selection.maxHeight > 0 -> {
                    val lowest = sized.minOf { it.height }
                    sized.filter { it.height <= selection.maxHeight }.ifEmpty { sized.filter { it.height == lowest } } +
                        variants.filter { it.height <= 0 }
                }
                else -> variants
            }
            (variants - keep.toSet()).forEach { variant ->
                dropped += variant.infoLine
                dropped += variant.uriLine
            }
        }

        // Audio: in each group with a match, only the chosen rendition, as the default.
        audioLines(lines).groupBy { it.second.groupId }.forEach { (_, group) ->
            val chosen = pickAudio(group.map { it.second }, selection) ?: return@forEach
            val chosenLine = group.first { it.second == chosen }.first
            group.forEach { (index, _) -> if (index != chosenLine) dropped += index }
            edited[chosenLine] = markDefault(lines[chosenLine])
        }

        return lines.withIndex()
            .filter { it.index !in dropped }
            .joinToString("\n") { edited[it.index] ?: it.value }
    }

    private fun variants(lines: List<String>): List<Variant> {
        val result = mutableListOf<Variant>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val uri = (index + 1 until lines.size).firstOrNull { next ->
                    val candidate = lines[next].trim()
                    candidate.isNotEmpty() && !candidate.startsWith("#")
                }
                if (uri != null) {
                    val attributes = attributes(line)
                    val height = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull() ?: 0
                    result += Variant(index, uri, height)
                    index = uri
                }
            }
            index++
        }
        return result
    }

    private fun audioLines(lines: List<String>): List<Pair<Int, HlsAudioRendition>> =
        lines.withIndex().mapNotNull { (index, raw) ->
            val line = raw.trim()
            if (!line.startsWith("#EXT-X-MEDIA:")) return@mapNotNull null
            val attributes = attributes(line)
            if (attributes["TYPE"] != "AUDIO") return@mapNotNull null
            index to HlsAudioRendition(attributes["GROUP-ID"], attributes["NAME"], attributes["LANGUAGE"])
        }

    private fun attributes(line: String): Map<String, String> =
        ATTRIBUTE.findAll(line.substringAfter(':')).associate { match ->
            match.groupValues[1] to match.groupValues[2].removeSurrounding("\"")
        }

    private fun markDefault(line: String): String {
        var result = line
        for (name in listOf("DEFAULT", "AUTOSELECT")) {
            val existing = Regex("(?<=[:,])$name=(YES|NO)")
            result = if (existing.containsMatchIn(result)) {
                existing.replace(result, "$name=YES")
            } else {
                "$result,$name=YES"
            }
        }
        return result
    }

    private fun sameLanguage(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return iso3(a) == iso3(b)
    }

    private fun iso3(code: String): String =
        runCatching { Locale.forLanguageTag(code.replace('_', '-')).isO3Language }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: code.lowercase()

    private val ATTRIBUTE = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
}
