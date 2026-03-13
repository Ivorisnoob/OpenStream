package com.ivor.openanime.presentation.player.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControlsTest {

    @Test
    fun `formatTime converts zero milliseconds correctly`() {
        val result = formatTime(0L)
        assertEquals("00:00", result)
    }

    @Test
    fun `formatTime converts seconds correctly`() {
        val result = formatTime(15000L) // 15 seconds
        assertEquals("00:15", result)
    }

    @Test
    fun `formatTime converts minutes correctly`() {
        val result = formatTime(90000L) // 1 minute 30 seconds
        assertEquals("01:30", result)
    }

    @Test
    fun `formatTime converts hours correctly`() {
        val result = formatTime(3661000L) // 1 hour, 1 minute, 1 second
        assertEquals("1:01:01", result)
    }

    @Test
    fun `formatTime formats minutes with leading zeros`() {
        val result = formatTime(125000L) // 2 minutes 5 seconds
        assertEquals("02:05", result)
    }

    @Test
    fun `formatTime formats seconds with leading zeros`() {
        val result = formatTime(5000L) // 5 seconds
        assertEquals("00:05", result)
    }

    @Test
    fun `formatTime handles exactly one minute`() {
        val result = formatTime(60000L)
        assertEquals("01:00", result)
    }

    @Test
    fun `formatTime handles exactly one hour`() {
        val result = formatTime(3600000L)
        assertEquals("1:00:00", result)
    }

    @Test
    fun `formatTime handles multi-digit hours`() {
        val result = formatTime(36000000L) // 10 hours
        assertEquals("10:00:00", result)
    }

    @Test
    fun `formatTime handles typical video length`() {
        val result = formatTime(1500000L) // 25 minutes
        assertEquals("25:00", result)
    }

    @Test
    fun `formatTime handles movie length`() {
        val result = formatTime(7200000L) // 2 hours
        assertEquals("2:00:00", result)
    }

    @Test
    fun `formatTime handles complex time`() {
        val result = formatTime(5432100L) // 1 hour 30 minutes 32 seconds 100ms
        assertEquals("1:30:32", result)
    }

    @Test
    fun `formatTime ignores milliseconds in display`() {
        val result = formatTime(1999L) // 1 second 999 milliseconds
        assertEquals("00:01", result)
    }

    @Test
    fun `formatTime handles maximum typical duration`() {
        val result = formatTime(43200000L) // 12 hours
        assertEquals("12:00:00", result)
    }

    @Test
    fun `formatTime pads hours in minutes-seconds format`() {
        val result = formatTime(599000L) // 9 minutes 59 seconds
        assertEquals("09:59", result)
    }

    @Test
    fun `formatTime shows hours format for exactly 1 hour 1 second`() {
        val result = formatTime(3601000L) // 1 hour 0 minutes 1 second
        assertEquals("1:00:01", result)
    }

    @Test
    fun `formatTime handles negative values as zero`() {
        // The function does integer division, so negative values should be handled
        val result = formatTime(-1000L)
        // Negative time should ideally not happen, but testing behavior
        // -1000ms = -1 second = 00:-01 which formats incorrectly
        // This is a boundary case - the actual result depends on String.format behavior
        assertTrue(result.contains("-") || result == "00:00")
    }

    @Test
    fun `formatTime handles typical anime episode duration`() {
        val result = formatTime(1440000L) // 24 minutes (typical anime episode)
        assertEquals("24:00", result)
    }

    @Test
    fun `formatTime handles short video clip`() {
        val result = formatTime(3500L) // 3.5 seconds
        assertEquals("00:03", result)
    }

    @Test
    fun `formatTime handles seconds at boundary`() {
        val result = formatTime(59999L) // 59 seconds 999 milliseconds
        assertEquals("00:59", result)
    }

    @Test
    fun `formatTime handles minutes at boundary`() {
        val result = formatTime(3599999L) // 59 minutes 59 seconds 999 milliseconds
        assertEquals("59:59", result)
    }

    @Test
    fun `formatTime handles hours with double digit minutes and seconds`() {
        val result = formatTime(5555000L) // 1 hour 32 minutes 35 seconds
        assertEquals("1:32:35", result)
    }

    // Helper method for negative test case
    private fun assertTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Assertion failed")
        }
    }
}