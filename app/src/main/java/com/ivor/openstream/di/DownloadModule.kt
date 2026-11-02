package com.ivor.openstream.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import com.ivor.openstream.data.downloads.DownloadStorage
import com.ivor.openstream.data.settings.AppSettings
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.DownloadRequestHeaderStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideDownloadCache(storage: DownloadStorage, databaseProvider: DatabaseProvider): Cache {
        // On the storage chosen in Settings (internal, or an SD card); see DownloadStorage.
        return SimpleCache(storage.downloadDirectory(), NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    fun provideDataSourceFactory(headerStore: DownloadRequestHeaderStore): DataSource.Factory {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(BROWSER_USER_AGENT)
        return ResolvingDataSource.Factory(
            httpFactory,
            ResolvingDataSource.Resolver { dataSpec ->
                dataSpec.withAdditionalHeaders(headerStore.headersFor(dataSpec.uri))
            }
        )
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        cache: Cache,
        dataSourceFactory: DataSource.Factory
    ): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(dataSourceFactory)
            .setCacheWriteDataSinkFactory(null) // Player should not write to cache, only read
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideDownloadExecutor(): Executor {
        return Executors.newFixedThreadPool(4)
    }

    @Provides
    @Singleton
    fun provideMedia3DownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        cache: Cache,
        dataSourceFactory: DataSource.Factory,
        executor: Executor,
        settings: AppSettingsStore
    ): DownloadManager {
        val downloadManager = DownloadManager(
            context,
            databaseProvider,
            cache,
            dataSourceFactory,
            executor
        )
        downloadManager.requirements = downloadRequirements(settings.current)
        return downloadManager
    }

    @Provides
    @Singleton
    fun provideDownloadNotificationHelper(@ApplicationContext context: Context): androidx.media3.exoplayer.offline.DownloadNotificationHelper {
        return androidx.media3.exoplayer.offline.DownloadNotificationHelper(context, "download_channel")
    }
}

/**
 * What downloads wait for: any network, or only unmetered ones (Wi-Fi) when the user asked for
 * that, and the charger when downloads are set to run only while charging.
 */
fun downloadRequirements(settings: AppSettings): Requirements {
    val network = if (settings.wifiOnlyDownloads) Requirements.NETWORK_UNMETERED else Requirements.NETWORK
    val charging = if (settings.chargingOnlyDownloads) Requirements.DEVICE_CHARGING else 0
    return Requirements(network or charging)
}
