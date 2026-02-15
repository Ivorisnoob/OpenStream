package com.ivor.openanime.domain.repository

import com.ivor.openanime.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

interface WatchLaterRepository {
    fun getWatchLaterList(): Flow<List<WatchLaterEntity>>
    fun isWatchLater(id: Int): Flow<Boolean>
    suspend fun addToWatchLater(item: WatchLaterEntity)
    suspend fun removeFromWatchLater(item: WatchLaterEntity)
    suspend fun removeFromWatchLaterById(id: Int)
}
