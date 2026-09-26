package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface WatchProgressRepository {
    /** The latest unfinished episode of each title, newest first. */
    fun continueWatching(limit: Int = 20): Flow<List<WatchProgress>>

    fun progressForTitle(mediaType: String, tmdbId: Int): Flow<List<WatchProgress>>

    /** Every recorded episode and movie, newest first. */
    fun allProgress(): Flow<List<WatchProgress>>

    suspend fun get(mediaType: String, tmdbId: Int, season: Int, episode: Int): WatchProgress?

    /**
     * Persists progress without tying the write to the caller's lifecycle, so the final
     * checkpoint still lands when the player screen is closing.
     */
    fun record(progress: WatchProgress)

    /** Removes a title from Continue Watching while keeping its watched episodes. */
    suspend fun dismiss(mediaType: String, tmdbId: Int)

    suspend fun clearTitle(mediaType: String, tmdbId: Int)

    /** Forgets one episode, e.g. "Mark as unwatched". */
    suspend fun clearEpisode(mediaType: String, tmdbId: Int, season: Int, episode: Int)

    suspend fun clearAll()
}
