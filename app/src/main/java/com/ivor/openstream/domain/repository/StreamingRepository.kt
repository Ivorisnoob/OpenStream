package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.flow.Flow

interface StreamingRepository {
    fun resolveServers(identity: MediaIdentity): Flow<ServerResolution>
    suspend fun getServers(identity: MediaIdentity): List<VideoServer>
    suspend fun refreshServer(server: VideoServer): Result<VideoServer>
    fun rememberServer(identity: MediaIdentity, server: VideoServer)
}
