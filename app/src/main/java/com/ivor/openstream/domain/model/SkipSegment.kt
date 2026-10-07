package com.ivor.openstream.domain.model

import androidx.annotation.StringRes
import com.ivor.openstream.R

enum class SkipType(val label: String, @StringRes val labelRes: Int) {
    INTRO("Skip intro", R.string.player_skip_intro),
    RECAP("Skip recap", R.string.player_skip_recap),
    CREDITS("Skip credits", R.string.player_skip_credits)
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
