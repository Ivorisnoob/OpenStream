package com.ivor.openstream.data.playback

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoOutputRangeTest {
    private val sdr = Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H265).build()

    @Test fun `PQ and HLG trigger HDR output`() {
        assertFalse(sdr.requiresHdrOutput())
        for (transfer in listOf(C.COLOR_TRANSFER_ST2084, C.COLOR_TRANSFER_HLG)) {
            val hdr = sdr.buildUpon().setColorInfo(ColorInfo.Builder().setColorTransfer(transfer).build()).build()
            assertTrue(hdr.requiresHdrOutput())
        }
    }

    @Test fun `Dolby Vision codec identifiers establish HDR but absent tracks do not establish SDR`() {
        assertTrue(sdr.buildUpon().setCodecs("dvh1.08.06").build().requiresHdrOutput())
        assertFalse(sdr.buildUpon().setCodecs("hev1.2.4.L120.B0").build().requiresHdrOutput())
        assertTrue(Tracks.EMPTY.selectedVideoRanges().isEmpty())
    }
}
