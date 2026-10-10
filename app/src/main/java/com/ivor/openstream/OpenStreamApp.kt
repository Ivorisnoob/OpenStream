package com.ivor.openstream

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ivor.openstream.data.diagnostics.Diagnostics
import com.ivor.openstream.data.downloads.SmartDownloads
import com.ivor.openstream.data.notifications.EpisodeAlarm
import com.ivor.openstream.data.settings.AppDns
import com.ivor.openstream.data.settings.AppLocale
import com.ivor.openstream.data.settings.AppSettingsStore
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class OpenStreamApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var appDns: AppDns

    @Inject
    lateinit var appSettingsStore: AppSettingsStore

    @Inject
    lateinit var smartDownloads: SmartDownloads

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base, AppLocale.savedTag(base)))
    }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.installCrashRecorder(this)
        // Daily episode check, only while the toggle is on.
        if (appSettingsStore.current.episodeNotifications &&
            !EpisodeAlarm.isScheduled(this)
        ) {
            EpisodeAlarm.schedule(this)
        }
        smartDownloads.start()
    }

    /** Artwork goes through the app's DNS setting too, so posters load where TMDB's DNS is blocked. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder().dns(appDns).build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .build()
    }
}
