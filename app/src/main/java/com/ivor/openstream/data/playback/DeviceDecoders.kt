package com.ivor.openstream.data.playback

import android.media.MediaCodecList
import androidx.media3.common.MimeTypes
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which video codecs this device can actually decode.
 *
 * Several anime and web sources ship H.265 (HEVC) because it halves their bandwidth. Newer phones
 * decode it in hardware; older ones either fail outright or fall back to a slow software decoder,
 * which looks to the user like "a black screen with sound". Asking [MediaCodecList] the same way
 * the player does lets the track selector prefer H.264 and the player pick a source that will play.
 */
@Singleton
class DeviceDecoders @Inject constructor() {

    private val cache = ConcurrentHashMap<String, Boolean>()

    fun canDecode(mimeType: String): Boolean = cache.getOrPut(mimeType) { probe(mimeType) }

    /** True when a hardware HEVC decoder exists, so H.265 sources are safe to prefer. */
    val supportsHevc: Boolean get() = canDecode(MimeTypes.VIDEO_H265)

    private fun probe(mimeType: String): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }.getOrDefault(true)
}
