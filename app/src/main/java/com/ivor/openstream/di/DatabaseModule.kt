package com.ivor.openstream.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ivor.openstream.data.local.AppDatabase
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.dao.WatchLaterDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "open_stream_db"
        )
            .addMigrations(migration2To3)
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
}
