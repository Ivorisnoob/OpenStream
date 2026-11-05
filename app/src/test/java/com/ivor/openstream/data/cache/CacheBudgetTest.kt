package com.ivor.openstream.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * `bytesFor` has to hold the cache to a budget on a device, and to degrade to the wanted size
 * whenever it can't measure anything: the folder chain doesn't exist yet, or `StatFs` fails.
 * On the JVM `StatFs` is unimplemented, so this exercises the degradation, not the free/10 cap.
 */
class CacheBudgetTest {

    @Test
    fun `a folder that is not on disk hands back the wanted size`() {
        // Nothing exists anywhere up this chain, so there is no disk to measure and no space to guard.
        val missing = File("openstream-cache-budget-missing").resolve("cache")
        val wanted = 300L * 1024 * 1024
        assertEquals(wanted, CacheBudget.bytesFor(missing, wanted))
    }

    @Test
    fun `a folder that exists but cannot be measured hands back the wanted size`() {
        // On a device StatFs answers here; on the JVM it throws and runCatching falls through.
        val existing = File(System.getProperty("java.io.tmpdir"), "openstream-cache-budget")
        val wanted = 50L * 1024 * 1024
        assertEquals(wanted, CacheBudget.bytesFor(existing, wanted))
    }
}
