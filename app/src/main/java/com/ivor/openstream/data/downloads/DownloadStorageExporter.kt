package com.ivor.openstream.data.downloads

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.repository.LocalMediaRepository
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.DownloadRequestHeaderStore
import com.ivor.openstream.data.streaming.ImagePrefixStrippingDataSource
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

sealed interface ExportState {
    data object Idle : ExportState
    data class Progress(val downloadId: String, val percent: Int) : ExportState
    data class Success(val downloadId: String, val title: String, val uri: Uri) : ExportState
    data class Failed(val downloadId: String, val message: String) : ExportState
}

@OptIn(UnstableApi::class)
@Singleton
class DownloadStorageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: Cache,
    private val headerStore: DownloadRequestHeaderStore,
    private val savedSubtitles: SavedSubtitleRepository,
    private val localMediaRepository: LocalMediaRepository
) {
    private val _exportStates = MutableStateFlow<Map<String, ExportState>>(emptyMap())
    val exportStates: StateFlow<Map<String, ExportState>> = _exportStates.asStateFlow()

    private val exportedIds = ConcurrentHashMap.newKeySet<String>()

    fun isExported(downloadId: String): Boolean = exportedIds.contains(downloadId)

    suspend fun exportToStorage(download: DownloadEntity): Result<Uri> = withContext(Dispatchers.IO) {
        val downloadId = download.downloadId
        try {
            updateState(downloadId, ExportState.Progress(downloadId, 0))

            val rawTitle = download.displayTitle.ifBlank { "Openstream" }
            val cleanFolderName = sanitizeFileName(rawTitle)

            val isMovie = download.mediaType == "movie"
            val baseFileName = if (isMovie) {
                if (download.year != null) "$cleanFolderName (${download.year})" else cleanFolderName
            } else {
                val sEp = String.format(Locale.US, "S%02dE%02d", download.season, download.episode)
                val epPart = if (!download.episodeTitle.isNullOrBlank()) " - ${sanitizeFileName(download.episodeTitle)}" else ""
                "$cleanFolderName - $sEp$epPart"
            }

            val fileName = "$baseFileName.mp4"
            val mimeType = "video/mp4"

            val targetOutput = createTargetFile(cleanFolderName, fileName, mimeType)
            val outputStream = targetOutput.outputStream
            val targetUri = targetOutput.uri

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(BROWSER_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)

            val cacheDataSource = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()

            val strippedDataSource = ImagePrefixStrippingDataSource(cacheDataSource)

            val isHls = download.uri.contains(".m3u8") || download.uri.contains("hls")

            if (isHls) {
                exportHlsStream(download, strippedDataSource, outputStream) { percent ->
                    updateState(downloadId, ExportState.Progress(downloadId, percent))
                }
            } else {
                exportProgressiveStream(download, strippedDataSource, outputStream) { percent ->
                    updateState(downloadId, ExportState.Progress(downloadId, percent))
                }
            }

            outputStream.flush()
            outputStream.close()
            targetOutput.finalizeWrite()

            exportSubtitles(download, cleanFolderName, baseFileName)

            localMediaRepository.importUris(listOf(targetUri))
            exportedIds.add(downloadId)

            updateState(downloadId, ExportState.Success(downloadId, download.displayTitle, targetUri))
            Result.success(targetUri)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed for ${download.downloadId}", e)
            val msg = e.message ?: "Failed to export download"
            updateState(download.downloadId, ExportState.Failed(download.downloadId, msg))
            Result.failure(e)
        }
    }

    private fun updateState(downloadId: String, state: ExportState) {
        _exportStates.value = _exportStates.value + (downloadId to state)
    }

    private fun exportProgressiveStream(
        download: DownloadEntity,
        dataSource: androidx.media3.datasource.DataSource,
        outputStream: OutputStream,
        onProgress: (Int) -> Unit
    ) {
        val dataSpec = DataSpec(Uri.parse(download.uri))
        dataSource.open(dataSpec)
        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = 0L
        val expectedLength = download.totalBytes.takeIf { it > 0 } ?: C.LENGTH_UNSET.toLong()
        var bytesRead: Int

        try {
            while (dataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != C.RESULT_END_OF_INPUT) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                if (expectedLength > 0) {
                    val percent = ((totalRead * 100) / expectedLength).toInt().coerceIn(0, 99)
                    onProgress(percent)
                }
            }
            onProgress(100)
        } finally {
            runCatching { dataSource.close() }
        }
    }

    private fun exportHlsStream(
        download: DownloadEntity,
        dataSource: androidx.media3.datasource.DataSource,
        outputStream: OutputStream,
        onProgress: (Int) -> Unit
    ) {
        val playlistBytes = ByteArrayOutputStream()
        val playlistSpec = DataSpec(Uri.parse(download.uri))
        try {
            dataSource.open(playlistSpec)
            val tempBuf = ByteArray(BUFFER_SIZE)
            var n: Int
            while (dataSource.read(tempBuf, 0, tempBuf.size).also { n = it } != C.RESULT_END_OF_INPUT) {
                playlistBytes.write(tempBuf, 0, n)
            }
        } finally {
            runCatching { dataSource.close() }
        }

        val playlistText = playlistBytes.toString(Charsets.UTF_8.name())
        val baseHttpUrl = download.uri.toHttpUrlOrNull()
        val lines = playlistText.lines().map(String::trim)

        val segments = mutableListOf<String>()

        for (line in lines) {
            if (line.startsWith("#EXT-X-MAP:")) {
                val uriMatch = Regex("URI=\"([^\"]+)\"").find(line)
                val uriPart = uriMatch?.groupValues?.get(1)
                if (!uriPart.isNullOrEmpty()) {
                    val resolved = baseHttpUrl?.resolve(uriPart)?.toString() ?: uriPart
                    segments.add(0, resolved)
                }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val resolved = baseHttpUrl?.resolve(line)?.toString() ?: line
                segments.add(resolved)
            }
        }

        if (segments.isEmpty()) {
            throw IOException("No media segments found in HLS playlist")
        }

        val totalSegments = segments.size
        val buffer = ByteArray(BUFFER_SIZE)

        for ((index, segmentUri) in segments.withIndex()) {
            val segmentSpec = DataSpec(Uri.parse(segmentUri))
            try {
                dataSource.open(segmentSpec)
                var bytesRead: Int
                while (dataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != C.RESULT_END_OF_INPUT) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error writing segment $segmentUri", e)
            } finally {
                runCatching { dataSource.close() }
            }

            val percent = (((index + 1) * 100) / totalSegments).coerceIn(0, 100)
            onProgress(percent)
        }
    }

    private suspend fun exportSubtitles(
        download: DownloadEntity,
        cleanFolderName: String,
        baseFileName: String
    ) {
        runCatching {
            val saved = savedSubtitles.observe(download.mediaType, download.tmdbId, download.season, download.episode).first()
            val subDir = File(File(context.filesDir, "subtitles"), "${download.mediaType}_${download.tmdbId}_${download.season}_${download.episode}")
            if (!subDir.exists() || !subDir.isDirectory) return@runCatching

            for (sub in saved) {
                val sourceFile = File(subDir, sub.fileName)
                if (sourceFile.exists()) {
                    val lang = sub.language?.takeIf { it.isNotEmpty() } ?: "und"
                    val ext = sourceFile.extension.ifEmpty { "vtt" }
                    val subFileName = "$baseFileName.$lang.$ext"
                    val subTarget = createTargetFile(cleanFolderName, subFileName, "text/vtt")
                    sourceFile.inputStream().use { input ->
                        subTarget.outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    subTarget.finalizeWrite()
                }
            }
        }.onFailure {
            Log.w(TAG, "Could not export subtitles for ${download.downloadId}", it)
        }
    }

    private class TargetFileResult(
        val uri: Uri,
        val outputStream: OutputStream,
        val finalizeWrite: () -> Unit
    )

    private fun createTargetFile(
        folderName: String,
        fileName: String,
        mimeType: String
    ): TargetFileResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Openstream/$folderName"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore entry for $fileName")

            val outputStream = context.contentResolver.openOutputStream(uri, "w")
                ?: throw IOException("Failed to open output stream for $uri")

            return TargetFileResult(uri, outputStream) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, completeValues, null, null)
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetDir = File(downloadsDir, "Openstream/$folderName")
            if (!targetDir.exists()) targetDir.mkdirs()

            val file = File(targetDir, fileName)
            val outputStream = FileOutputStream(file)
            val uri = Uri.fromFile(file)

            return TargetFileResult(uri, outputStream) {
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            }
        }
    }

    companion object {
        private const val TAG = "DownloadStorageExporter"
        private const val BUFFER_SIZE = 64 * 1024

        fun sanitizeFileName(name: String): String {
            return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim('.', ' ')
                .ifBlank { "Untitled" }
        }
    }
}
