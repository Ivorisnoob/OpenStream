package com.ivor.openstream.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ivor.openstream.data.local.AppDatabase
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.local.dao.HiddenTitleDao
import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.dao.WatchProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS id_mappings (
                    cacheKey TEXT NOT NULL PRIMARY KEY,
                    providerId TEXT NOT NULL,
                    providerMediaId TEXT NOT NULL,
                    resolvedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL("ALTER TABLE downloads ADD COLUMN providerId TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN serverId TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN serverName TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN requestHeadersJson TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN resolvedAt INTEGER")
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `watch_progress` (
                    `id` TEXT NOT NULL,
                    `tmdbId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `season` INTEGER NOT NULL,
                    `episode` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `episodeTitle` TEXT,
                    `posterPath` TEXT,
                    `backdropPath` TEXT,
                    `stillPath` TEXT,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_watch_progress_mediaType_tmdbId` " +
                    "ON `watch_progress` (`mediaType`, `tmdbId`)"
            )
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE downloads ADD COLUMN showTitle TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN episodeTitle TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN stillPath TEXT")
            database.execSQL("ALTER TABLE downloads ADD COLUMN year INTEGER")
            database.execSQL("ALTER TABLE downloads ADD COLUMN errorMessage TEXT")
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `hidden_titles` (
                    `tmdbId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `hiddenAt` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaType`, `tmdbId`)
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "open_stream_db"
        )
            .addMigrations(migration2To3, migration3To4, migration4To5, migration5To6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWatchLaterDao(database: AppDatabase): WatchLaterDao {
        return database.watchLaterDao()
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideIdMappingDao(database: AppDatabase): IdMappingDao {
        return database.idMappingDao()
    }

    @Provides
    fun provideHiddenTitleDao(database: AppDatabase): HiddenTitleDao {
        return database.hiddenTitleDao()
    }

    @Provides
    fun provideWatchProgressDao(database: AppDatabase): WatchProgressDao {
        return database.watchProgressDao()
    }
}
