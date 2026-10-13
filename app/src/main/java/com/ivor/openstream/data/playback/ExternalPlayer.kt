package com.ivor.openstream.data.playback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Metadata for a subtitle track forwarded to an external video player.
 */
data class ExternalSubtitle(
    val uri: Uri,
    val name: String,
    val isDefault: Boolean = false
)

/**
 * Playback request packaged for external media players (VLC, Just Player, MPV, Nova, MX Player).
 */
data class ExternalPlayerRequest(
    val mediaUri: Uri,
    val title: String,
    val positionMs: Long = 0L,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<ExternalSubtitle> = emptyList()
)

/**
 * Handles intent creation and coordination for launching streams in external Android players.
 *
 * Implements standard intent protocols accepted across the Android media ecosystem:
 * - Title: "title"
 * - Resume position: "position" (int ms) and "extra_position" (long ms)
 * - Result callback: "return_result" = true
 * - Headers: "android.media.intent.extra.HTTP_HEADERS" (Bundle), "headers" (String[]), "User-Agent"
 * - Subtitles: "subs" (Uri[]), "subs.name" (String[]), "subs.filename", "subs.enable", "subtitles_location"
 */
object ExternalPlayer {
    private const val TAG = "ExternalPlayer"

    // Recognized player package names
    const val PACKAGE_VLC = "org.videolan.vlc"
    const val PACKAGE_JUST_PLAYER = "com.brouken.player"
    const val PACKAGE_MPV = "is.xyz.mpv"
    const val PACKAGE_MX_PLAYER_FREE = "com.mxtech.videoplayer.ad"
    const val PACKAGE_MX_PLAYER_PRO = "com.mxtech.videoplayer.pro"
    const val PACKAGE_NOVA = "org.courville.nova"

    /**
     * Builds an [Intent.ACTION_VIEW] intent configured with all stream headers, subtitles,
     * and start positions.
     */
    fun createIntent(request: ExternalPlayerRequest, targetPackage: String? = null): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndTypeAndNormalize(request.mediaUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }

            // Title
            putExtra("title", request.title)

            // Start position & request result return
            val posInt = request.positionMs.coerceAtLeast(0L).toInt()
            putExtra("position", posInt)
            putExtra("extra_position", request.positionMs.coerceAtLeast(0L))
            putExtra("return_result", true)

            // Headers
            if (request.headers.isNotEmpty()) {
                val headerBundle = Bundle().apply {
                    request.headers.forEach { (key, value) -> putString(key, value) }
                }
                putExtra("android.media.intent.extra.HTTP_HEADERS", headerBundle)

                val headerStrings = request.headers.map { "${it.key}: ${it.value}" }.toTypedArray()
                putExtra("headers", headerStrings)

                request.headers["User-Agent"]?.let { userAgent ->
                    putExtra("User-Agent", userAgent)
                    putExtra("http-user-agent", userAgent)
                }
            }

            // Subtitles
            if (request.subtitles.isNotEmpty()) {
                val subUris = request.subtitles.map { it.uri }.toTypedArray()
                val subNames = request.subtitles.map { it.name }.toTypedArray()

                putExtra("subs", subUris)
                putExtra("subs.name", subNames)
                putExtra("subs.filename", subNames)

                val enabledSubs = request.subtitles.filter { it.isDefault }.map { it.uri }.toTypedArray()
                if (enabledSubs.isNotEmpty()) {
                    putExtra("subs.enable", enabledSubs)
                }

                // VLC single subtitle compatibility
                putExtra("subtitles_location", request.subtitles.first().uri)
            }
        }
    }

    /**
     * Builds a system chooser intent to allow the user to pick an installed video player.
     */
    fun createChooserIntent(request: ExternalPlayerRequest, chooserTitle: CharSequence): Intent {
        val baseIntent = createIntent(request)
        return Intent.createChooser(baseIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches the playback request using the system app chooser.
     * Returns true if an activity was found and started successfully.
     */
    fun launch(context: Context, request: ExternalPlayerRequest, chooserTitle: CharSequence): Boolean {
        return runCatching {
            val chooser = createChooserIntent(request, chooserTitle)
            context.startActivity(chooser)
            true
        }.onFailure { error ->
            Log.w(TAG, "Failed to launch external player chooser: ${error.message}")
        }.getOrDefault(false)
    }

    /**
     * Checks if a specific media player package is installed on the device.
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    /**
     * Extracts returned playback position (in milliseconds) from an external player's result intent.
     * Returns 0L or positive timestamp if available.
     */
    fun parseResultPosition(resultIntent: Intent?): Long {
        if (resultIntent == null) return 0L
        val posLong = resultIntent.getLongExtra("extra_position", -1L)
        if (posLong > 0L) return posLong
        val posInt = resultIntent.getIntExtra("position", -1)
        if (posInt > 0) return posInt.toLong()
        return 0L
    }
}
