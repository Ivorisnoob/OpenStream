package com.ivor.openstream.data.streaming.anime

import com.ivor.openstream.domain.model.StreamQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimeSiteParsingTest {

    @Test
    fun `megaplay payload decrypts to the stream file`() {
        // Captured from megaplay.buzz/stream/getSourcesNew (Frieren episode 1, tcdn server).
        val enc = "wdeBruh3qqn_i5wUNnyaPf4C_ZqTXgr0fSawVujYvQ83aeKbKNaav8LzVZBqxz9ib5tKkVkiKceQ9tJYbIzlNe1R5Z-l9lVHXscUFwh7yprh8JX7xVXx9rLpb5Hl_JZDgnHW2cvM0y_iKN1_TYG_RkCOsl5oxep02JtCZZw2za0"

        assertEquals(
            """{"file":"https://megap.mikora.top/bb6d2babd7797d94d8f4a8600bc9b44e/b7d51fb7e838ee9b60dcdb34b953bc07/master.m3u8"}""",
            MegaplayExtractor.decrypt(enc)
        )
    }

    @Test
    fun `packed kwik script unpacks to its playlist url`() {
        val packed = "eval(function(p,a,c,k,e,d){return p}('0 1=\\'2\\';',3,3,'const|source|https://cdn.example/stream/uwu.m3u8'.split('|'),0,{}))"

        assertEquals("const source='https://cdn.example/stream/uwu.m3u8';", PackerUnpacker.unpack(packed))
    }

    @Test
    fun `unpacked words above radix 36 use letters past z`() {
        // Radix 62: index 36 encodes as 'A', index 61 as 'Z'.
        val words = List(62) { "w$it" }.joinToString("|")
        val packed = "}('A Z 1',62,62,'$words'.split('|'),0,{}))"

        assertEquals("w36 w61 w1", PackerUnpacker.unpack(packed))
    }

    @Test
    fun `master playlist quality is its tallest rendition`() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
            720.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1800112,RESOLUTION=1920x1080
            1080.m3u8
            #EXT-X-I-FRAME-STREAM-INF:BANDWIDTH=49071,RESOLUTION=640x360,URI="iframes.m3u8"
        """.trimIndent()

        assertEquals(StreamQuality.Q1080, bestPlaylistQuality(playlist))
    }

    @Test
    fun `episode data attributes are read from anikoto tags`() {
        val tag = """<a href="#" data-id="97908" data-num="1" data-mal="52991" data-ids="SUR=" class="active">"""

        assertEquals(
            mapOf("id" to "97908", "num" to "1", "mal" to "52991", "ids" to "SUR="),
            dataAttributes(tag)
        )
    }
}
