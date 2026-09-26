package com.ivor.openstream.data.repository

import com.ivor.openstream.data.local.dao.WatchProgressDao
import com.ivor.openstream.data.local.entity.WatchProgressEntity
import com.ivor.openstream.domain.model.WatchProgress
import com.ivor.openstream.domain.repository.WatchProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressRepositoryImpl @Inject constructor(
    private val dao: WatchProgressDao
) : WatchProgressRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun continueWatching(limit: Int): Flow<List<WatchProgress>> =
        dao.observeContinueWatching(limit).map { rows -> rows.map { it.toDomain() } }

    override fun progressForTitle(mediaType: String, tmdbId: Int): Flow<List<WatchProgress>> =
        dao.observeForTitle(mediaType, tmdbId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(mediaType: String, tmdbId: Int, season: Int, episode: Int): WatchProgress? =
        dao.get(WatchProgressEntity.idFor(mediaType, tmdbId, season, episode))?.toDomain()

    override fun record(progress: WatchProgress) {
        scope.launch { dao.upsert(progress.toEntity()) }
    }

    override suspend fun dismiss(mediaType: String, tmdbId: Int) {
        dao.deleteUnfinished(mediaType, tmdbId)
    }

    override suspend fun clearTitle(mediaType: String, tmdbId: Int) {
        dao.deleteForTitle(mediaType, tmdbId)
    }

    override suspend fun clearEpisode(mediaType: String, tmdbId: Int, season: Int, episode: Int) {
        dao.delete(WatchProgressEntity.idFor(mediaType, tmdbId, season, episode))
    }

    override suspend fun clearAll() {
        dao.clear()
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

    private fun WatchProgress.toEntity() = WatchProgressEntity(
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
        updatedAt = updatedAt
    )
}
