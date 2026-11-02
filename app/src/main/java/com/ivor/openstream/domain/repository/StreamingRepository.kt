package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.flow.Flow

interface StreamingRepository {
    /**
     * Resolves servers from every active source. Backup (fallback) sources normally run only when
     * the direct ones return nothing; [includeFallbacks] queries them alongside, for when the
     * direct links resolved but refused to play. [preferLastWorking] asks the source that last
     * played this title (the movie, or any episode of the show) first and stops there when it has a
     * stream at the quality cap; below the cap the other sources are searched as well.
     */
    fun resolveServers(
        identity: MediaIdentity,
        includeFallbacks: Boolean = false,
        preferLastWorking: Boolean = false
    ): Flow<ServerResolution>
    suspend fun getServers(identity: MediaIdentity): List<VideoServer>
    suspend fun refreshServer(server: VideoServer): Result<VideoServer>
    fun rememberServer(identity: MediaIdentity, server: VideoServer)
    fun getRememberedServerId(identity: MediaIdentity): String?

    /** A stream [resolveServers] handed out for [identity] failed: search afresh next time. */
    fun forgetResolvedServers(identity: MediaIdentity)

    /** Drops every remembered search result ("Clear cache"). */
    fun clearResolvedServers()

    /** [server] actually played: its source is the one [resolveServers] asks first for this title. */
    fun rememberWorkingSource(identity: MediaIdentity, server: VideoServer)
}
