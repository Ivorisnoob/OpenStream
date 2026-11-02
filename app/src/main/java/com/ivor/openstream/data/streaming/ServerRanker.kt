package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer

internal object ServerRanker {
    fun mergeAndRank(
        existing: List<VideoServer>,
        incoming: List<VideoServer>,
        providerPriorities: Map<String, Int>,
        preferredServerId: String?,
        /** The user's source order before quality ("search in my order"); otherwise quality first. */
        providerFirst: Boolean = false,
        /** Streaming quality cap in pixels; 0 or Int.MAX_VALUE means no cap. */
        qualityCapHeight: Int = Int.MAX_VALUE
    ): List<VideoServer> {
        val merged = linkedMapOf<String, VideoServer>()
        (existing + incoming).forEach { candidate ->
            val key = candidate.url.substringBefore('#')
            val current = merged[key]
            if (current == null || compare(candidate, current, providerPriorities, providerFirst, qualityCapHeight) < 0) {
                merged[key] = candidate
            }
        }

        return merged.values.sortedWith { left, right ->
            when {
                left.id == preferredServerId && right.id != preferredServerId -> -1
                right.id == preferredServerId && left.id != preferredServerId -> 1
                else -> compare(left, right, providerPriorities, providerFirst, qualityCapHeight)
            }
        }
    }

    /**
     * True when [servers] include one whose stated height is exactly the cap ([qualityCapHeight];
     * 0 or 2160 and up mean the best there is, 4K). Unknown or vague labels ("Auto", "HD") never
     * count, because nothing says how close to the cap they are.
     */
    fun reachesQualityCap(servers: List<VideoServer>, qualityCapHeight: Int): Boolean {
        val capRank = StreamQuality.maxRankForHeight(qualityCapHeight).coerceAtMost(StreamQuality.Q2160.rank)
        return servers.any { server ->
            server.quality != StreamQuality.UNKNOWN &&
                server.quality != StreamQuality.HD &&
                server.quality.rank == capRank
        }
    }

    private fun compare(
        left: VideoServer,
        right: VideoServer,
        providerPriorities: Map<String, Int>,
        providerFirst: Boolean,
        qualityCapHeight: Int
    ): Int {
        val provider = (providerPriorities[left.providerId] ?: Int.MAX_VALUE)
            .compareTo(providerPriorities[right.providerId] ?: Int.MAX_VALUE)
        if (providerFirst && provider != 0) return provider
        // Prefer servers that fit the quality cap.
        val cap = qualityCapHeight.takeIf { it > 0 } ?: Int.MAX_VALUE
        val leftFits = left.quality.fitsHeightCap(cap)
        val rightFits = right.quality.fitsHeightCap(cap)
        if (leftFits != rightFits) return if (leftFits) -1 else 1
        val quality = right.quality.rank.compareTo(left.quality.rank)
        if (quality != 0) return quality
        val audio = right.audio.rank.compareTo(left.audio.rank)
        if (audio != 0) return audio
        if (provider != 0) return provider
        return left.name.compareTo(right.name, ignoreCase = true)
    }
}
