package com.ivor.openstream.domain.model

import androidx.annotation.StringRes
import com.ivor.openstream.R

enum class StreamAudio(val label: String, @StringRes val labelRes: Int?, val rank: Int) {
    SUB("Sub", R.string.audio_sub, 4),
    DUB("Dub", R.string.player_dub, 3),
    MULTI("Multi-audio", R.string.audio_multi, 2),
    RAW("Raw", R.string.audio_raw, 1),
    UNKNOWN("", null, 0);

    companion object {
        fun parse(raw: String?): StreamAudio {
            val normalized = raw?.lowercase().orEmpty()
            return when {
                "multi" in normalized || "dual" in normalized -> MULTI
                "dub" in normalized ||
                    "english" in normalized ||
                    "hindi" in normalized ||
                    "german" in normalized -> DUB
                "sub" in normalized -> SUB
                "raw" in normalized -> RAW
                else -> UNKNOWN
            }
        }
    }
}
