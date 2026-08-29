package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer

interface StreamProvider {
    val id: String
    val displayName: String
    val priority: Int
    val isEnabled: Boolean
    val isFallback: Boolean
        get() = false

    suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>>
}
