package com.ivor.openstream.data.streaming.providers

import java.util.Locale

/**
 * Short, consistent names for Stremio streams. Add-ons describe streams however they like: host
 * tags and emoji ("[PixelDrain] [6.3 GB] …"), scene file names
 * ("Breaking.Bad.S01.E01.1080p.BluRay.Hindi.DD2.0-English.5.1.ESub.x264-HDHub4u.Tv.mkv"), or blocks
 * of emoji-led lines. This pulls out what helps pick a stream, in a fixed order:
 * "1080p · BluRay · HEVC 10-bit · Hindi + English · 1.98 GB". When a stream says too little for
 * that, a cleaned-up remainder of its own text leads ("kisskh", "Source 2 · Dubbed").
 */
internal object StremioStreamLabels {

    /** What one stream's text says, already split into lines. */
    class Parts(
        val nameLines: List<String>,
        val detailLines: List<String>,
        val filename: String?
    ) {
        private val firstDetail = detailLines.firstOrNull().orEmpty()

        val size: String? = SIZE.find(firstDetail)?.value?.let { raw ->
            raw.replace(Regex("\\s+"), "").replace(Regex("(?i)([GM]B)$")) { " " + it.value.uppercase(Locale.ROOT) }
        }

        /**
         * The descriptive lines: the first without its leading host/size tags, then the rest minus
         * host footers ("pixeldrain | HdHub"). Mirrors of one release on different hosts share them.
         */
        val body: List<String> = listOf(firstDetail.replace(LEADING_TAGS, "").trim()) +
            detailLines.drop(1).filterNot { " | " in it }

        /** Groups mirrors: same descriptive text and size. */
        val releaseKey: String =
            (filename ?: body.joinToString(" ")).lowercase(Locale.ROOT).filter { it.isLetterOrDigit() } +
                "|" + size.orEmpty()
    }

    fun label(parts: Parts, title: String, fallback: String): String {
        val text = (parts.nameLines + parts.body + listOfNotNull(parts.filename)).joinToString(" ")
        val facts = facts(text).toMutableList()
        if (facts.size < 2) {
            val hasLanguage = tokens(text).any { it in LANGUAGES }
            leadingText(parts, title, hasLanguage)?.let { facts.add(0, it) }
        }
        parts.size?.let { facts += it }
        return facts.joinToString(" · ").ifEmpty { fallback }
    }

    private fun facts(text: String): List<String> {
        val low = text.lowercase(Locale.ROOT)
        val words = tokens(text)
        val facts = mutableListOf<String>()

        val height = Regex("(?:^|\\D)(2160|1440|1080|720|480)p").find(low)?.groupValues?.get(1)
        when {
            height == "2160" -> facts += "4K"
            height != null -> facts += "${height}p"
            Regex("\\b(4k|uhd)\\b").containsMatchIn(low) -> facts += "4K"
        }
        when {
            Regex("\\b(dolby.?vision|dovi|dv)\\b").containsMatchIn(low) -> facts += "Dolby Vision"
            Regex("\\bhdr(10\\+?)?\\b").containsMatchIn(low) -> facts += "HDR"
        }
        if ("imax" in words) facts += "IMAX"
        when {
            "remux" in words -> facts += "Remux"
            Regex("blu-?ray|bdrip|brrip").containsMatchIn(low) -> facts += "BluRay"
            Regex("web-?dl").containsMatchIn(low) -> facts += "WEB-DL"
            "webrip" in words -> facts += "WEBRip"
            "hdtv" in words -> facts += "HDTV"
        }
        val tenBit = Regex("10.?bit").containsMatchIn(low)
        when {
            Regex("hevc|x265|h\\.?265|x2645").containsMatchIn(low) -> facts += if (tenBit) "HEVC 10-bit" else "HEVC"
            "av1" in words -> facts += "AV1"
            Regex("x264|h\\.?264|\\bavc\\b").containsMatchIn(low) -> facts += "H.264"
        }
        if (Regex("60\\s*fps").containsMatchIn(low)) facts += "60fps"

        val languages = words.mapNotNull { LANGUAGES[it] }.distinct()
        when {
            languages.isNotEmpty() -> facts += languages.take(3).joinToString(" + ")
            Regex("dual.?(audio|áudio)|\\bdual\\b").containsMatchIn(low) -> facts += "Dual audio"
            Regex("\\bmulti\\b").containsMatchIn(low) -> facts += "Multi audio"
        }
        when {
            Regex("dublado|dubbed|\\bdub\\b").containsMatchIn(low) -> facts += "Dubbed"
            Regex("legendado|subbed|\\bsub\\b").containsMatchIn(low) -> facts += "Subtitled"
        }
        return facts
    }

