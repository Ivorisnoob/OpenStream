package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.CustomListDao
import com.ivor.openstream.data.local.dao.CustomListSummary
import com.ivor.openstream.data.local.entity.CustomListEntity
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The user's own lists of titles, kept alongside (not instead of) Watch Later. */
@Singleton
class CustomListRepository @Inject constructor(private val dao: CustomListDao) {

    fun summaries(): Flow<List<CustomListSummary>> = dao.observeSummaries()

    fun list(listId: Long): Flow<CustomListEntity?> = dao.observeList(listId)

    fun items(listId: Long): Flow<List<CustomListItemEntity>> = dao.observeItems(listId)

    /** Ids of the lists a title is in. */
    fun listIdsFor(mediaType: String, tmdbId: Int): Flow<Set<Long>> =
        dao.observeListIdsFor(mediaType, tmdbId).map { it.toSet() }

    /** Creates a list; a name that already exists (ignoring case) returns that list instead. */
    suspend fun create(name: String): Long {
        val trimmed = name.trim()
        return dao.findByName(trimmed) ?: dao.insertList(CustomListEntity(name = trimmed))
    }

    suspend fun rename(listId: Long, name: String) = dao.rename(listId, name.trim())

    suspend fun delete(listId: Long) = dao.deleteList(listId)

    suspend fun add(item: CustomListItemEntity) = dao.addItem(item)

    suspend fun remove(listId: Long, mediaType: String, tmdbId: Int) = dao.removeItem(listId, mediaType, tmdbId)
}
