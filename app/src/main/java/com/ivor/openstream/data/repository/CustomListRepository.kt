package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.CustomListDao
import com.ivor.openstream.data.local.dao.CustomListSummary
import com.ivor.openstream.data.local.entity.CustomListEntity
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The active profile's own lists of titles, kept alongside (not instead of) Watch Later. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CustomListRepository @Inject constructor(
    private val dao: CustomListDao,
    private val settings: AppSettingsStore
) {
    private val profileId: Long get() = settings.activeProfileId.value

    fun summaries(): Flow<List<CustomListSummary>> = settings.activeProfileId.flatMapLatest { dao.observeSummaries(it) }

    fun list(listId: Long): Flow<CustomListEntity?> = dao.observeList(listId)

    fun items(listId: Long): Flow<List<CustomListItemEntity>> = dao.observeItems(listId)

    /** Ids of the lists a title is in. */
    fun listIdsFor(mediaType: String, tmdbId: Int): Flow<Set<Long>> =
        settings.activeProfileId.flatMapLatest { dao.observeListIdsFor(it, mediaType, tmdbId) }.map { it.toSet() }

    /** Creates a list; a name that already exists (ignoring case) returns that list instead. */
    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        return dao.findByName(profileId, trimmed) ?: dao.insertList(CustomListEntity(name = trimmed, profileId = profileId))
    }

    suspend fun rename(listId: Long, name: String) = dao.rename(listId, name.trim())

    suspend fun delete(listId: Long) = dao.deleteList(listId)

    suspend fun add(item: CustomListItemEntity) = dao.addItem(item)

    suspend fun remove(listId: Long, mediaType: String, tmdbId: Int) = dao.removeItem(listId, mediaType, tmdbId)
}
