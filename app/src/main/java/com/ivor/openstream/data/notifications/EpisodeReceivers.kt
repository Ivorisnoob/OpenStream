package com.ivor.openstream.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
                runCatching { notifier.checkOnce() }
            } finally {
                pending.finish()
            }
        }
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
