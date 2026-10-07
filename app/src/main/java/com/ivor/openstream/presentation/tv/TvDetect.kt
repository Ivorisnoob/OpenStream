package com.ivor.openstream.presentation.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** True on Android TV / Google TV devices driven by a remote rather than touch. */
object TvDetect {
    fun isTv(context: Context): Boolean {
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) return true
        if (pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) return true
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType ?: Configuration.UI_MODE_TYPE_UNDEFINED
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /** Honor the system animator scale: no focus zoom when the user disabled animation. */
    fun animationsEnabled(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }.getOrDefault(true)
}

/** Remembers whether this device is a TV; TV mode never changes at runtime. */
@Composable
fun rememberTvMode(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember { TvDetect.isTv(context) }
}
