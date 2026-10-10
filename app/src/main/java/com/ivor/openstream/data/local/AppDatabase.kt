package com.ivor.openstream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ivor.openstream.data.local.dao.CustomListDao
import com.ivor.openstream.data.local.dao.DownloadDao
import com.ivor.openstream.data.local.dao.HiddenTitleDao
import com.ivor.openstream.data.local.dao.ReminderDao
import com.ivor.openstream.data.local.dao.TitleRatingDao
import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.dao.ProfileDao
import com.ivor.openstream.data.local.dao.WatchProgressDao
import com.ivor.openstream.data.local.entity.CustomListEntity
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import com.ivor.openstream.data.local.entity.IdMappingEntity
import com.ivor.openstream.data.local.entity.ProfileEntity
import com.ivor.openstream.data.local.entity.ReminderEntity
import com.ivor.openstream.data.local.entity.TitleRatingEntity
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.local.entity.WatchProgressEntity

@Database(
    entities = [
        WatchLaterEntity::class,
        DownloadEntity::class,
        IdMappingEntity::class,
        WatchProgressEntity::class,
        HiddenTitleEntity::class,
        CustomListEntity::class,
        CustomListItemEntity::class,
        ProfileEntity::class,
        ReminderEntity::class,
        TitleRatingEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun downloadDao(): DownloadDao
    abstract fun idMappingDao(): IdMappingDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun hiddenTitleDao(): HiddenTitleDao
    abstract fun customListDao(): CustomListDao
    abstract fun profileDao(): ProfileDao
    abstract fun reminderDao(): ReminderDao
    abstract fun ratingDao(): TitleRatingDao
}
