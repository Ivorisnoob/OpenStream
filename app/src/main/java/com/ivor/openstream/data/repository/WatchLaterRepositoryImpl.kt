package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.WatchLaterDao
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.repository.WatchLaterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/** Watch Later of the active profile; reads follow profile switches. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchLaterRepositoryImpl @Inject constructor(
    private val dao: WatchLaterDao,
    private val settings: AppSettingsStore
) : WatchLaterRepository {

    private val profileId: Long get() = settings.activeProfileId.value

    override fun getWatchLaterList(): Flow<List<WatchLaterEntity>> =
        settings.activeProfileId.flatMapLatest { dao.getAllWatchLaterItems(it) }

    override fun isWatchLater(id: Int): Flow<Boolean> =
        settings.activeProfileId.flatMapLatest { dao.isWatchLater(it, id) }

    override suspend fun addToWatchLater(item: WatchLaterEntity) {
        dao.insertWatchLaterItem(item.copy(profileId = profileId))
    }

    override suspend fun removeFromWatchLater(item: WatchLaterEntity) {
        dao.deleteWatchLaterItemById(item.profileId, item.id)
    }

    override suspend fun removeFromWatchLaterById(id: Int) {
        dao.deleteWatchLaterItemById(profileId, id)
    }
}