    /**
     * The add-on's own words when its facts are thin: a sub-source from the name ("kisskh"), else
     * the first detail line, minus the film's title, years, episode markers, emoji and words the
     * facts already say.
     */
    private fun leadingText(parts: Parts, title: String, hasLanguage: Boolean): String? {
        val titleWords = tokens(title).filterNot { it in STOP_WORDS }.toSet()
        fun tidy(raw: String): String {
            var text = clean(raw.replace(LEADING_TAGS, ""))
            text = text.replace(Regex("(?i)\\b(?:fonte|source|servidor|server|link)\\b\\D{0,20}?0*(\\d{1,2})\\b"), "Source $1")
            text = text.replace(Regex("(?i)\\b[ST]\\d{1,2}\\s*EP?\\s*\\d{1,3}\\b|\\bseason\\s*\\d+\\b|\\(\\d{4}\\)|\\b(19|20)\\d{2}\\b"), "")
            return text.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .filterNot { word -> tokens(word).let { it.isNotEmpty() && it.all { token -> token in titleWords } } }
                .filterNot { word -> hasLanguage && tokens(word).any { it in LANGUAGES } }
                .filterNot { DUB_SUB_WORDS.matches(it) }
                .joinToString(" ")
                .trim(' ', '-', '|', '•', '·', ':')
        }
        val nameExtra = parts.nameLines.drop(1).joinToString(" ")
            .replace(Regex("(?i)\\b(2160|1440|1080|720|480)p\\b|\\b4k\\b"), "")
        return listOf(nameExtra, parts.detailLines.firstOrNull().orEmpty())
            .map(::tidy)
            .firstOrNull { it.isNotEmpty() }
            ?.take(MAX_LEADING_LENGTH)
    }

    /** Drops emoji, flags and other symbols, then tidies spacing. */
    fun clean(text: String): String {
        val kept = StringBuilder()
        text.codePoints().forEach { codePoint ->
            val type = Character.getType(codePoint)
            val drop = type == Character.OTHER_SYMBOL.toInt() ||
                type == Character.MODIFIER_SYMBOL.toInt() ||
                type == Character.PRIVATE_USE.toInt() ||
                codePoint == 0xFE0F || codePoint == 0x200D
            if (!drop) kept.appendCodePoint(codePoint)
        }
        return kept.toString().replace(Regex("\\s+"), " ").trim(' ', '-', '|', '•', '·', ':')
    }

    private fun tokens(text: String): List<String> =
        text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private val SIZE = Regex("\\d+(?:[.,]\\d+)?\\s*[GM]B\\b", RegexOption.IGNORE_CASE)

    /** Bracketed host and size tags add-ons put before the release name. */
    val LEADING_TAGS = Regex("^(\\s*\\[[^\\]]*]\\s*)+")

    private val STOP_WORDS = setOf("the", "a", "an", "of", "and")
    private val DUB_SUB_WORDS = Regex("(?i)dublado|legendado|dubbed|subbed|subtitled|dual|audio|áudio")
    private const val MAX_LEADING_LENGTH = 40

    /** Words and scene abbreviations for audio languages, as they appear in release names. */
    private val LANGUAGES = mapOf(
        "hindi" to "Hindi", "hin" to "Hindi", "english" to "English", "eng" to "English",
        "tamil" to "Tamil", "tam" to "Tamil", "telugu" to "Telugu", "tel" to "Telugu",
        "malayalam" to "Malayalam", "kannada" to "Kannada", "bengali" to "Bengali", "punjabi" to "Punjabi",
        "marathi" to "Marathi", "korean" to "Korean", "kor" to "Korean", "japanese" to "Japanese",
        "jpn" to "Japanese", "jap" to "Japanese", "chinese" to "Chinese", "mandarin" to "Chinese",
        "spanish" to "Spanish", "spa" to "Spanish", "french" to "French", "german" to "German",
        "italian" to "Italian", "portuguese" to "Portuguese", "português" to "Portuguese",
        "russian" to "Russian", "arabic" to "Arabic", "turkish" to "Turkish", "thai" to "Thai", "urdu" to "Urdu"
    )
}
