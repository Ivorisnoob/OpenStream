package com.ivor.openstream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.local.entity.IdMappingEntity
import com.ivor.openstream.data.local.entity.WatchLaterEntity

@Database(
    entities = [WatchLaterEntity::class, DownloadEntity::class, IdMappingEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun downloadDao(): DownloadDao
    abstract fun idMappingDao(): IdMappingDao
}
