package com.ivor.openstream.data.repository

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.ivor.openstream.data.local.dao.LocalMediaDao
import com.ivor.openstream.data.local.entity.LocalMediaEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LocalMediaDao
) {
    fun getAllLocalMedia(): Flow<List<LocalMediaEntity>> = dao.getAllLocalMedia()

    suspend fun getLocalMediaById(id: Long): LocalMediaEntity? = dao.getLocalMediaById(id)

    suspend fun getLocalMediaByUri(uri: String): LocalMediaEntity? = dao.getLocalMediaByUri(uri)

    suspend fun insert(entity: LocalMediaEntity): Long = dao.insert(entity)

    suspend fun importUris(uris: List<Uri>): List<LocalMediaEntity> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<LocalMediaEntity>()
        for (uri in uris) {
            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            var displayName: String? = null
            var sizeBytes: Long = 0L

            if (uri.scheme == "content") {
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = File(path)
                    displayName = file.name
                    sizeBytes = file.length()
                }
            }

            if (displayName.isNullOrBlank()) {
                displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
            }

            var durationMs = 0L
            var mimeType: String? = context.contentResolver.getType(uri)

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (mimeType == null) {
                    mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { retriever.release() }
            }

            val folderName = extractFolderName(uri)
            val uriString = uri.toString()
            val existing = dao.getLocalMediaByUri(uriString)

            val entity = if (existing != null) {
                existing.copy(
                    displayName = displayName ?: existing.displayName,
                    sizeBytes = if (sizeBytes > 0) sizeBytes else existing.sizeBytes,
                    durationMs = if (durationMs > 0) durationMs else existing.durationMs,
                    mimeType = mimeType ?: existing.mimeType,
                    folderName = folderName ?: existing.folderName
                ).also { dao.update(it) }
            } else {
                val newEntity = LocalMediaEntity(
                    uri = uriString,
                    displayName = displayName ?: "Video",
                    sizeBytes = sizeBytes,
                    durationMs = durationMs,
                    dateAdded = System.currentTimeMillis(),
                    dateModified = System.currentTimeMillis(),
                    mimeType = mimeType,
                    folderName = folderName
                )
                val id = dao.insert(newEntity)
                newEntity.copy(id = id)
            }
            imported.add(entity)
        }
        imported
    }

    private fun extractFolderName(uri: Uri): String? {
        return try {
            val path = uri.path ?: return null
            val segments = path.split('/').filter { it.isNotEmpty() }
            if (segments.size >= 2) segments[segments.size - 2] else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun deleteWithFile(id: Long) = withContext(Dispatchers.IO) {
        val item = dao.getLocalMediaById(id) ?: return@withContext
        val uri = Uri.parse(item.uri)
        if (uri.scheme == "file") {
            uri.path?.let { runCatching { File(it).delete() } }
        } else if (uri.scheme == "content") {
            runCatching {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } else {
                    context.contentResolver.delete(uri, null, null)
                }
            }
        }
        dao.deleteById(id)
    }

    suspend fun deleteAll() = dao.deleteAll()
}
