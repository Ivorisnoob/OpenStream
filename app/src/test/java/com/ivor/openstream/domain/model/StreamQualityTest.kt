package com.ivor.openstream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamQualityTest {
    @Test
    fun parsesCommonQualityLabels() {
        assertEquals(StreamQuality.Q1080, StreamQuality.parse("1080p"))
        assertEquals(StreamQuality.Q1080, StreamQuality.parse("Full HD 1920x1080"))
        assertEquals(StreamQuality.Q2160, StreamQuality.parse("4K UHD"))
        assertEquals(StreamQuality.Q720, StreamQuality.parse("HD-1 - 1280x720 - sub"))
        assertEquals(StreamQuality.HD, StreamQuality.parse("HD"))
        assertEquals(StreamQuality.UNKNOWN, StreamQuality.parse("default"))
        assertEquals(StreamQuality.UNKNOWN, StreamQuality.parse(null))
    }
}
