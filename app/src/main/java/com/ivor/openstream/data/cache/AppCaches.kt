package com.ivor.openstream.data.cache

import android.content.Context
import coil3.SingletonImageLoader
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every cache the app keeps for speed, as one thing Settings can size and clear: artwork (Coil),
 * TMDB answers (OkHttp), Home's built lists and recently found streams. None of it is user data;
 * downloads, progress and saved subtitles live elsewhere and are never touched here.
 */
@Singleton
class AppCaches @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataCache: Cache,
    private val animeRepository: AnimeRepository,
    private val streamingRepository: StreamingRepository
) {
    /** Bytes on disk across artwork and title info, or null when it can't be read. */
    suspend fun sizeBytes(): Long? = withContext(Dispatchers.IO) {
        val images = runCatching { SingletonImageLoader.get(context).diskCache?.size }.getOrNull()
        val metadata = runCatching { metadataCache.size() }.getOrNull()
        if (images == null && metadata == null) null else (images ?: 0L) + (metadata ?: 0L)
    }

    /** Clears everything; each part on its own, so one failing doesn't keep the rest. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching { metadataCache.evictAll() }
        animeRepository.clearCachedCatalogs()
        streamingRepository.clearResolvedServers()
    }
}
