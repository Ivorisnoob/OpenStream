package com.ivor.openstream.domain.repository

import android.net.Uri
import com.ivor.openstream.data.local.entity.DownloadEntity
import com.ivor.openstream.domain.model.DownloadTarget
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.flow.Flow

/** The source a queued download should use when it can: a provider, at the closest quality. */
data class SourcePreference(val providerId: String, val qualityRank: Int)

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    fun getDownloadByContent(tmdbId: Int, season: Int, episode: Int, mediaType: String): Flow<DownloadEntity?>

    fun getDownloadsForTitle(tmdbId: Int, mediaType: String): Flow<List<DownloadEntity>>

    /** Downloads from a source the user already picked, e.g. the one playing in the player. */
    suspend fun download(server: VideoServer, target: DownloadTarget)

    /**
     * Finds a downloadable source for each target and downloads it. Runs in the app's scope, so
     * leaving the screen that started it does not cancel the queue. With [preference], each target
     * downloads from that source when it has the episode, otherwise from the best one found.
     */
    fun enqueue(targets: List<DownloadTarget>, preference: SourcePreference? = null)

    fun pause(downloadId: String)

    fun resume(downloadId: String)

    /** Looks for a fresh source (stream links expire) and starts over. */
    fun retry(downloadId: String)

    suspend fun removeDownload(downloadId: String)

    suspend fun getPlaybackUri(downloadId: String): String?

    /** Exports a completed download to the device's public Downloads/Openstream folder. */
    suspend fun exportToStorage(downloadId: String): Result<Uri>
}
