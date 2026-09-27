package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.HiddenTitleDao
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Titles the user said they're not interested in, keyed as "mediaType:tmdbId". */
@Singleton
class HiddenTitlesRepository @Inject constructor(
    private val dao: HiddenTitleDao
) {
    val hiddenKeys: Flow<Set<String>> = dao.observeAll().map { rows -> rows.mapTo(HashSet()) { key(it.mediaType, it.tmdbId) } }

    val count: Flow<Int> = dao.observeAll().map { it.size }

    suspend fun hide(mediaType: String, tmdbId: Int, title: String) =
        dao.insert(HiddenTitleEntity(tmdbId = tmdbId, mediaType = mediaType, title = title))

    suspend fun unhide(mediaType: String, tmdbId: Int) = dao.delete(mediaType, tmdbId)

    suspend fun unhideAll() = dao.clear()

    companion object {
        fun key(mediaType: String, tmdbId: Int) = "$mediaType:$tmdbId"
    }
}
