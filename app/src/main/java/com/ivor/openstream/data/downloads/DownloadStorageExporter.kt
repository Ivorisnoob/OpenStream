package com.ivor.openstream.data.downloads

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.repository.LocalMediaRepository
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.ImagePrefixStrippingDataSource
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

sealed interface ExportState {
    data object Idle : ExportState
    data class Progress(val downloadId: String, val percent: Int) : ExportState
    data class Success(val downloadId: String, val title: String, val uri: Uri) : ExportState
    data class Failed(val downloadId: String, val message: String) : ExportState
}

/** An export that can't work for this download, with a message the user can act on. */
class ExportException(message: String) : IOException(message)

/**
 * Copies a finished download out of the app's download cache into the public Downloads/Openstream
 * folder as one video file, with its saved subtitles next to it.
 *
 * Progressive downloads are copied as they are. HLS downloads are joined segment by segment: TS
 * segments become a `.ts` file, fMP4 segments (with an init section) a `.mp4`. Downloads that
 * can't be joined into one playable file are refused with a reason instead of producing a broken
 * file: encrypted segments, and master playlists with audio in separate renditions. Segments
 * missing from the cache are fetched again with the headers the download was made with. A failed
 * export removes its partial file.
 */
@OptIn(UnstableApi::class)
@Singleton
class DownloadStorageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: Cache,
    private val json: Json,
    private val savedSubtitles: SavedSubtitleRepository,
    private val localMediaRepository: LocalMediaRepository
) {
    private val _exportStates = MutableStateFlow<Map<String, ExportState>>(emptyMap())
    val exportStates: StateFlow<Map<String, ExportState>> = _exportStates.asStateFlow()

    /** Downloads being exported right now; a second request for one of them is ignored. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Android 9 and older need the storage permission to write to the public Downloads folder. */
    fun needsStoragePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    suspend fun exportToStorage(download: DownloadEntity): Result<Uri> {
        val downloadId = download.downloadId
        if (!inFlight.add(downloadId)) {
            return Result.failure(ExportException(context.getString(R.string.dl_save_in_progress)))
        }
        try {
            return withContext(Dispatchers.IO) { export(download) }
        } finally {
            inFlight.remove(downloadId)
        }
    }

    private suspend fun export(download: DownloadEntity): Result<Uri> {
        val downloadId = download.downloadId
        var target: TargetFile? = null
        return try {
            if (needsStoragePermission()) throw ExportException(context.getString(R.string.dl_storage_permission_needed))
            updateState(downloadId, ExportState.Progress(downloadId, 0))
            val dataSource = dataSourceFor(download)
            val plan = planExport(download, dataSource)

            val folder = sanitizeFileName(download.displayTitle.ifBlank { "Openstream" })
            val baseName = baseFileName(download, folder)
            target = createTargetFile(folder, "$baseName.${plan.extension}", plan.mimeType)
            target.outputStream.use { output ->
                when (plan) {
                    is ExportPlan.Progressive -> copyProgressive(download, dataSource, output) { percent ->
                        updateState(downloadId, ExportState.Progress(downloadId, percent))
                    }
                    is ExportPlan.Segments -> copySegments(plan, dataSource, output) { percent ->
                        updateState(downloadId, ExportState.Progress(downloadId, percent))
                    }
                }
            }
            target.finish()

            exportSubtitles(download, folder, baseName)
            localMediaRepository.importUris(listOf(target.uri))
            updateState(downloadId, ExportState.Success(downloadId, download.displayTitle, target.uri))
            Result.success(target.uri)
        } catch (error: Exception) {
            target?.let { runCatching { it.discard() } }
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(TAG, "Export failed for $downloadId", error)
            val message = (error as? ExportException)?.message ?: context.getString(R.string.dl_save_to_storage_failed)
            updateState(downloadId, ExportState.Failed(downloadId, message))
            Result.failure(error)
        }
    }

    private fun updateState(downloadId: String, state: ExportState) {
        _exportStates.update { it + (downloadId to state) }
    }

    // region Reading the download

    /** The download cache first; anything missing is fetched with the download's own headers. */
    private fun dataSourceFor(download: DownloadEntity): DataSource {
        val headers = download.requestHeadersJson
            ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
            .orEmpty()
        val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: BROWSER_USER_AGENT)
            .setDefaultRequestProperties(headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) })
            .setAllowCrossProtocolRedirects(true)
        val cached = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()
        return ImagePrefixStrippingDataSource(cached)
    }

    private sealed interface ExportPlan {
        val extension: String
        val mimeType: String

        data class Progressive(override val extension: String, override val mimeType: String) : ExportPlan

        data class Segments(
            val parts: List<DataSpec>,
            override val extension: String,
            override val mimeType: String
        ) : ExportPlan
    }

    private fun planExport(download: DownloadEntity, dataSource: DataSource): ExportPlan {
        val playlist = readPlaylistOrNull(dataSource, download.uri)
            ?: return progressivePlan(download.uri)
        return segmentsPlan(dataSource, download.uri, playlist, followedVariant = false)
    }

    /** The playlist text when [url] is an HLS playlist, or null for a plain video file. */
    private fun readPlaylistOrNull(dataSource: DataSource, url: String): String? {
        val output = ByteArrayOutputStream()
        try {
            dataSource.open(DataSpec(Uri.parse(url)))
            val buffer = ByteArray(BUFFER_SIZE)
            var checked = false
            while (true) {
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, read)
                // A video file is recognised from its first bytes, without reading it all.
                if (!checked && output.size() >= PLAYLIST_MAGIC.size) {
                    if (!output.toByteArray().copyOf(PLAYLIST_MAGIC.size).contentEquals(PLAYLIST_MAGIC)) return null
                    checked = true
                }
                if (output.size() > MAX_PLAYLIST_BYTES) throw ExportException(context.getString(R.string.dl_save_unsupported))
            }
        } finally {
            runCatching { dataSource.close() }
        }
        return output.toString(Charsets.UTF_8.name()).takeIf { it.startsWith("#EXTM3U") }
    }

    private fun segmentsPlan(dataSource: DataSource, playlistUrl: String, text: String, followedVariant: Boolean): ExportPlan {
        val base = playlistUrl.toHttpUrlOrNull()
        fun resolve(reference: String) = base?.resolve(reference)?.toString() ?: reference
        val lines = text.lines().map(String::trim).filter { it.isNotEmpty() }

        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            // A master playlist: one rendition plays on its own only if audio isn't separate.
            val separateAudio = lines.any { it.startsWith("#EXT-X-MEDIA") && "TYPE=AUDIO" in it && "URI=" in it }
            if (separateAudio || followedVariant) throw ExportException(context.getString(R.string.dl_save_unsupported))
            val variant = lines.withIndex()
                .filter { it.value.startsWith("#EXT-X-STREAM-INF") }
                .mapNotNull { (index, info) ->
                    val uri = lines.getOrNull(index + 1)?.takeUnless { it.startsWith("#") } ?: return@mapNotNull null
                    val bandwidth = Regex("BANDWIDTH=(\\d+)").find(info)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    bandwidth to resolve(uri)
                }
                .maxByOrNull { it.first }
                ?.second
                ?: throw ExportException(context.getString(R.string.dl_save_unsupported))
            val variantText = readPlaylistOrNull(dataSource, variant)
                ?: throw ExportException(context.getString(R.string.dl_save_unsupported))
            return segmentsPlan(dataSource, variant, variantText, followedVariant = true)
        }

        val encrypted = lines.any { line ->
            line.startsWith("#EXT-X-KEY") && !line.contains("METHOD=NONE")
        }
        if (encrypted) throw ExportException(context.getString(R.string.dl_save_protected))

        val parts = mutableListOf<DataSpec>()
        var fragmented = false
        var pendingRange: Pair<Long, Long?>? = null
        val nextOffset = mutableMapOf<String, Long>()
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MAP") -> {
                    fragmented = true
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: continue
                    val range = Regex("BYTERANGE=\"(\\d+)(?:@(\\d+))?\"").find(line)
                    val spec = if (range != null) {
                        DataSpec.Builder().setUri(resolve(uri))
                            .setPosition(range.groupValues[2].toLongOrNull() ?: 0L)
                            .setLength(range.groupValues[1].toLong())
                            .build()
                    } else {
                        DataSpec(Uri.parse(resolve(uri)))
                    }
                    parts += spec
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    val value = line.substringAfter(':')
                    pendingRange = value.substringBefore('@').toLong() to value.substringAfter('@', "").toLongOrNull()
                }
                !line.startsWith("#") -> {
                    val url = resolve(line)
                    val range = pendingRange
                    pendingRange = null
                    parts += if (range == null) {
                        DataSpec(Uri.parse(url))
                    } else {
                        val position = range.second ?: nextOffset[url] ?: 0L
                        nextOffset[url] = position + range.first
                        DataSpec.Builder().setUri(url).setPosition(position).setLength(range.first).build()
                    }
                }
            }
        }
        if (parts.isEmpty()) throw ExportException(context.getString(R.string.dl_save_unsupported))
        return if (fragmented) {
            ExportPlan.Segments(parts, "mp4", "video/mp4")
        } else {
            ExportPlan.Segments(parts, "ts", "video/mp2t")
        }
    }

    private fun progressivePlan(url: String): ExportPlan.Progressive {
        val extension = Uri.parse(url).lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.US)
            ?.takeIf { it in VIDEO_MIME_TYPES }
            ?: "mp4"
        return ExportPlan.Progressive(extension, VIDEO_MIME_TYPES.getValue(extension))
    }

    private suspend fun copyProgressive(
        download: DownloadEntity,
        dataSource: DataSource,
        output: OutputStream,
        onProgress: (Int) -> Unit
    ) {
        val expected = download.totalBytes.takeIf { it > 0 }
        var copied = 0L
        copy(dataSource, DataSpec(Uri.parse(download.uri)), output) { read ->
            copied += read
            if (expected != null) onProgress(((copied * 100) / expected).toInt().coerceIn(0, 99))
        }
        onProgress(100)
    }

    private suspend fun copySegments(
        plan: ExportPlan.Segments,
        dataSource: DataSource,
        output: OutputStream,
        onProgress: (Int) -> Unit
    ) {
        plan.parts.forEachIndexed { index, spec ->
            // A missing segment would leave a gap; the whole export fails instead.
            copy(dataSource, spec, output) {}
            onProgress(((index + 1) * 100) / plan.parts.size)
        }
    }

    private suspend fun copy(dataSource: DataSource, spec: DataSpec, output: OutputStream, onRead: (Int) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            dataSource.open(spec)
            while (true) {
                coroutineContext.ensureActive()
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                output.write(buffer, 0, read)
                onRead(read)
            }
        } finally {
            runCatching { dataSource.close() }
        }
    }

    // endregion

    private suspend fun exportSubtitles(download: DownloadEntity, folder: String, baseName: String) {
        runCatching {
            val saved = savedSubtitles.observe(download.mediaType, download.tmdbId, download.season, download.episode).first()
            saved.forEachIndexed { index, subtitle ->
                val source = savedSubtitles.fileFor(download.mediaType, download.tmdbId, download.season, download.episode, subtitle)
                if (!source.exists()) return@forEachIndexed
                val language = subtitle.language?.takeIf { it.isNotBlank() } ?: "und"
                val extension = source.extension.lowercase(Locale.US).ifEmpty { "srt" }
                // Two subtitles in one language keep apart instead of overwriting each other.
                val suffix = if (saved.count { (it.language ?: "und") == language } > 1) ".${index + 1}" else ""
                val target = createTargetFile(folder, "$baseName.$language$suffix.$extension", SUBTITLE_MIME_TYPES[extension] ?: "text/plain")
                try {
                    source.inputStream().use { input -> target.outputStream.use { input.copyTo(it) } }
                    target.finish()
                } catch (error: Exception) {
                    runCatching { target.discard() }
                    throw error
                }
            }
        }.onFailure { Log.w(TAG, "Could not save subtitles for ${download.downloadId}", it) }
    }

    // region Writing the file

    private class TargetFile(
        val uri: Uri,
        val outputStream: OutputStream,
        val finish: () -> Unit,
        val discard: () -> Unit
    )

    private fun createTargetFile(folder: String, fileName: String, mimeType: String): TargetFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Openstream/$folder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create $fileName in Downloads")
            val output = try {
                resolver.openOutputStream(uri, "w") ?: throw IOException("Could not open $uri")
            } catch (error: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
            return TargetFile(
                uri = uri,
                outputStream = output,
                finish = {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                },
                discard = {
                    runCatching { output.close() }
                    resolver.delete(uri, null, null)
                }
            )
        }
        @Suppress("DEPRECATION")
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Openstream/$folder")
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create $directory")
        val file = File(directory, fileName)
        val output = FileOutputStream(file)
        return TargetFile(
            uri = Uri.fromFile(file),
            outputStream = output,
            finish = { MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null) },
            discard = {
                runCatching { output.close() }
                file.delete()
            }
        )
    }

    // endregion

    companion object {
        private const val TAG = "DownloadStorageExporter"
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_PLAYLIST_BYTES = 8 * 1024 * 1024
        private val PLAYLIST_MAGIC = "#EXTM3U".toByteArray(Charsets.US_ASCII)

        private val VIDEO_MIME_TYPES = mapOf(
            "mp4" to "video/mp4",
            "m4v" to "video/mp4",
            "mkv" to "video/x-matroska",
            "webm" to "video/webm",
            "ts" to "video/mp2t"
        )

        private val SUBTITLE_MIME_TYPES = mapOf(
            "srt" to "application/x-subrip",
            "vtt" to "text/vtt",
            "ass" to "text/x-ssa",
            "ssa" to "text/x-ssa"
        )

        /** "Show (2020)" for a movie, "Show - S01E02 - Episode name" for an episode. */
        fun baseFileName(download: DownloadEntity, folder: String): String =
            if (download.mediaType == "movie") {
                if (download.year != null) "$folder (${download.year})" else folder
            } else {
                val code = String.format(Locale.US, "S%02dE%02d", download.season, download.episode)
                val name = download.episodeTitle?.takeIf { it.isNotBlank() }?.let { " - ${sanitizeFileName(it)}" }.orEmpty()
                "$folder - $code$name"
            }

        fun sanitizeFileName(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim('.', ' ')
                .take(120)
                .ifBlank { "Untitled" }
    }
}
