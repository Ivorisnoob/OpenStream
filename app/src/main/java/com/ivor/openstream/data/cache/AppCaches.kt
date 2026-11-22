package com.ivor.openstream.data.cache

import android.content.Context
import coil3.SingletonImageLoader
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import com.ivor.openstream.data.streaming.IdMappingService
import javax.inject.Inject
import androidx.media3.common.util.UnstableApi
import com.ivor.openstream.di.PlaybackBuffer
import javax.inject.Singleton

/**
 * Every cache the app keeps for speed, as one thing Settings can size and clear: artwork (Coil),
 * TMDB answers (OkHttp), Home's built lists, recently found streams and remembered id mappings.
 * None of it is user data; downloads, progress and saved subtitles live elsewhere and are never
 * touched here.
 */
@UnstableApi
@Singleton
class AppCaches @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataCache: Cache,
    @PlaybackBuffer private val playbackBuffer: androidx.media3.datasource.cache.Cache,
    private val animeRepository: AnimeRepository,
    private val streamingRepository: StreamingRepository,
    private val idMappingService: IdMappingService
) {
    /** Bytes on disk across artwork and title info, or null when it can't be read. */
    suspend fun sizeBytes(): Long? = withContext(Dispatchers.IO) {
        val images = runCatching { SingletonImageLoader.get(context).diskCache?.size }.getOrNull()
        val metadata = runCatching { metadataCache.size() }.getOrNull()
        val video = runCatching { playbackBuffer.cacheSpace }.getOrNull()
        if (images == null && metadata == null && video == null) null
        else (images ?: 0L) + (metadata ?: 0L) + (video ?: 0L)
    }

    /** Clears everything; each part on its own, so one failing doesn't keep the rest. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching { metadataCache.evictAll() }
        runCatching { playbackBuffer.keys.forEach(playbackBuffer::removeResource) }
        animeRepository.clearCachedCatalogs()
        streamingRepository.clearResolvedServers()
        runCatching { idMappingService.clear() }
    }
}
