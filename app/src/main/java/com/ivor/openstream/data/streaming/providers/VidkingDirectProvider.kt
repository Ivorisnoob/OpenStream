package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer

class VidkingDirectProvider(
    private val api: VidkingDirectApi,
    private val spec: VidkingServerSpec
) : StreamProvider {
    override val id: String = "vidking-${spec.id}"
    override val displayName: String = "Vidking · ${spec.name}"
    override val priority: Int = spec.priority
    override val isEnabled: Boolean = true

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> =
        runCatching { api.resolve(spec, identity) }
}
