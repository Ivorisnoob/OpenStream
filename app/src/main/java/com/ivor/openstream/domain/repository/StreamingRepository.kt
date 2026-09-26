package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.flow.Flow

interface StreamingRepository {
    /**
     * Resolves servers from every active source. Backup (fallback) sources normally run only when
     * the direct ones return nothing; [includeFallbacks] queries them alongside, for when the
     * direct links resolved but refused to play.
     */
    fun resolveServers(identity: MediaIdentity, includeFallbacks: Boolean = false): Flow<ServerResolution>
    suspend fun getServers(identity: MediaIdentity): List<VideoServer>
    suspend fun refreshServer(server: VideoServer): Result<VideoServer>
    fun rememberServer(identity: MediaIdentity, server: VideoServer)
}
