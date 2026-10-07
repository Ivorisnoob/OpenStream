package com.ivor.openstream.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Once-a-day, inexact, non-waking alarm that fires [EpisodeCheckReceiver].
 * Inexact + elapsed-time based: batches with the system, costs no visible battery.
 */
object EpisodeAlarm {
    const val ACTION_CHECK = "com.ivor.openstream.EPISODE_CHECK"
    private const val REQUEST_CODE = 41
    private const val INTERVAL_MS = AlarmManager.INTERVAL_DAY

    fun schedule(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            INTERVAL_MS,
            operation(context)
        )
    }

    fun cancel(context: Context) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(operation(context))
    }

    fun isScheduled(context: Context): Boolean {
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, EpisodeCheckReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        return existing != null
    }

    private fun operation(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, EpisodeCheckReceiver::class.java).setAction(ACTION_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
