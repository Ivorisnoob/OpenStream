package com.ivor.openstream.data.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi

private val dolbyVisionProfiles = setOf("dvhe", "dvh1", "dvav", "dva1", "dav1")

@UnstableApi
internal fun Format.requiresHdrOutput(): Boolean =
    sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION ||
        colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
        colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG ||
        codecs?.split(',')?.any { codec ->
            codec.trim().substringBefore('.').lowercase() in dolbyVisionProfiles
        } == true

/** An empty selection does not establish a range. Mixed selections need the decoder's format. */
@UnstableApi
internal fun Tracks.selectedVideoRanges(): Set<Boolean> = buildSet {
    for (group in groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) add(group.getTrackFormat(index).requiresHdrOutput())
        }
    }
}
