package com.ivor.openstream.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Fires the daily episode check off the main thread; AlarmManager holds a wake window. */
@AndroidEntryPoint
class EpisodeCheckReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifier: EpisodeNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != EpisodeAlarm.ACTION_CHECK) return
        val pending = goAsync()
        scope.launch {
            try {
                // The system only waits so long for a receiver; a slow network must not run the
                // check past that. Shows it didn't reach are picked up by the next run.
                runCatching { withTimeoutOrNull(CHECK_BUDGET_MS) { notifier.checkOnce() } }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** Under the 60 s the system gives a background broadcast before it reports an ANR. */
        const val CHECK_BUDGET_MS = 45_000L
    }
}

/** Re-arms the daily check after a reboot; alarms don't survive power cycles. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settings: com.ivor.openstream.data.settings.AppSettingsStore

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (settings.current.episodeNotifications) {
            EpisodeAlarm.schedule(context.applicationContext)
        }
    }
}
