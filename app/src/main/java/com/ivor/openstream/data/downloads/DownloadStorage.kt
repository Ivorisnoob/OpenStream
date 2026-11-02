package com.ivor.openstream.data.downloads

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Where downloads are kept: the app's folder on internal storage, or on a removable volume (an SD
 * card or USB drive) the user picked in Settings. Media3's download cache opens this folder once
 * per process, so a change takes effect after [moveTo] and an app restart.
 *
 * The cache's own index lives in the app database under the folder's id file, which moves with
 * the folder, so moved downloads open exactly as before.
 */
@Singleton
class DownloadStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) {
    /** A place downloads can live. [root] is this app's own folder on that volume. */
    data class Volume(val root: File, val removable: Boolean, val freeBytes: Long, val totalBytes: Long)

    /** Every mounted volume the app can write to, internal storage first. */
    fun volumes(): List<Volume> =
        context.getExternalFilesDirs(null).filterNotNull()
            .filter { runCatching { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.getOrDefault(false) }
            .map { dir ->
                Volume(
                    root = dir,
                    removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false),
                    freeBytes = dir.usableSpace,
                    totalBytes = dir.totalSpace
                )
            }
            .ifEmpty { listOf(Volume(primaryRoot(), removable = false, freeBytes = primaryRoot().usableSpace, totalBytes = primaryRoot().totalSpace)) }

    /**
     * The volume holding downloads: the one chosen in Settings while it is mounted, otherwise
     * internal storage (a removed SD card must not stop the app from starting).
     */
    fun currentRoot(): File {
        val chosen = prefs.getString(KEY_ROOT, null)?.let(::File) ?: return primaryRoot()
        return volumes().firstOrNull { it.root.absolutePath == chosen.absolutePath }?.root ?: primaryRoot()
    }

    /** True when the chosen volume is a removable one that is not mounted right now. */
    fun chosenVolumeMissing(): Boolean {
        val chosen = prefs.getString(KEY_ROOT, null) ?: return false
        return volumes().none { it.root.absolutePath == chosen }
    }

    fun downloadDirectory(): File = File(currentRoot(), DIRECTORY)

    /** Bytes the downloads take up now. Blocking. */
    fun downloadsSize(): Long = downloadDirectory().walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Moves every download to [target], reporting bytes copied out of the total as it goes. The
     * old copy is deleted only once the new one is complete and chosen, so a failure (or a full
     * card) leaves downloads where they were. The app has to restart afterwards. Blocking work on
     * the IO dispatcher; call with downloads paused.
     */
    suspend fun moveTo(target: Volume, onProgress: (copied: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val source = downloadDirectory()
        val destination = File(target.root, DIRECTORY)
        if (source.absolutePath == destination.absolutePath) return@withContext
        val files = if (source.exists()) source.walkTopDown().filter { it.isFile }.toList() else emptyList()
        val total = files.sumOf { it.length() }
        if (total + SPACE_MARGIN_BYTES > target.root.usableSpace) {
            throw IOException("Not enough space")
        }

        val staging = File(target.root, "$DIRECTORY.moving")
        staging.deleteRecursively()
        var copied = 0L
        try {
            for (file in files) {
                coroutineContext.ensureActive()
                val out = File(staging, file.relativeTo(source).path)
                out.parentFile?.mkdirs()
                file.inputStream().use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(copied, total)
                        }
                    }
                }
            }
            staging.mkdirs()
            // A leftover folder from an earlier move to this volume would mix two caches.
            destination.deleteRecursively()
            if (!staging.renameTo(destination)) throw IOException("Could not finish moving downloads")
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
        // Chosen synchronously: the restart that follows must open the new folder.
        val isPrimary = target.root.absolutePath == primaryRoot().absolutePath
        val saved = prefs.edit().apply {
            if (isPrimary) remove(KEY_ROOT) else putString(KEY_ROOT, target.root.absolutePath)
        }.commit()
        if (!saved) throw IOException("Could not save the new location")
        source.deleteRecursively()
        onProgress(total, total)
    }

    private fun primaryRoot(): File = context.getExternalFilesDir(null) ?: context.filesDir

    private companion object {
        const val DIRECTORY = "downloads"
        const val KEY_ROOT = "download_storage_root"
        const val COPY_BUFFER_BYTES = 256 * 1024
        /** Left free on the target so the move itself never fills a card to the last byte. */
        const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024
    }
}
