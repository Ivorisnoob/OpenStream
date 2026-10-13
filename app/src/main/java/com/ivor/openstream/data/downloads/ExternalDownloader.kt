package com.ivor.openstream.data.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Parameters for handing off a video stream to an external download manager.
 */
data class ExternalDownloadInfo(
    val url: String,
    val title: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Coordinates handoffs to external mobile download managers (1DM, ADM) and CLI tools (aria2, curl).
 */
object ExternalDownloader {
    private const val TAG = "ExternalDownloader"

    const val PACKAGE_1DM = "idm.internet.download.manager"
    const val PACKAGE_1DM_PLUS = "idm.internet.download.manager.plus"
    const val PACKAGE_ADM = "com.dv.adm"

    /**
     * Checks if 1DM or 1DM+ is installed.
     */
    fun is1DMInstalled(context: Context): Boolean =
        isPackageInstalled(context, PACKAGE_1DM) || isPackageInstalled(context, PACKAGE_1DM_PLUS)

    /**
     * Checks if ADM (Advanced Download Manager) is installed.
     */
    fun isADMInstalled(context: Context): Boolean =
        isPackageInstalled(context, PACKAGE_ADM)

    /**
     * Launches 1DM or 1DM+ with the download URL, headers, and media title.
     */
    fun launch1DM(context: Context, info: ExternalDownloadInfo): Boolean {
        val targetPackage = when {
            isPackageInstalled(context, PACKAGE_1DM_PLUS) -> PACKAGE_1DM_PLUS
            isPackageInstalled(context, PACKAGE_1DM) -> PACKAGE_1DM
            else -> null
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(info.url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val headersBundle = Bundle().apply {
                info.headers.forEach { (k, v) -> putString(k, v) }
            }
            putExtra("extra_headers", headersBundle)
            putExtra("name", sanitizeFilename(info.title))
            putExtra("secure_uri", true)

            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }

        return runCatching {
            if (targetPackage != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(Intent.createChooser(intent, "Download with 1DM").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            true
        }.onFailure { Log.w(TAG, "Failed to launch 1DM: ${it.message}") }.getOrDefault(false)
    }

    /**
     * Launches ADM (Advanced Download Manager) with the download URL, headers, and media title.
     */
    fun launchADM(context: Context, info: ExternalDownloadInfo): Boolean {
        val headersString = info.headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(info.url)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("headers_string", headersString)
            putExtra("com.dv.adm.extra.TITLE", sanitizeFilename(info.title))
            if (isPackageInstalled(context, PACKAGE_ADM)) {
                setPackage(PACKAGE_ADM)
            }
        }

        return runCatching {
            context.startActivity(intent)
            true
        }.onFailure { Log.w(TAG, "Failed to launch ADM: ${it.message}") }.getOrDefault(false)
    }

    /**
     * Formats an executable aria2c command containing all necessary headers and output name.
     */
    fun formatAria2Command(info: ExternalDownloadInfo): String {
        val filename = sanitizeFilename(info.title).ifBlank { "video" }
        val headerArgs = info.headers.entries.joinToString(" ") { (key, value) ->
            "--header=\"$key: $value\""
        }
        return if (headerArgs.isNotBlank()) {
            "aria2c \"${info.url}\" $headerArgs --out=\"$filename.mp4\" -s16 -x16 -k1M"
        } else {
            "aria2c \"${info.url}\" --out=\"$filename.mp4\" -s16 -x16 -k1M"
        }
    }

    /**
     * Formats a curl command containing all necessary headers and output name.
     */
    fun formatCurlCommand(info: ExternalDownloadInfo): String {
        val filename = sanitizeFilename(info.title).ifBlank { "video" }
        val headerArgs = info.headers.entries.joinToString(" ") { (key, value) ->
            "-H \"$key: $value\""
        }
        return if (headerArgs.isNotBlank()) {
            "curl -L \"${info.url}\" $headerArgs -o \"$filename.mp4\""
        } else {
            "curl -L \"${info.url}\" -o \"$filename.mp4\""
        }
    }

    /**
     * Copies text (such as an aria2c or curl command) to the Android system clipboard.
     */
    fun copyToClipboard(context: Context, label: String, text: String): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard?.setPrimaryClip(clip)
            true
        }.getOrDefault(false)
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun sanitizeFilename(title: String): String {
        return title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }
}
