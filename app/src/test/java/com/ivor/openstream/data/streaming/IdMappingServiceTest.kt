package com.ivor.openstream.data.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The freshness rule for remembered TMDB → provider id mappings. A month-old entry is trusted
 * until then, drops out the instant it expires, and an entry dated after "now" (clock jump,
 * restored backup) is not trusted either.
 */
class IdMappingServiceTest {

    @Test
    fun `entry saved just now counts as fresh`() {
        assertTrue(IdMappingService.isFresh(resolvedAt = now, now = now + 1))
    }

    @Test
    fun `entry a day under the ttl is still fresh`() {
        assertTrue(IdMappingService.isFresh(resolvedAt = now, now = IdMappingService.TTL_MS - 1))
    }

    @Test
    fun `entry at the ttl is expired`() {
        assertFalse(IdMappingService.isFresh(resolvedAt = now, now = now + IdMappingService.TTL_MS))
    }

    @Test
    fun `entry older than ttl is expired`() {
        assertFalse(
            IdMappingService.isFresh(resolvedAt = now, now = now + IdMappingService.TTL_MS + 1)
        )
    }

    @Test
    fun `resolvedAt in the future counts as not fresh`() {
        assertFalse(IdMappingService.isFresh(resolvedAt = now + 1, now = now))
    }

    private val now: Long = TimeUnit.DAYS.toMillis(10)
}
