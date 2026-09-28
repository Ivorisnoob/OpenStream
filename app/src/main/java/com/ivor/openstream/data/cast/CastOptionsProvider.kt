package com.ivor.openstream.data.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.ivor.openstream.MainActivity

/**
 * Read by the Cast framework through the manifest (kept in proguard-rules.pro). Uses Google's
 * Default Media Receiver, so no receiver app has to be registered or hosted. The framework's media
 * notification and lock-screen controls cover playback on the TV; tapping them opens the app.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val notification = NotificationOptions.Builder()
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()
        val media = CastMediaOptions.Builder()
            .setNotificationOptions(notification)
            .setMediaSessionEnabled(true)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(media)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setResumeSavedSession(true)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
