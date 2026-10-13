package com.ivor.openstream.domain.model

import androidx.annotation.StringRes
import com.ivor.openstream.R

enum class StreamQuality(val label: String, @StringRes val labelRes: Int?, val rank: Int) {
    Q360("360p", R.string.q_360p, 0),
    Q480("480p", R.string.q_480p, 1),
    Q720("720p", R.string.q_720p, 2),
    Q1080("1080p", R.string.q_1080p, 3),
    HD("HD", R.string.q_hd, 3),
    Q1440("1440p", R.string.q_1440p, 4),
    Q2160("4K", R.string.q_2160p, 5),
    UNKNOWN("Auto", R.string.sheet_auto, 2);

    companion object {
        fun parse(raw: String?): StreamQuality {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            val height = Regex("(?:^|\\D)(2160|1440|1080|720|480|360)(?:p|\\D|$)")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)

            return when {
                normalized.contains("4k") || height == "2160" -> Q2160
                height == "1440" -> Q1440
                height == "1080" -> Q1080
                height == "720" -> Q720
                height == "480" -> Q480
                height == "360" -> Q360
                normalized == "hd" || normalized == "full hd" -> HD
                else -> UNKNOWN
            }
        }

        /**
         * The highest rank a rendition may carry to sit inside a [heightPx] cap.
         *
         * Ranks are not heights, so the cap has to be translated through the enum: a 1080p
         * cap admits ranks up to [Q1080], a 2160p cap admits everything, and a 0 or
         * negative cap means "no limit" and also admits everything.
         */
        fun maxRankForHeight(heightPx: Int): Int = when {
            heightPx <= 0 -> Int.MAX_VALUE
            heightPx >= 2160 -> Int.MAX_VALUE
            heightPx >= 1440 -> Q1440.rank
            heightPx >= 1080 -> Q1080.rank
            heightPx >= 720 -> Q720.rank
            heightPx >= 480 -> Q480.rank
            else -> Q360.rank
        }
    }

    /**
     * Whether a server labelled with this quality may be picked while a [heightPx] cap applies.
     *
     * [UNKNOWN] is always admitted: it is an adaptive master playlist whose actual renditions
     * are bounded by the track-selection cap, not by the label, so excluding it would strand
     * a source that is perfectly capable of playing inside the cap.
     */
    fun fitsHeightCap(heightPx: Int): Boolean =
        this == UNKNOWN || rank <= StreamQuality.maxRankForHeight(heightPx)
}
