package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.VideoServer

internal object ServerRanker {
    fun mergeAndRank(
        existing: List<VideoServer>,
        incoming: List<VideoServer>,
        providerPriorities: Map<String, Int>,
        preferredServerId: String?
    ): List<VideoServer> {
        val merged = linkedMapOf<String, VideoServer>()
        (existing + incoming).forEach { candidate ->
            val key = candidate.url.substringBefore('#')
            val current = merged[key]
            if (current == null || compare(candidate, current, providerPriorities) < 0) {
                merged[key] = candidate
            }
        }

        return merged.values.sortedWith { left, right ->
            when {
                left.id == preferredServerId && right.id != preferredServerId -> -1
                right.id == preferredServerId && left.id != preferredServerId -> 1
                else -> compare(left, right, providerPriorities)
            }
        }
    }

    private fun compare(
        left: VideoServer,
        right: VideoServer,
        providerPriorities: Map<String, Int>
    ): Int {
        val quality = right.quality.rank.compareTo(left.quality.rank)
        if (quality != 0) return quality
        val audio = right.audio.rank.compareTo(left.audio.rank)
        if (audio != 0) return audio
        val provider = (providerPriorities[left.providerId] ?: Int.MAX_VALUE)
            .compareTo(providerPriorities[right.providerId] ?: Int.MAX_VALUE)
        if (provider != 0) return provider
        return left.name.compareTo(right.name, ignoreCase = true)
    }
}
