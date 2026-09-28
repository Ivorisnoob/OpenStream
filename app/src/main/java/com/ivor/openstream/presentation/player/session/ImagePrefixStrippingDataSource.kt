package com.ivor.openstream.presentation.player.session

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.Collections

/**
 * Some anime CDNs (megaplay's `tcdn` server) serve MPEG-TS segments disguised as images: a PNG
 * header (~250 bytes) sits before the first TS packet, more than ExoPlayer's TS sniffer skips.
 * This wrapper drops everything before the first run of TS sync bytes when a response starts
 * with an image signature. Anything else passes through untouched.
 *
 * Retried loads resume mid-segment at a position counted in stripped bytes, so the prefix found
 * for each URI is remembered and added back to later requests' positions.
 */
@UnstableApi
class ImagePrefixStrippingDataSource(private val upstream: DataSource) : DataSource {

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ImagePrefixStrippingDataSource(upstream.createDataSource())
    }

    private var pending: ByteArray? = null
    private var pendingOffset = 0

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        pending = null
        pendingOffset = 0
        val knownPrefix = prefixes[dataSpec.uri.toString()]
        if (knownPrefix != null && dataSpec.position > 0) {
            return upstream.open(dataSpec.buildUpon().setPosition(dataSpec.position + knownPrefix).build())
        }
        val length = upstream.open(dataSpec)
        if (dataSpec.position != 0L) return length

        val head = readUpTo(PROBE_BYTES)
        val prefix = if (head.isImage()) head.tsStart() else 0
        if (prefix > 0) prefixes[dataSpec.uri.toString()] = prefix
        pending = head
        pendingOffset = prefix
        return if (length == C.LENGTH_UNSET.toLong()) length else length - prefix
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        pending?.let { head ->
            val available = head.size - pendingOffset
            if (available > 0) {
                val count = minOf(available, length)
                System.arraycopy(head, pendingOffset, buffer, offset, count)
                pendingOffset += count
                return count
            }
            pending = null
        }
        return upstream.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        pending = null
        upstream.close()
    }

    private fun readUpTo(limit: Int): ByteArray {
        val bytes = ByteArray(limit)
        var filled = 0
        while (filled < limit) {
            val read = upstream.read(bytes, filled, limit - filled)
            if (read == C.RESULT_END_OF_INPUT) break
            filled += read
        }
        return if (filled == limit) bytes else bytes.copyOf(filled)
    }

    private fun ByteArray.isImage(): Boolean =
        size >= 4 && (
            (this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte() && this[2] == 'N'.code.toByte()) ||
                (this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()) ||
                (this[0] == 'G'.code.toByte() && this[1] == 'I'.code.toByte() && this[2] == 'F'.code.toByte())
            )

    /** Offset of the first of three consecutive TS packets, or 0 when there is none. */
    private fun ByteArray.tsStart(): Int {
        val last = size - TS_PACKET * 2
        for (i in 0 until last) {
            if (this[i] == SYNC && this[i + TS_PACKET] == SYNC && this[i + TS_PACKET * 2] == SYNC) return i
        }
        return 0
    }

    private companion object {
        const val PROBE_BYTES = 64 * 1024
        const val TS_PACKET = 188
        const val SYNC = 0x47.toByte()
        val prefixes: MutableMap<String, Int> = Collections.synchronizedMap(
            object : LinkedHashMap<String, Int>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > 512
            }
        )
    }
}
