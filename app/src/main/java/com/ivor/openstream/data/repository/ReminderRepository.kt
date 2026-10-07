package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.ReminderDao
import com.ivor.openstream.data.local.dao.TitleRatingDao
import com.ivor.openstream.data.local.entity.ReminderEntity
import com.ivor.openstream.data.local.entity.TitleRatingEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** "Remind me" requests for the active profile, fired once by the daily alarm. */
@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    settings: AppSettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val reminders = settings.activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observe(id)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun all(): List<ReminderEntity> = dao.all()

    suspend fun add(item: ReminderEntity) = dao.upsert(item)

    suspend fun remove(profileId: Long, mediaType: String, tmdbId: Int) =
        dao.delete(profileId, mediaType, tmdbId)

    fun isReminder(profileId: Long, mediaType: String, tmdbId: Int): Flow<Boolean> =
        dao.observe(profileId).flatMapLatest { list ->
            flowOf(list.any { it.mediaType == mediaType && it.tmdbId == tmdbId })
        }
}

/** Explicit taste signals for the active profile. Values: +1 like, -1 dislike. */
@Singleton
class TitleRatingRepository @Inject constructor(
    private val dao: TitleRatingDao,
    private val settings: AppSettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val ratings = settings.activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(emptyMap()) else dao.observe(id)
            .flatMapLatest { list -> flowOf(list.associate { (it.mediaType to it.tmdbId) to it.rating }) }
    }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val entities = settings.activeProfileId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else dao.observe(id)
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun snapshot(): Map<Pair<String, Int>, Int> {
        val id = settings.activeProfileId.value ?: return emptyMap()
        return dao.all(id).associate { (it.mediaType to it.tmdbId) to it.rating }
    }

    fun ratingFor(mediaType: String, tmdbId: Int): Flow<Int?> =
        settings.activeProfileId.flatMapLatest { id ->
            if (id == null) flowOf(null) else dao.observeRating(id, mediaType, tmdbId)
        }

    /** Toggles: tapping the active rating clears it, otherwise sets it. */
    suspend fun toggle(
        mediaType: String,
        tmdbId: Int,
        rating: Int,
        title: String = "",
        genreIds: Set<Int> = emptySet(),
        language: String? = null,
        voteAverage: Double? = null
    ) {
        val id = settings.activeProfileId.value ?: return
        val current = dao.all(id).firstOrNull { it.mediaType == mediaType && it.tmdbId == tmdbId }?.rating
        if (current == rating) {
            dao.delete(id, mediaType, tmdbId)
        } else {
            dao.upsert(
                TitleRatingEntity(
                    profileId = id,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                    rating = rating,
                    title = title,
                    genreIds = genreIds.joinToString(","),
                    language = language,
                    voteAverage = voteAverage
                )
            )
        }
    }
}
