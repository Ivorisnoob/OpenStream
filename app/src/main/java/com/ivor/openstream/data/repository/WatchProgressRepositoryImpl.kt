package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.WatchProgressDao
import com.ivor.openstream.data.local.entity.WatchProgressEntity
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.repository.WatchProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of the active profile; reads follow profile switches, writes go to the active one. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WatchProgressRepositoryImpl @Inject constructor(
    private val dao: WatchProgressDao,
    private val settings: AppSettingsStore
) : WatchProgressRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val profileId: Long get() = settings.activeProfileId.value

    override fun continueWatching(limit: Int): Flow<List<WatchProgress>> =
        settings.activeProfileId.flatMapLatest { dao.observeContinueWatching(it, limit) }
            .map { rows -> rows.map { it.toDomain() } }

    override fun allProgress(): Flow<List<WatchProgress>> =
        settings.activeProfileId.flatMapLatest { dao.observeAll(it) }
            .map { rows -> rows.map { it.toDomain() } }

    override fun progressForTitle(mediaType: String, tmdbId: Int): Flow<List<WatchProgress>> =
        settings.activeProfileId.flatMapLatest { dao.observeForTitle(it, mediaType, tmdbId) }
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(mediaType: String, tmdbId: Int, season: Int, episode: Int): WatchProgress? =
        dao.get(profileId, WatchProgressEntity.idFor(mediaType, tmdbId, season, episode))?.toDomain()

    override fun record(progress: WatchProgress) {
        // The profile is read now, not when the write runs, so a switch can't redirect it.
        val entity = progress.toEntity(profileId)
        scope.launch { dao.upsert(entity) }
    }

    override suspend fun dismiss(mediaType: String, tmdbId: Int) {
        dao.deleteUnfinished(profileId, mediaType, tmdbId)
    }

    override suspend fun clearTitle(mediaType: String, tmdbId: Int) {
        dao.deleteForTitle(profileId, mediaType, tmdbId)
    }

    override suspend fun clearEpisode(mediaType: String, tmdbId: Int, season: Int, episode: Int) {
        dao.delete(profileId, WatchProgressEntity.idFor(mediaType, tmdbId, season, episode))
    }

    override suspend fun clearAll() {
        dao.clear(profileId)
    }

    private fun WatchProgressEntity.toDomain() = WatchProgress(
        tmdbId = tmdbId,
        mediaType = mediaType,
        season = season,
        episode = episode,
        title = title,
        episodeTitle = episodeTitle,
        posterPath = posterPath,
        backdropPath = backdropPath,
        stillPath = stillPath,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = completed,
        updatedAt = updatedAt
    )

    private fun WatchProgress.toEntity(profileId: Long) = WatchProgressEntity(
        id = WatchProgressEntity.idFor(mediaType, tmdbId, season, episode),
        tmdbId = tmdbId,
        mediaType = mediaType,
        season = season,
        episode = episode,
        title = title,
        episodeTitle = episodeTitle,
        posterPath = posterPath,
        backdropPath = backdropPath,
        stillPath = stillPath,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = completed,
        updatedAt = updatedAt,
        profileId = profileId
    )
}
