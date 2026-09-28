package com.ivor.openstream.domain.model

enum class SkipType(val label: String) {
    INTRO("Skip intro"),
    RECAP("Skip recap"),
    CREDITS("Skip credits")
}

/**
 * A stretch of an episode the player offers to jump past. [episodeLengthMs] is the length of the
 * file the submitter timed (0 when unknown); cuts of different lengths have different times.
 */
data class SkipSegment(val type: SkipType, val startMs: Long, val endMs: Long, val episodeLengthMs: Long = 0L)

/**
 * The segments that fit a video [durationMs] long: per type, the submission timed on the file
 * closest in length. Before the duration is known the first submission of each type is used.
 */
fun List<SkipSegment>.forDuration(durationMs: Long): List<SkipSegment> =
    groupBy { it.type }.values.mapNotNull { sameType ->
        if (durationMs <= 0L) sameType.firstOrNull()
        else sameType.minByOrNull { if (it.episodeLengthMs > 0) kotlin.math.abs(it.episodeLengthMs - durationMs) else Long.MAX_VALUE / 2 }
    }.filter { durationMs <= 0L || it.startMs < durationMs }.sortedBy { it.startMs }
