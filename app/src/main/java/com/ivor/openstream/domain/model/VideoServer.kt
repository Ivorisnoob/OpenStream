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
    val resolvedAt: Long = System.currentTimeMillis()
) {
    val isDub: Boolean
        get() = audio == StreamAudio.DUB

    val streamType: String
        get() = when {
            url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) -> "HLS"
            url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> "DASH"
            url.substringBefore('?').endsWith(".mp4", ignoreCase = true) -> "MP4"
            else -> "STREAM"
        }
}
