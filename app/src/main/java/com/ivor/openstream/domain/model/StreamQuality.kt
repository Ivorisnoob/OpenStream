package com.ivor.openstream.domain.model

enum class StreamQuality(val label: String, val rank: Int) {
    Q360("360p", 0),
    Q480("480p", 1),
    Q720("720p", 2),
    Q1080("1080p", 3),
    HD("HD", 3),
    Q1440("1440p", 4),
    Q2160("4K", 5),
    UNKNOWN("Auto", 2);

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
    }
}
