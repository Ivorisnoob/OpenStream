package com.ivor.openstream.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ivor.openstream.data.local.dao.ProfileDao
import com.ivor.openstream.data.local.entity.ProfileEntity
import com.ivor.openstream.data.repository.ProfileRepository
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ivor.openstream.data.local.AppDatabase
import com.ivor.openstream.data.local.dao.CustomListDao
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

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `custom_lists` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `custom_list_items` (
                    `listId` INTEGER NOT NULL,
                    `tmdbId` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `posterPath` TEXT,
                    `voteAverage` REAL NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`listId`, `mediaType`, `tmdbId`)
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_custom_list_items_mediaType_tmdbId` " +
                    "ON `custom_list_items` (`mediaType`, `tmdbId`)"
            )
        }
    }

    /**
     * Profiles: a `profiles` table seeded with the default profile, and a `profileId` on every
     * per-person table. Primary keys gain the profile so two profiles can hold the same title;
     * SQLite can't change a primary key in place, so those tables are rebuilt. Existing rows all
     * go to the default profile. Downloads stay device-wide.
     */
    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val defaultId = ProfileEntity.DEFAULT_ID
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `avatar` TEXT NOT NULL, `isKids` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            seedDefaultProfile(database)

            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `watch_later_new` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`posterPath` TEXT, `mediaType` TEXT NOT NULL, `voteAverage` REAL NOT NULL, `dateAdded` INTEGER NOT NULL, " +
                    "`profileId` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `id`))"
            )
            database.execSQL(
                "INSERT INTO `watch_later_new` (`id`, `title`, `posterPath`, `mediaType`, `voteAverage`, `dateAdded`, `profileId`) " +
                    "SELECT `id`, `title`, `posterPath`, `mediaType`, `voteAverage`, `dateAdded`, $defaultId FROM `watch_later`"
            )
            database.execSQL("DROP TABLE `watch_later`")
            database.execSQL("ALTER TABLE `watch_later_new` RENAME TO `watch_later`")

            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `watch_progress_new` (`id` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, " +
                    "`mediaType` TEXT NOT NULL, `season` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                    "`episodeTitle` TEXT, `posterPath` TEXT, `backdropPath` TEXT, `stillPath` TEXT, `positionMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                    "`profileId` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `id`))"
            )
            database.execSQL(
                "INSERT INTO `watch_progress_new` (`id`, `tmdbId`, `mediaType`, `season`, `episode`, `title`, `episodeTitle`, " +
                    "`posterPath`, `backdropPath`, `stillPath`, `positionMs`, `durationMs`, `completed`, `updatedAt`, `profileId`) " +
                    "SELECT `id`, `tmdbId`, `mediaType`, `season`, `episode`, `title`, `episodeTitle`, `posterPath`, " +
                    "`backdropPath`, `stillPath`, `positionMs`, `durationMs`, `completed`, `updatedAt`, $defaultId FROM `watch_progress`"
            )
            database.execSQL("DROP TABLE `watch_progress`")
            database.execSQL("ALTER TABLE `watch_progress_new` RENAME TO `watch_progress`")
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_watch_progress_profileId_mediaType_tmdbId` " +
                    "ON `watch_progress` (`profileId`, `mediaType`, `tmdbId`)"
            )

            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `hidden_titles_new` (`tmdbId` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `hiddenAt` INTEGER NOT NULL, `profileId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profileId`, `mediaType`, `tmdbId`))"
            )
            database.execSQL(
                "INSERT INTO `hidden_titles_new` (`tmdbId`, `mediaType`, `title`, `hiddenAt`, `profileId`) " +
                    "SELECT `tmdbId`, `mediaType`, `title`, `hiddenAt`, $defaultId FROM `hidden_titles`"
            )
            database.execSQL("DROP TABLE `hidden_titles`")
            database.execSQL("ALTER TABLE `hidden_titles_new` RENAME TO `hidden_titles`")

            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_lists_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `profileId` INTEGER NOT NULL)"
            )
            database.execSQL(
                "INSERT INTO `custom_lists_new` (`id`, `name`, `createdAt`, `updatedAt`, `profileId`) " +
                    "SELECT `id`, `name`, `createdAt`, `updatedAt`, $defaultId FROM `custom_lists`"
            )
            database.execSQL("DROP TABLE `custom_lists`")
            database.execSQL("ALTER TABLE `custom_lists_new` RENAME TO `custom_lists`")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lists_profileId` ON `custom_lists` (`profileId`)")
        }
    }

    /** The profile every existing row is given; also created on a fresh install. */
    private fun seedDefaultProfile(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT OR IGNORE INTO `profiles` (`id`, `name`, `avatar`, `isKids`, `createdAt`) VALUES (?, ?, ?, 0, ?)",
            arrayOf<Any>(
                ProfileEntity.DEFAULT_ID,
                ProfileRepository.DEFAULT_NAME,
                ProfileRepository.DEFAULT_AVATAR,
                System.currentTimeMillis()
            )
        )
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "open_stream_db"
        )
            .addMigrations(migration2To3, migration3To4, migration4To5, migration5To6, migration6To7, migration7To8)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) = seedDefaultProfile(db)
            })
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
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideCustomListDao(database: AppDatabase): CustomListDao {
        return database.customListDao()
    }

    @Provides
    fun provideWatchProgressDao(database: AppDatabase): WatchProgressDao {
        return database.watchProgressDao()
    }
}
