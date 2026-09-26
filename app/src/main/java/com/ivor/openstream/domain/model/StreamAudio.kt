package com.ivor.openstream.domain.model

enum class StreamAudio(val label: String, val rank: Int) {
    SUB("Sub", 4),
    DUB("Dub", 3),
    MULTI("Multi-audio", 2),
    RAW("Raw", 1),
    UNKNOWN("", 0);

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
