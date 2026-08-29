package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.domain.repository.WatchLaterRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchLaterRepositoryImpl @Inject constructor(
    private val dao: WatchLaterDao
) : WatchLaterRepository {

    override fun getWatchLaterList(): Flow<List<WatchLaterEntity>> {
        return dao.getAllWatchLaterItems()
    }

    override fun isWatchLater(id: Int): Flow<Boolean> {
        return dao.isWatchLater(id)
    }

    override suspend fun addToWatchLater(item: WatchLaterEntity) {
        dao.insertWatchLaterItem(item)
    }

    override suspend fun removeFromWatchLater(item: WatchLaterEntity) {
        dao.deleteWatchLaterItem(item)
    }

    override suspend fun removeFromWatchLaterById(id: Int) {
        dao.deleteWatchLaterItemById(id)
    }
}
