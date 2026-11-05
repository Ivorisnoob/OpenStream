package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.entity.IdMappingEntity
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.domain.model.MediaIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TMDB → provider id lookups, remembered in Room so later plays skip the request.
 *
 * Entries expire: TMDB editors do fix miss-linked imdb ids from time to time, and without an
 * expiry such a correction could never reach a title the app already mapped. The lifetime matches
 * the HTTP cache's freshness for external_ids answers. A refresh that fails (offline) keeps
 * serving the old mapping — asking the sources with an empty id would be worse than a month-old
 * one. Rows older than six lifetimes are swept once per process, so the table doesn't only grow.
 */
@Singleton
class IdMappingService @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val mappingDao: IdMappingDao
) {
    /** Skipped once the process has cleaned up whatever a previous run left behind. */
    private var sweptExpired = false

    suspend fun enrich(identity: MediaIdentity): MediaIdentity {
        if (!sweptExpired) {
            sweptExpired = true
            // Only rows long past their lifetime: an expired one is still the offline fallback below.
            runCatching { mappingDao.deleteOlderThan(now() - SWEEP_AFTER_MS) }
        }
        if (!identity.imdbId.isNullOrBlank()) return identity

        val cacheKey = "tmdb-external:${identity.tmdbType}:${identity.tmdbId}"
        val cached = mappingDao.get(cacheKey)
        if (cached != null && isFresh(cached.resolvedAt, now())) {
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
                    resolvedAt = now()
                )
            )
            identity.copy(imdbId = imdbId)
        }
            // Offline with an expired entry: the old mapping is still better than no mapping.
            .getOrDefault(identity.copy(imdbId = cached?.providerMediaId))
    }

    /** Forgets every remembered mapping; Settings' "Clear cache" calls this. */
    suspend fun clear() = mappingDao.clear()

    private fun now(): Long = System.currentTimeMillis()

    internal companion object {
        /** How long a remembered mapping is trusted, as long as the HTTP cache keeps external_ids fresh. */
        internal const val TTL_MS = 30L * 24 * 60 * 60 * 1000

        /** When an expired mapping is deleted rather than kept for offline use. */
        private const val SWEEP_AFTER_MS = 6 * TTL_MS

        /** [resolvedAt] within [TTL_MS] of [now], and not from the future. */
        internal fun isFresh(resolvedAt: Long, now: Long): Boolean =
            now - resolvedAt in 0 until TTL_MS
    }
}
