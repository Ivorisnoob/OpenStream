package com.ivor.openstream.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.settings.AppSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash reports kept on the device, and a plain-text diagnostics file the user can save and attach
 * to a bug report. Nothing is sent anywhere.
 */
@Singleton
class Diagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettingsStore
) {
    val crashCount: Int get() = crashFiles(context).size

    /** Writes device and app details, saved crashes and this process's recent log to [output]. */
    fun export(output: OutputStream) {
        output.bufferedWriter().use { writer ->
            writer.appendLine("OpenStream diagnostics")
            writer.appendLine("Created: ${timestamp(System.currentTimeMillis())}")
            writer.appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}")
            writer.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            writer.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            val current = settings.current
            writer.appendLine("DNS: ${current.dnsProvider.label} · Wi-Fi-only downloads: ${current.wifiOnlyDownloads}")
            writer.appendLine()

            val crashes = crashFiles(context)
            writer.appendLine("== Crashes (${crashes.size}) ==")
            crashes.forEach { file ->
                writer.appendLine()
                writer.appendLine(file.readText())
            }
            writer.appendLine()

            writer.appendLine("== Recent log ==")
            writer.appendLine(recentLog())
        }
    }

    fun clearCrashes() {
        crashFiles(context).forEach(File::delete)
    }

    /** This process's own log lines; apps can always read those without a permission. */
    private fun recentLog(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", LOG_LINES.toString(), "--pid", Process.myPid().toString()
        ).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, TimeUnit.SECONDS)
        text.ifBlank { "(empty)" }
    }.getOrElse { "(couldn't read the log: ${it.message})" }

    companion object {
        private const val CRASH_DIR = "crashes"
        private const val MAX_CRASHES = 5
        private const val LOG_LINES = 3000

        /** Records uncaught exceptions to a file, then lets the previous handler crash the app as usual. */
        fun installCrashRecorder(context: Context) {
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { writeCrash(appContext, thread, error) }
                previous?.uncaughtException(thread, error)
            }
        }

        private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
            val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
            val now = System.currentTimeMillis()
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
            File(dir, "crash-$now.txt").writeText(
                buildString {
                    appendLine("Crash at ${timestamp(now)} on thread \"${thread.name}\"")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    append(trace)
                }
            )
            // Keep only the newest few.
            crashFiles(context).drop(MAX_CRASHES).forEach(File::delete)
        }

        private fun crashFiles(context: Context): List<File> =
            File(context.filesDir, CRASH_DIR).listFiles { file -> file.name.startsWith("crash-") }
                ?.sortedByDescending { it.lastModified() }
                .orEmpty()

        private fun timestamp(ms: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(ms))
    }
}
