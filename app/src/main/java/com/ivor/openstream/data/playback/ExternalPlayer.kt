package com.ivor.openstream.data.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/** A subtitle file handed to another player along with the stream. */
data class ExternalSubtitle(
    val uri: Uri,
    val name: String
)

/** What another player app needs to play the current stream: where, from when, and how. */
data class ExternalPlayerRequest(
    val mediaUri: Uri,
    val title: String,
    val positionMs: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<ExternalSubtitle> = emptyList()
)

/**
 * Hands a stream to another player app through the system chooser.
 *
 * The extras follow MX Player's public intent API, which Just Player, mpv and Nova also read:
 * `title`, `position` (int ms), `headers` (a flat String[] of name, value, name, value...),
 * `subs` / `subs.name` (Uri[] / String[]). VLC reads `title`, `position` and
 * `subtitles_location`. None of this is a formal contract; a player that ignores an extra simply
 * plays without it (for example without the Referer some hosts need).
 */
object ExternalPlayer {
    private const val TAG = "ExternalPlayer"

    private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")

    /** Other apps can only open plain subtitle files on the web, not zips or this app's files. */
    fun canOpenSubtitle(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        return extension in SUBTITLE_EXTENSIONS
    }

    fun createIntent(request: ExternalPlayerRequest): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndTypeAndNormalize(request.mediaUri, "video/*")
            putExtra("title", request.title)
            val position = request.positionMs.coerceIn(0L, Int.MAX_VALUE.toLong())
            putExtra("position", position.toInt())
            putExtra("extra_position", position)

            if (request.headers.isNotEmpty()) {
                putExtra("headers", request.headers.flatMap { (name, value) -> listOf(name, value) }.toTypedArray())
                putExtra(
                    "android.media.intent.extra.HTTP_HEADERS",
                    Bundle().apply { request.headers.forEach { (name, value) -> putString(name, value) } }
                )
            }

            if (request.subtitles.isNotEmpty()) {
                putExtra("subs", request.subtitles.map { it.uri }.toTypedArray())
                putExtra("subs.name", request.subtitles.map { it.name }.toTypedArray())
                putExtra("subtitles_location", request.subtitles.first().uri.toString())
            }
        }

    /** True when the chooser opened; the user may still back out of it. */
    fun launch(context: Context, request: ExternalPlayerRequest, chooserTitle: CharSequence): Boolean =
        runCatching {
            val chooser = Intent.createChooser(createIntent(request), chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not open the external player chooser: ${error.message}")
        }.getOrDefault(false)
}
