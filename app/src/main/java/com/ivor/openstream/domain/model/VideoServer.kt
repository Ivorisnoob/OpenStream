package com.ivor.openstream.domain.model

data class VideoServer(
    val id: String,
    val providerId: String,
    val providerName: String,
    val name: String,
    val url: String,
    val quality: StreamQuality,
    val audio: StreamAudio = StreamAudio.UNKNOWN,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<StreamSubtitle> = emptyList(),
    val isDownloadable: Boolean = true,
    /** Spoken language when the source says so (dub routes), e.g. "English" or "Hindi". */
    val audioLanguage: String? = null,
    val resolvedAt: Long = System.currentTimeMillis(),
    /** Container hint (for example `application/x-mpegURL`) when the URL doesn't say; skips sniffing. */
    val mimeType: String? = null,
    /** Intro/outro times the source itself provides; preferred over AniSkip. */
    val skipSegments: List<SkipSegment> = emptyList()
) {
    val isDub: Boolean
        get() = audio == StreamAudio.DUB

    val isHls: Boolean
        get() = mimeType.equals(HLS_MIME_TYPE, ignoreCase = true) ||
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

    val streamType: String
        get() = when {
            isHls -> "HLS"
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "DASH"
            url.substringBefore('?').endsWith(".mp4", ignoreCase = true) -> "MP4"
            else -> "STREAM"
        }
}

/** Media3's HLS MIME type (MimeTypes.APPLICATION_M3U8), without depending on Media3 here. */
const val HLS_MIME_TYPE = "application/x-mpegURL"
