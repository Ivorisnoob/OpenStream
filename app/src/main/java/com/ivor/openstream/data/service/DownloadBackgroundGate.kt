package com.ivor.openstream.data.service

import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [HlsDownloadService] from needing to become a foreground service while the app is in the
 * background, which Android 12+ answers with a crash Media3 cannot catch: the call is made from the
 * manager's own callback. Two cases reach it, a download waiting for Wi-Fi when Wi-Fi returns, and
 * the system restarting the sticky service after the process died.
 *
 * Both are held here and pick up when the app next comes to the foreground. A download already
 * running when the app leaves keeps its foreground service and is not touched.
 */
@OptIn(UnstableApi::class)
@Singleton
class DownloadBackgroundGate @Inject constructor(
    private val downloadManager: DownloadManager
) : DefaultLifecycleObserver {

    private var observing = false
    private var holding = false

    /** Main thread, from the service's onCreate. */
    fun onServiceCreated() {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        if (!observing) {
            observing = true
            lifecycle.addObserver(this)
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) hold()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (holding) {
            holding = false
            downloadManager.resumeDownloads()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        if (downloadManager.isWaitingForRequirements) hold()
    }

    private fun hold() {
        holding = true
        downloadManager.pauseDownloads()
    }
}
