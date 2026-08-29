package com.ivor.openstream.presentation.player

import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSourcePresentationTest {
    @Test
    fun adaptiveSourcesDescribeQualityAsMaximum() {
        assertEquals("Up to 1080p", server("stream.m3u8", StreamQuality.Q1080).sourceQualityLabel())
        assertEquals("Adaptive", server("stream.mpd", StreamQuality.UNKNOWN).sourceQualityLabel())
    }

    @Test
    fun fixedSourcesDescribeExactQuality() {
        assertEquals("720p", server("video.mp4", StreamQuality.Q720).sourceQualityLabel())
        assertEquals("Quality unknown", server("video.mp4", StreamQuality.UNKNOWN).sourceQualityLabel())
    }

    @Test
    fun summaryOnlyIncludesKnownAudioMetadata() {
        assertEquals(
            "Up to 1080p · SUB · HLS",
            server("stream.m3u8", StreamQuality.Q1080, StreamAudio.SUB).sourceSummary()
        )
        assertEquals(
            "720p · MP4",
            server("video.mp4", StreamQuality.Q720).sourceSummary()
        )
    }

    private fun server(
        url: String,
        quality: StreamQuality,
        audio: StreamAudio = StreamAudio.UNKNOWN
    ) = VideoServer(
        id = "server",
        providerId = "provider",
        providerName = "Provider",
        name = "Source",
        url = "https://example.com/$url",
        quality = quality,
        audio = audio
    )
}
