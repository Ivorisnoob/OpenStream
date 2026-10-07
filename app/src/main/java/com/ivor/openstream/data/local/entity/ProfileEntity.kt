package com.ivor.openstream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Someone who watches on this device. Watch Later, progress (History, Continue Watching), hidden
 * titles and custom lists belong to a profile; downloads are shared by the whole device.
 *
 * [avatar] is a built-in avatar key (see `ProfileAvatar`).
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String,
    val isKids: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    /** SHA-256 hex of salt + PIN; null means no PIN lock. */
    val pinHash: String? = null,
    val pinSalt: String? = null
) {
    companion object {
        /** The profile every row that predates profiles belongs to (seeded by the migration). */
        const val DEFAULT_ID = 1L
    }
}
