package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.entity.IdMappingEntity
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.domain.model.MediaIdentity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdMappingService @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val mappingDao: IdMappingDao
) {
    suspend fun enrich(identity: MediaIdentity): MediaIdentity {
        if (!identity.imdbId.isNullOrBlank()) return identity

        val cacheKey = "tmdb-external:${identity.tmdbType}:${identity.tmdbId}"
        val cached = mappingDao.get(cacheKey)
        if (cached != null) {
            return identity.copy(imdbId = cached.providerMediaId)
        }

        return runCatching {
            val externalIds = tmdbApi.getExternalIds(identity.tmdbType, identity.tmdbId)
            val imdbId = externalIds.imdbId?.takeIf { it.isNotBlank() } ?: return@runCatching identity
            mappingDao.insert(
                IdMappingEntity(
                    cacheKey = cacheKey,
                    providerId = "imdb",
                    providerMediaId = imdbId,
                    resolvedAt = System.currentTimeMillis()
                )
            )
            identity.copy(imdbId = imdbId)
        }.getOrDefault(identity)
    }
}
