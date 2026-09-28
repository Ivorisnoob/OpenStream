package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.HiddenTitleDao
import com.ivor.openstream.data.local.entity.HiddenTitleEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Titles the active profile said it's not interested in, keyed as "mediaType:tmdbId". */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HiddenTitlesRepository @Inject constructor(
    private val dao: HiddenTitleDao,
    private val settings: AppSettingsStore
) {
    private val rows = settings.activeProfileId.flatMapLatest { dao.observeAll(it) }

    val hiddenKeys: Flow<Set<String>> = rows.map { list -> list.mapTo(HashSet()) { key(it.mediaType, it.tmdbId) } }

    val count: Flow<Int> = rows.map { it.size }

    suspend fun hide(mediaType: String, tmdbId: Int, title: String) = dao.insert(
        HiddenTitleEntity(tmdbId = tmdbId, mediaType = mediaType, title = title, profileId = settings.activeProfileId.value)
    )

    suspend fun unhide(mediaType: String, tmdbId: Int) = dao.delete(settings.activeProfileId.value, mediaType, tmdbId)

    suspend fun unhideAll() = dao.clear(settings.activeProfileId.value)

    companion object {
        fun key(mediaType: String, tmdbId: Int) = "$mediaType:$tmdbId"
    }
}
