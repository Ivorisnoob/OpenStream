package com.ivor.openstream.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "id_mappings")
data class IdMappingEntity(
    @PrimaryKey val cacheKey: String,
    val providerId: String,
    val providerMediaId: String,
    val resolvedAt: Long
)
