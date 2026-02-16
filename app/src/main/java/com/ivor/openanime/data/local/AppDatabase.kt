package com.ivor.openanime.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ivor.openanime.data.local.dao.DownloadDao
import com.ivor.openanime.data.local.dao.WatchLaterDao
import com.ivor.openanime.data.local.entity.DownloadEntity
import com.ivor.openanime.data.local.entity.WatchLaterEntity

@Database(entities = [WatchLaterEntity::class, DownloadEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun downloadDao(): DownloadDao
}
