package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerRankerTest {
    @Test
    fun ranksQualityThenAudioThenProviderAndDeduplicatesUrls() {
        val low = server("low", "a", "https://example.com/low.m3u8", StreamQuality.Q720)
        val dub = server("dub", "b", "https://example.com/high.m3u8", StreamQuality.Q1080, StreamAudio.DUB)
        val sub = server("sub", "a", "https://example.com/high.m3u8", StreamQuality.Q1080, StreamAudio.SUB)

        val ranked = ServerRanker.mergeAndRank(
            existing = listOf(low, dub),
            incoming = listOf(sub),
            providerPriorities = mapOf("a" to 0, "b" to 1),
            preferredServerId = null
        )

        assertEquals(listOf("sub", "low"), ranked.map { it.id })
    }

    @Test
    fun promotesLastKnownWorkingServer() {
        val high = server("high", "a", "https://example.com/high.m3u8", StreamQuality.Q1080)
        val preferred = server("preferred", "b", "https://example.com/low.m3u8", StreamQuality.Q720)

        val ranked = ServerRanker.mergeAndRank(
            existing = emptyList(),
            incoming = listOf(high, preferred),
            providerPriorities = mapOf("a" to 0, "b" to 1),
            preferredServerId = "preferred"
        )

        assertEquals("preferred", ranked.first().id)
    }

    private fun server(
        id: String,
        providerId: String,
        url: String,
        quality: StreamQuality,
        audio: StreamAudio = StreamAudio.UNKNOWN
    ) = VideoServer(
        id = id,
        providerId = providerId,
        providerName = providerId,
        name = id,
        url = url,
        quality = quality,
        audio = audio
    )
}
