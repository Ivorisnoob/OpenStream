package com.ivor.openstream

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ivor.openstream.data.diagnostics.Diagnostics
import com.ivor.openstream.data.settings.AppDns
import com.ivor.openstream.presentation.widget.ContinueWatchingWidgetRefresher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class OpenStreamApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var appDns: AppDns

    @Inject
    lateinit var widgetRefresher: ContinueWatchingWidgetRefresher

    /** Outlives every Activity, which is what the widget needs for background playback. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Diagnostics.installCrashRecorder(this)
        widgetRefresher.start(appScope)
    }

    /** Artwork goes through the app's DNS setting too, so posters load where TMDB's DNS is blocked. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder().dns(appDns).build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .build()
    }
}
