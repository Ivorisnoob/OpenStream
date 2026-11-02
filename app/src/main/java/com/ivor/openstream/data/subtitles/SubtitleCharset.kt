package com.ivor.openstream.data.subtitles

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Turns subtitle file bytes into text. Subtitle files carry no charset of their own, and many
 * older SRTs are saved in the Windows codepage of their language (Turkish in windows-1254, Russian
 * in windows-1251...), so reading every non-UTF-8 file as windows-1252 garbles them ("Korsanýn"
 * for "Korsanın").
 *
 * In order: a byte order mark decides; else BOM-less UTF-16 (every other byte zero, which would
 * otherwise pass as UTF-8); else text that is valid UTF-8 is UTF-8; else the usual codepage of
 * the subtitle's language (windows-1252 for Western ones); else, with no language, a guess from
 * which high bytes the file uses.
 */
object SubtitleCharset {

    /** [language] is the subtitle's ISO 639-1 code when known ("tr"); it picks the legacy codepage. */
    fun decode(bytes: ByteArray, language: String? = null): String {
        val text = bomCharset(bytes)?.let { (charset, bomSize) -> String(bytes, bomSize, bytes.size - bomSize, charset) }
            ?: utf16WithoutBom(bytes)?.let { String(bytes, it) }
            ?: strictUtf8(bytes)
            ?: String(bytes, legacyCharset(bytes, language))
        return text.removePrefix("\uFEFF")
    }

    /** The codepage a file that is neither marked nor valid UTF-8 is read in. */
    internal fun legacyCharset(bytes: ByteArray, language: String?): Charset {
        val code = language?.trim()?.lowercase()?.substringBefore('-')?.takeIf { it.isNotEmpty() }
        val name = if (code != null) LANGUAGE_CODEPAGES[code] ?: WESTERN else guessCodepage(bytes)
        return charsetOrNull(name) ?: charsetOrNull(WESTERN) ?: Charsets.ISO_8859_1
    }

    private fun bomCharset(bytes: ByteArray): Pair<Charset, Int>? = when {
        bytes.startsWith(0xEF, 0xBB, 0xBF) -> Charsets.UTF_8 to 3
        bytes.startsWith(0xFF, 0xFE) -> Charsets.UTF_16LE to 2
        bytes.startsWith(0xFE, 0xFF) -> Charsets.UTF_16BE to 2
        else -> null
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /** UTF-16 saved without a BOM: mostly-ASCII text leaves every other byte zero. */
    private fun utf16WithoutBom(bytes: ByteArray): Charset? {
        val sample = minOf(bytes.size, UTF16_SAMPLE) and 1.inv()
        if (sample < 4) return null
        var evenZeros = 0
        var oddZeros = 0
        for (index in 0 until sample) {
            if (bytes[index] != 0.toByte()) continue
            if (index % 2 == 0) evenZeros++ else oddZeros++
        }
        val half = sample / 2
        return when {
            oddZeros * 10 >= half * 6 && evenZeros * 10 < half -> Charsets.UTF_16LE
            evenZeros * 10 >= half * 6 && oddZeros * 10 < half -> Charsets.UTF_16BE
            else -> null
        }
    }

    /**
     * Guesses the codepage of a file whose language is unknown, from its high bytes:
     * text mostly in high bytes is a non-Latin script (Hebrew when no byte falls in the range
     * Cyrillic, Greek and Arabic use for capitals and letters, otherwise Cyrillic); Latin text
     * leaning on the bytes that are Turkish or Central European letters but rare in
     * windows-1252 text is read in that codepage; anything else stays windows-1252.
     */
    private fun guessCodepage(bytes: ByteArray): String {
        var high = 0
        var asciiLetters = 0
        var upperHalf = 0
        var turkish = 0
        var central = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            when {
                value < 0x80 -> if (value in 0x41..0x5A || value in 0x61..0x7A) asciiLetters++
                else -> {
                    high++
                    if (value in 0xC0..0xDF) upperHalf++
                    if (value in TURKISH_MARKERS) turkish++
                    if (value in CENTRAL_MARKERS) central++
                }
            }
        }
        if (high == 0) return WESTERN
        if (high * 10 > (high + asciiLetters) * 4) return if (upperHalf == 0) "windows-1255" else "windows-1251"
        val threshold = maxOf(MIN_MARKERS, high / 10)
        return when {
            turkish >= threshold && turkish >= central -> "windows-1254"
            central >= threshold -> "windows-1250"
            else -> WESTERN
        }
    }

    private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { (this[it].toInt() and 0xFF) == prefix[it] }

    private const val WESTERN = "windows-1252"
    private const val UTF16_SAMPLE = 4_096
    private const val MIN_MARKERS = 3

    /** ı ş ğ İ Ş Ğ in windows-1254; ý þ ð Ý Þ Ð in windows-1252, which Western subtitles barely use. */
    private val TURKISH_MARKERS = setOf(0xFD, 0xFE, 0xF0, 0xDD, 0xDE, 0xD0)

    /**
     * ą ł Ą ľ ź Ś in windows-1250 (¹ ³ ¥ ¾ Ÿ Œ in windows-1252, rare in subtitles), plus the
     * Ť Ź ť bytes windows-1252 leaves unassigned.
     */
    private val CENTRAL_MARKERS = setOf(0xB9, 0xB3, 0xA5, 0xBE, 0x9F, 0x8C, 0x8D, 0x8F, 0x9D)

    /** Windows codepages that subtitles in these languages are usually saved in. */
    private val LANGUAGE_CODEPAGES: Map<String, String> = buildMap {
        put("tr", "windows-1254")
        put("az", "windows-1254")
        put("el", "windows-1253")
        listOf("ru", "uk", "bg", "be", "mk").forEach { put(it, "windows-1251") }
        listOf("ar", "fa", "ur").forEach { put(it, "windows-1256") }
        put("he", "windows-1255")
        put("iw", "windows-1255")
        listOf("pl", "cs", "sk", "sl", "hu", "ro", "hr", "bs", "sq").forEach { put(it, "windows-1250") }
        put("vi", "windows-1258")
        listOf("lt", "lv", "et").forEach { put(it, "windows-1257") }
        put("th", "TIS-620")
        put("ja", "Shift_JIS")
        put("zh", "GBK")
        put("ko", "EUC-KR")
    }
}
