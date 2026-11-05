package com.ivor.openstream.data.images

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Size
import com.ivor.openstream.data.settings.AppSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** TMDB artwork URLs, built the same way the screens build them so the cache entries match. */
object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"

    fun url(size: String, path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "$BASE$size$it" }
}

/**
 * Downloads artwork for rows the user hasn't scrolled to yet into Coil's disk cache, so it is
 * already there when they do.
 *
 * Built to stay out of the way: only on unmetered networks, never with Data Saver or battery saver
 * on or on low-memory phones, after the visible images have had a head start, a couple of images
 * at a time, nothing kept in memory, and each URL at most once per run. Anything already on disk
 * is skipped without a request. Failures are ignored; the screen loads the image normally later.
 */
@Singleton
class ArtworkPrefetcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(PARALLEL_DOWNLOADS)

    /** URLs already handled this run, oldest first, bounded. */
    private val seen = object : LinkedHashMap<String, Unit>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_REMEMBERED
    }

    /** Queues [urls] (nulls skipped) behind whatever is already queued. */
    fun prefetch(urls: List<String?>) {
        if (!allowedNow()) return
        val batch = urls.asSequence().filterNotNull().distinct().take(MAX_BATCH).toList()
        if (batch.isEmpty()) return
        scope.launch {
            // Let the images on screen go first; they share the network and the decoder.
            delay(HEAD_START_MS)
            for (url in batch) {
                if (!allowedNow()) return@launch
                if (!markSeen(url)) continue
                permits.withPermit { fetch(url) }
            }
        }
    }

    private suspend fun fetch(url: String) {
        try {
            val loader = SingletonImageLoader.get(context)
            val onDisk = loader.diskCache?.openSnapshot(url)?.use { true } ?: false
            if (onDisk) return
            // A 1x1 target keeps decoding nearly free; what matters is the file landing on disk.
            loader.execute(
                ImageRequest.Builder(context)
                    .data(url)
                    .size(Size(1, 1))
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Best effort only.
        }
    }

    @Synchronized
    private fun markSeen(url: String): Boolean {
        if (seen.containsKey(url)) return false
        seen[url] = Unit
        return true
    }

    private fun allowedNow(): Boolean {
        if (!settings.current.preloadArtwork) return false
        return runCatching {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val power = context.getSystemService(PowerManager::class.java)
            val activity = context.getSystemService(ActivityManager::class.java)
            connectivity.activeNetwork != null &&
                !connectivity.isActiveNetworkMetered &&
                connectivity.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED &&
                power?.isPowerSaveMode != true &&
                activity?.isLowRamDevice != true
        }.getOrDefault(false)
    }

    private companion object {
        const val PARALLEL_DOWNLOADS = 2
        const val HEAD_START_MS = 1_500L
        const val MAX_BATCH = 160
        const val MAX_REMEMBERED = 2_000
    }
}
