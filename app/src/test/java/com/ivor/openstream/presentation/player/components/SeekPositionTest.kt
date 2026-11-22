package com.ivor.openstream.presentation.player.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekPositionTest {
    @Test fun `exact endpoints always win over the origin`() {
        assertEquals(0L, snapSeekPosition(0L, 200L, 60_000L))
        assertEquals(60_000L, snapSeekPosition(60_000L, 59_800L, 60_000L))
    }

    @Test fun `only a nearby position snaps to the origin`() {
        assertEquals(20_000L, snapSeekPosition(20_150L, 20_000L, 60_000L))
        assertEquals(20_800L, snapSeekPosition(20_800L, 20_000L, 60_000L))
    }
}
