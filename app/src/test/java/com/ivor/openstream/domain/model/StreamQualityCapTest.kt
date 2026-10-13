package com.ivor.openstream.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streaming quality cap.
 *
 * The bug this guards against: servers are ranked best-first, so an uncapped default always
 * landed on the 4K entry, and that is what a TV started playing before anyone touched a
 * setting. The cap has to work at both layers — server *selection* (this file) and ExoPlayer
 * *track selection* — and it has to keep admitting adaptive streams, which are labelled
 * "Auto" but can render at any height.
 */
class StreamQualityCapTest {

    @Test
    fun `a 1080p cap admits 1080p and below but not 1440p or 4K`() {
        assertTrue(StreamQuality.Q1080.fitsHeightCap(1080))
        assertTrue(StreamQuality.HD.fitsHeightCap(1080))
        assertTrue(StreamQuality.Q720.fitsHeightCap(1080))
        assertFalse(StreamQuality.Q1440.fitsHeightCap(1080))
        assertFalse(StreamQuality.Q2160.fitsHeightCap(1080))
    }

    @Test
    fun `a 720p cap admits 720p and below but not 1080p`() {
        assertTrue(StreamQuality.Q720.fitsHeightCap(720))
        assertTrue(StreamQuality.Q480.fitsHeightCap(720))
        assertFalse(StreamQuality.Q1080.fitsHeightCap(720))
        assertFalse(StreamQuality.Q2160.fitsHeightCap(720))
    }

    @Test
    fun `a 4K cap admits everything`() {
        StreamQuality.entries.forEach { quality ->
            assertTrue("$quality should fit a 4K cap", quality.fitsHeightCap(2160))
        }
    }

    @Test
    fun `no cap admits everything`() {
        // 0 is the "No limit" setting value, and Int.MAX_VALUE is how the session
        // represents "uncapped"; both must behave identically.
        StreamQuality.entries.forEach { quality ->
            assertTrue("$quality should fit a 0 cap", quality.fitsHeightCap(0))
            assertTrue("$quality should fit no cap", quality.fitsHeightCap(Int.MAX_VALUE))
        }
    }

    @Test
    fun `an adaptive stream is always admitted regardless of the cap`() {
        // UNKNOWN is a master playlist, not a fixed rendition. Excluding it would strand
        // sources that can perfectly well play inside the cap, because the track-selection
        // cap is what actually bounds what they render.
        assertTrue(StreamQuality.UNKNOWN.fitsHeightCap(360))
        assertTrue(StreamQuality.UNKNOWN.fitsHeightCap(1080))
    }

    @Test
    fun `maxRankForHeight steps down through the scale`() {
        assertEquals(Int.MAX_VALUE, StreamQuality.maxRankForHeight(2160))
        assertEquals(StreamQuality.Q1440.rank, StreamQuality.maxRankForHeight(1440))
        assertEquals(StreamQuality.Q1080.rank, StreamQuality.maxRankForHeight(1080))
        assertEquals(StreamQuality.Q720.rank, StreamQuality.maxRankForHeight(720))
        assertEquals(StreamQuality.Q480.rank, StreamQuality.maxRankForHeight(480))
        assertEquals(StreamQuality.Q360.rank, StreamQuality.maxRankForHeight(360))
        // Below the scale still yields something playable rather than admitting nothing.
        assertEquals(StreamQuality.Q360.rank, StreamQuality.maxRankForHeight(240))
    }
}