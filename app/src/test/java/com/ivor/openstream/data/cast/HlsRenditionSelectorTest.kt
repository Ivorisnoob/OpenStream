package com.ivor.openstream.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsRenditionSelectorTest {
    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="en.m3u8"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Japanese",LANGUAGE="ja",DEFAULT=NO,URI="ja.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2",AUDIO="aud"
        1080.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2",AUDIO="aud"
        720.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="aud"
        360.m3u8
    """.trimIndent()

    @Test
    fun keepsOnlyTheChosenHeightAndAudioAsDefault() {
        val result = HlsRenditionSelector.select(
            master,
            HlsSelection(height = 720, audioLanguage = "jpn", audioName = "Japanese")
        )

        assertTrue(result.contains("720.m3u8"))
        assertFalse(result.contains("1080.m3u8"))
        assertFalse(result.contains("360.m3u8"))
        assertFalse(result.contains("NAME=\"English\""))
        assertTrue(result.contains("NAME=\"Japanese\",LANGUAGE=\"ja\",DEFAULT=YES,URI=\"ja.m3u8\",AUTOSELECT=YES"))
    }

    @Test
    fun automaticKeepsEveryVariantUnderTheCap() {
        val result = HlsRenditionSelector.select(master, HlsSelection(maxHeight = 720))

        assertFalse(result.contains("1080.m3u8"))
        assertTrue(result.contains("720.m3u8"))
        assertTrue(result.contains("360.m3u8"))
        assertTrue(result.contains("NAME=\"English\""))
        assertTrue(result.contains("NAME=\"Japanese\""))
    }

    @Test
    fun missingHeightFallsBackToTheNextOneDown() {
        val result = HlsRenditionSelector.select(master, HlsSelection(height = 480))

        assertEquals(listOf("360.m3u8"), result.lines().filter { it.endsWith(".m3u8") && !it.startsWith("#") })
    }

    @Test
    fun unmatchedAudioLeavesTheGroupAlone() {
        val result = HlsRenditionSelector.select(master, HlsSelection(audioLanguage = "fr"))

        assertEquals(master, result)
    }

    @Test
    fun listsHeightsAndAudio() {
        assertEquals(listOf(1080, 720, 360), HlsRenditionSelector.heights(master))
        assertEquals(listOf("English", "Japanese"), HlsRenditionSelector.audioRenditions(master).map { it.name })
    }

    @Test
    fun selectionSurvivesTheQuery() {
        val selection = HlsSelection(height = 1080, maxHeight = 1080, audioLanguage = "en", audioName = "English (AD)")

        assertEquals(selection, HlsSelection.fromQuery(selection.toQuery()))
    }
}
