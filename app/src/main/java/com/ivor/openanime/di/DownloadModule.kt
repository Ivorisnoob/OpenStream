package com.ivor.openanime.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
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
    fun provideDownloadCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): Cache {
        val downloadDirectory = File(context.getExternalFilesDir(null), "downloads")
        return SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)
    }

    @Provides
    @Singleton
    fun provideDataSourceFactory(): DataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.vidking.net/"))
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
        executor: Executor
    ): DownloadManager {
        val downloadManager = DownloadManager(
            context,
            databaseProvider,
            cache,
            dataSourceFactory,
            executor
        )
        // Allow downloading over cellular data
        downloadManager.requirements = androidx.media3.exoplayer.scheduler.Requirements(
            androidx.media3.exoplayer.scheduler.Requirements.NETWORK
        )
        return downloadManager
    }

    @Provides
    @Singleton
    fun provideDownloadNotificationHelper(@ApplicationContext context: Context): androidx.media3.exoplayer.offline.DownloadNotificationHelper {
        return androidx.media3.exoplayer.offline.DownloadNotificationHelper(context, "download_channel")
    }
}
