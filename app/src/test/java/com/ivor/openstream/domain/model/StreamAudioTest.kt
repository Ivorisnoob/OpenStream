package com.ivor.openstream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamAudioTest {
    @Test
    fun `language-specific routes are presented as dubbed audio`() {
        assertEquals(StreamAudio.DUB, StreamAudio.parse("English"))
        assertEquals(StreamAudio.DUB, StreamAudio.parse("Hindi"))
        assertEquals(StreamAudio.DUB, StreamAudio.parse("german"))
    }

    @Test
    fun `multi audio wins over an embedded dub descriptor`() {
        assertEquals(StreamAudio.MULTI, StreamAudio.parse("multi english dub"))
    }
}
