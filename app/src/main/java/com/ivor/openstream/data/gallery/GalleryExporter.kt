package com.ivor.openstream.data.gallery

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.streaming.ImagePrefixStrippingDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Where a download stands with the gallery. */
sealed interface GalleryExport {
    data object Queued : GalleryExport

    /** [percent] is null while Media3 cannot tell yet. */
    data class Running(val percent: Int?) : GalleryExport

    data class Done(val uri: String) : GalleryExport

    data class Failed(val message: String) : GalleryExport
}

/**
 * Copies finished downloads into the gallery as MP4 (`Movies/OpenStream`). Downloads are kept as
 * Media3 cache pieces (HLS segments), which no other app can read, so each one is repackaged with
 * Media3 Transformer reading from the same cache the offline player uses. Nothing is re-encoded
 * when the codecs fit MP4 (H.264/H.265 with AAC), so it is fast and keeps the quality.
 *
 * The download is never touched: the cache is opened read-only (no write sink, no eviction), the
 * MP4 is built in a temp file under `cacheDir`, and only that temp file and an unfinished gallery
 * entry are deleted when an export fails or is cancelled. The gallery copy is a separate file that
 * survives deleting the download. Exports run one at a time while the app process is alive.
 */
@OptIn(UnstableApi::class)
@Singleton
class GalleryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media3: DownloadManager,
    private val cache: Cache,
    /** Network with the download's headers; only used for a piece the cache is missing. */
    private val upstreamFactory: DataSource.Factory,
    private val preferences: SharedPreferences
) {
    /**
     * Reads the download cache without ever writing to it. Built here rather than reusing a shared
     * factory so a change elsewhere can never make an export modify or evict a download.
     */
    private val readOnlyCache: DataSource.Factory by lazy {
        ImagePrefixStrippingDataSource.Factory(
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(null)
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queue = Channel<DownloadEntity>(Channel.UNLIMITED)
    private val _exports = MutableStateFlow<Map<String, GalleryExport>>(emptyMap())

    /** By download id. */
    val exports: StateFlow<Map<String, GalleryExport>> = _exports.asStateFlow()

    /** Android 9 and older need the storage permission to write to the shared Movies folder. */
    val needsStoragePermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    init {
        scope.launch { restoreFinished() }
        scope.launch {
            for (download in queue) runExport(download)
        }
    }

    fun export(download: DownloadEntity) {
        when (_exports.value[download.downloadId]) {
            GalleryExport.Queued, is GalleryExport.Running -> return
            else -> Unit
        }
        _exports.update { it + (download.downloadId to GalleryExport.Queued) }
        queue.trySend(download)
    }

    /** Forgets a failed attempt so the row stops showing the error. */
    fun dismiss(downloadId: String) {
        if (_exports.value[downloadId] is GalleryExport.Failed) _exports.update { it - downloadId }
    }

    private suspend fun runExport(download: DownloadEntity) {
        val id = download.downloadId
        val temp = File(context.cacheDir, "gallery-export/$id.mp4")
        try {
            val mediaItem = withContext(Dispatchers.IO) { media3.downloadIndex.getDownload(id) }
                ?.request?.toMediaItem()
                ?: throw IOException("Only downloads made by this version of the app can be saved to the gallery")
            withContext(Dispatchers.IO) {
                temp.parentFile?.mkdirs()
                temp.delete()
                // The MP4 is written next to the app first, then copied out: about twice its size.
                val needed = download.sizeBytes * 2 + SPACE_MARGIN_BYTES
                if (download.sizeBytes > 0 && temp.parentFile!!.usableSpace < needed) {
                    throw IOException("Not enough free space")
                }
            }
            _exports.update { it + (id to GalleryExport.Running(null)) }
            repackage(mediaItem, temp, id)
            val uri = withContext(Dispatchers.IO) { publish(temp, download) }
            preferences.edit().putString(KEY_PREFIX + id, uri).apply()
            _exports.update { it + (id to GalleryExport.Done(uri)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Gallery export of $id failed", error)
            _exports.update { it + (id to GalleryExport.Failed(error.userMessage())) }
        } finally {
            withContext(Dispatchers.IO) { temp.delete() }
        }
    }

    /** Runs on the main thread: Transformer must be started, polled and cancelled from its looper. */
    private suspend fun repackage(mediaItem: MediaItem, output: File, id: String) = coroutineScope {
        val result = CompletableDeferred<Unit>()
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(readOnlyCache)
        val transformer = Transformer.Builder(context)
            .setAssetLoaderFactory(
                DefaultAssetLoaderFactory(
                    context,
                    DefaultDecoderFactory(context),
                    /* forceInterpretHdrVideoAsSdr = */ false,
                    Clock.DEFAULT,
                    mediaSourceFactory,
                    DataSourceBitmapLoader(context)
                )
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    result.complete(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    result.completeExceptionally(exportException)
                }
            })
            .build()
        transformer.start(mediaItem, output.absolutePath)
        val progress = launch {
            val holder = ProgressHolder()
            while (true) {
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exports.update { it + (id to GalleryExport.Running(holder.progress.coerceIn(0, 100))) }
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
        try {
            result.await()
        } catch (cancelled: CancellationException) {
            transformer.cancel()
            throw cancelled
        } finally {
            progress.cancel()
        }
    }

    /** Copies the MP4 into Movies/OpenStream and returns its content URI (a file URI before Android 10). */
    private fun publish(file: File, download: DownloadEntity): String {
        val name = fileName(download)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.TITLE, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$FOLDER")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("The gallery refused the file")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: throw IOException("Could not write to the gallery")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) {
                resolver.delete(uri, null, null)
                throw error
            }
            return uri.toString()
        }
        @Suppress("DEPRECATION")
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create the Movies folder")
        var target = File(folder, "$name.mp4")
        var copy = 1
        while (target.exists()) target = File(folder, "$name (${copy++}).mp4")
        file.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf("video/mp4"), null)
        return Uri.fromFile(target).toString()
    }

    /** Marks earlier exports that are still in the gallery; the user may have deleted them since. */
    private suspend fun restoreFinished() {
        val saved = withContext(Dispatchers.IO) {
            preferences.all
                .filterKeys { it.startsWith(KEY_PREFIX) }
                .mapNotNull { (key, value) ->
                    val uri = value as? String ?: return@mapNotNull null
                    val id = key.removePrefix(KEY_PREFIX)
                    if (stillExists(uri)) {
                        id to GalleryExport.Done(uri)
                    } else {
                        preferences.edit().remove(key).apply()
                        null
                    }
                }
                .toMap()
        }
        // Anything queued meanwhile wins over the stored state.
        _exports.update { saved + it }
    }

    private fun stillExists(uri: String): Boolean = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") return@runCatching File(parsed.path.orEmpty()).exists()
        context.contentResolver.query(parsed, arrayOf(MediaStore.Video.Media._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun fileName(download: DownloadEntity): String {
        val title = if (download.mediaType == "movie") {
            download.displayTitle + (download.year?.let { " ($it)" } ?: "")
        } else {
            String.format(Locale.US, "%s S%02dE%02d", download.displayTitle, download.season, download.episode) +
                (download.episodeTitle?.let { " - $it" } ?: "")
        }
        return title.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
    }

    private fun Exception.userMessage(): String = when (this) {
        is ExportException -> when (errorCode) {
            ExportException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            ExportException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Some downloaded parts are missing"
            ExportException.ERROR_CODE_MUXING_FAILED,
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED -> "This video's format can't be saved as MP4"
            else -> "Could not save to the gallery"
        }
        else -> message ?: "Could not save to the gallery"
    }

    private companion object {
        const val TAG = "GalleryExport"
        const val FOLDER = "OpenStream"
        const val KEY_PREFIX = "gallery_export_"
        const val PROGRESS_INTERVAL_MS = 500L
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
    }
}
