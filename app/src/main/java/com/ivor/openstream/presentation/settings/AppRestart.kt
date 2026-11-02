package com.ivor.openstream.presentation.settings

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Starts the app afresh: a new task for the launcher activity, then the old process ends. Needed
 * after the download storage moves, because the download cache opens its folder once per process.
 */
internal object AppRestart {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val component = launch.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        exitProcess(0)
    }
}
