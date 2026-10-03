package com.ivor.openstream.presentation.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ivor.openstream.domain.model.WatchProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Poster art for the Continue Watching widget.
 *
 * RemoteViews has no image loader and cannot reach the app's disk cache, so the bitmap is fetched
 * and downsampled here and kept in `cacheDir`. The client is the app's streaming one, which already
 * goes through `AppDns` (see `NetworkModule`), so artwork loads where TMDB's DNS is blocked.
 */
@Singleton
class ContinueWatchingArtwork @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("StreamingClient") client: OkHttpClient
) {

    /** Short timeouts: a widget that misses its window shows no art at all. */
    private val client = client.newBuilder().callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()

    private val directory: File by lazy { File(context.cacheDir, "widget-art").apply { mkdirs() } }

    /** Cached art for [item], or null when it cannot be fetched. Never throws. */
    suspend fun load(item: WatchProgress): Bitmap? = withContext(Dispatchers.IO) {
        val path = item.stillPath ?: item.backdropPath ?: item.posterPath ?: return@withContext null
        val url = "https://image.tmdb.org/t/p/w500$path"
        val file = File(directory, hash(url))
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)?.takeIf { it.byteCount <= MAX_BYTES }?.let { return@withContext it }
        }
        decode(download(url) ?: return@withContext null)?.also { bitmap ->
            runCatching { file.writeBytes(bitmap.toJpegBytes()) }
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes()
        }
    }.getOrNull()

    /**
     * Decoded at roughly [TARGET_WIDTH] so the parcelled bitmap stays well inside the Binder
     * transaction limit. Two passes: measure, then sample.
     */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?.takeIf { it.byteCount <= MAX_BYTES }
    }

    private fun sampleSizeFor(sourceWidth: Int): Int {
        if (sourceWidth <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= TARGET_WIDTH) sample *= 2
        return sample
    }

    private fun Bitmap.toJpegBytes(): ByteArray =
        java.io.ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }

    private fun hash(url: String) = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(32)

    private companion object {
        /** Roughly a 250dp card at 2x. A larger bitmap risks TransactionTooLargeException. */
        const val TARGET_WIDTH = 480
        const val MAX_BYTES = 200 * 1024
    }
}