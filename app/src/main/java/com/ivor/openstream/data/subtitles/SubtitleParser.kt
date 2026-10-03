package com.ivor.openstream.data.subtitles

import android.util.Log

/** Parses SRT, WebVTT or ASS/SSA text into cues. */
fun parseSubtitles(content: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    val cleanContent = content.replace("\ufeff", "").replace("\r\n", "\n").replace("\r", "\n")

    if (cleanContent.contains("[Events]")) {
        Log.i("PlayerSubtitles", "Detected ASS/SSA format")
        val lines = cleanContent.lines()
        val eventsIndex = lines.indexOfFirst { it.trim().contains("[Events]", ignoreCase = true) }
        if (eventsIndex != -1) {
            val dialogueLines = lines.drop(eventsIndex + 1).filter { it.trim().startsWith("Dialogue:", ignoreCase = true) }
            Log.d("PlayerSubtitles", "Found ${dialogueLines.size} Dialogue lines")
            for (line in dialogueLines) {
                try {
                    // Dialogue: 0,0:00:28.57,0:00:30.40,Default,,0,0,0,,Text
                    // Limit is 10 because the text part can contain commas
                    val parts = line.split(",", limit = 10)
                    if (parts.size >= 10) {
                        val start = parseAssTimestamp(parts[1])
                        val end = parseAssTimestamp(parts[2])
                        // Strip ASS override tags like {\fn...} and handle \N (newline)
                        val text = parts[9].replace(Regex("\\{[^}]*\\}"), "")
                                       .replace("\\N", "\n")
                                       .replace("\\n", "\n")
                                       .replace("\\h", " ")
                                       .trim()
                        if (text.isNotEmpty()) {
                            cues.add(SubtitleCue(start, end, text))
                        }
                    }
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
        }
    } else {
        Log.i("PlayerSubtitles", "Detected SRT/VTT format")
        // WebVTT may leave the hours out ("01:02.345"); SRT always has them ("00:01:02,345").
        val timestampRegex = Regex("((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{1,3})\\s*-->\\s*((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[,.]\\d{1,3})")
        val blocks = cleanContent.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotBlank() }
            val match = timestampRegex.find(block)
            
            if (match != null) {
                val start = parseTimestamp(match.groupValues[1])
                val end = parseTimestamp(match.groupValues[2])
                
                val textLines = lines.dropWhile { !it.contains("-->") }.drop(1)
                val textRaw = textLines.joinToString("\n").trim()
                
                if (textRaw.isNotEmpty()) {
                    val cleanedText = textRaw.replace(Regex("<[^>]*>"), "").trim()
                    if (cleanedText.isNotEmpty()) {
                        cues.add(SubtitleCue(start, end, cleanedText))
                    }
                }
            }
        }
    }
    
    if (cues.isNotEmpty()) {
        Log.i("PlayerSubtitles", "Successfully parsed ${cues.size} cues. First: ${cues.first().text}")
    } else {
        Log.w("PlayerSubtitles", "Parsed 0 cues from content length: ${cleanContent.length}")
    }
    return cues
}

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

private fun parseAssTimestamp(ts: String): Long {
    try {
        val parts = ts.trim().split(':')
        if (parts.size < 3) return 0L
        val h = parts[0].toLongOrNull() ?: 0L
        val m = parts[1].toLongOrNull() ?: 0L
        val sParts = parts[2].split('.')
        val s = sParts[0].toLongOrNull() ?: 0L
        val ms = if (sParts.size > 1) {
            // ASS usually has 2 decimals, e.g. .57 -> 570ms
            val msStr = sParts[1].padEnd(3, '0').take(3)
            msStr.toLongOrNull() ?: 0L
        } else 0L
        return (h * 3600 + m * 60 + s) * 1000 + ms
    } catch (e: Exception) {
        return 0L
    }
}

/** "hh:mm:ss.mmm", or "mm:ss.mmm" as WebVTT allows; a comma works as the separator too. */
private fun parseTimestamp(ts: String): Long {
    val clean = ts.trim().replace(',', '.')
    val parts = clean.split(':')
    if (parts.size !in 2..3) return 0L

    val secondsParts = parts.last().split('.')

    val h = if (parts.size == 3) parts[0].toLongOrNull() ?: 0L else 0L
    val m = parts[parts.size - 2].toLongOrNull() ?: 0L
    val s = secondsParts[0].toLongOrNull() ?: 0L
    val ms = if (secondsParts.size > 1) {
        secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
    } else 0L
    
    return (h * 3600 + m * 60 + s) * 1000 + ms
}

private val SUBTITLE_AD = Regex("""(?i)(www\.|https?://|\.(link|lt|com|net|org)\b|opensubtitles|osdb|subtitletools)""")

/** Community subtitle files often open with a promo line (a site address); true for such a cue. */
fun String.isSubtitleAd(): Boolean = SUBTITLE_AD.containsMatchIn(this)

/** Cues as a WebVTT file, the caption format Cast receivers read. */
fun List<SubtitleCue>.toWebVtt(): String = buildString {
    append("WEBVTT\n\n")
    this@toWebVtt.forEachIndexed { index, cue ->
        append(index + 1).append('\n')
        append(vttTime(cue.startMs)).append(" --> ").append(vttTime(cue.endMs)).append('\n')
        // A blank line would end the cue early.
        append(cue.text.lines().filter { it.isNotBlank() }.joinToString("\n")).append("\n\n")
    }
}

private fun vttTime(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val h = clamped / 3_600_000
    val m = clamped / 60_000 % 60
    val s = clamped / 1_000 % 60
    return "%02d:%02d:%02d.%03d".format(java.util.Locale.ROOT, h, m, s, clamped % 1_000)
}
