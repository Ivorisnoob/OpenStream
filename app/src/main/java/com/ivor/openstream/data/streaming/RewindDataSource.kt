package com.ivor.openstream.data.streaming

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.security.MessageDigest

/** Routes only recognizable media payloads through the rewind buffer. */
@UnstableApi
internal class RewindDataSource(
    private val network: DataSource,
    private val buffer: DataSource,
    private val sessionId: () -> String
) : DataSource {
    private var active: DataSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val extension = dataSpec.uri.path?.substringAfterLast('.', "")?.lowercase()
        if (extension !in mediaExtensions || dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET ||
            dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP)
        ) {
            active = network
            return network.open(dataSpec)
        }

        // Include the URI even when the source supplies a cache key. Do not write credentials
        // or signed URLs into the on-disk key index.
        val identity = buildString {
            append(sessionId())
            append('\u0000').append(dataSpec.uri)
            append('\u0000').append(dataSpec.key)
            for ((name, value) in dataSpec.httpRequestHeaders.toSortedMap()) {
                append('\u0000').append(name).append('\u0000').append(value)
            }
        }
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
        active = buffer
        return buffer.open(
            dataSpec.buildUpon()
                .setKey(key)
                .setFlags(dataSpec.flags or DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                .build()
        )
    }

    override fun addTransferListener(transferListener: TransferListener) {
        network.addTransferListener(transferListener)
        buffer.addTransferListener(transferListener)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active).read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()

    override fun close() {
        val source = active
        active = null
        source?.close()
    }

    private companion object {
        val mediaExtensions = setOf("mp4", "m4v", "mkv", "webm", "ts", "m4s", "cmfv", "cmfa", "aac")
    }
}
