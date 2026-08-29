package com.ivor.openstream.data.streaming

import android.content.SharedPreferences
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.StreamingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class StreamingRepositoryImpl @Inject constructor(
    providers: Set<@JvmSuppressWildcards StreamProvider>,
    private val idMappingService: IdMappingService,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val preferences: SharedPreferences
) : StreamingRepository {
    private val providers = providers.sortedBy(StreamProvider::priority)
    private val providerPriorities = providers.associate { it.id to it.priority }
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    override fun resolveServers(identity: MediaIdentity): Flow<ServerResolution> = channelFlow {
        val enrichedIdentity = idMappingService.enrich(identity)
        val enabledProviders = providers.filter {
            it.isEnabled && (consecutiveFailures[it.id] ?: 0) < CIRCUIT_BREAKER_THRESHOLD
        }
        val preferredServerId = preferences.getString(preferenceKey(identity), null)
        send(ServerResolution(totalProviders = enabledProviders.size))
        if (enabledProviders.isEmpty()) {
            send(ServerResolution(isComplete = true))
            return@channelFlow
        }

        val outcomes = Channel<ProviderOutcome>(enabledProviders.size)
        enabledProviders.forEach { provider ->
            launch(Dispatchers.IO) {
                val result = runCatching {
                    withTimeout(PROVIDER_TIMEOUT_MS) {
                        provider.resolve(enrichedIdentity).getOrThrow()
                    }
                }
                outcomes.send(ProviderOutcome(provider, result))
            }
        }

        var servers = emptyList<VideoServer>()
        val failedProviders = mutableListOf<String>()
        repeat(enabledProviders.size) { completedIndex ->
            val outcome = outcomes.receive()
            outcome.result.fold(
                onSuccess = { incoming ->
                    consecutiveFailures[outcome.provider.id] = 0
                    servers = ServerRanker.mergeAndRank(
                        existing = servers,
                        incoming = incoming,
                        providerPriorities = providerPriorities,
                        preferredServerId = preferredServerId
                    )
                },
                onFailure = {
                    consecutiveFailures.compute(outcome.provider.id) { _, count -> (count ?: 0) + 1 }
                    failedProviders += outcome.provider.displayName
                }
            )
            val completed = completedIndex + 1
            send(
                ServerResolution(
                    servers = servers,
                    completedProviders = completed,
                    totalProviders = enabledProviders.size,
                    failedProviders = failedProviders.toList(),
                    isComplete = completed == enabledProviders.size
                )
            )
        }
        outcomes.close()
    }

    override suspend fun getServers(identity: MediaIdentity): List<VideoServer> =
        resolveServers(identity).last().servers

    override suspend fun refreshServer(server: VideoServer): Result<VideoServer> =
        runCatching {
            val request = Request.Builder()
                .url(server.url)
                .header("Range", "bytes=0-1")
                .apply { server.headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    throw IOException("${server.name} returned HTTP ${response.code}")
                }
            }
            server.copy(resolvedAt = System.currentTimeMillis())
        }

    override fun rememberServer(identity: MediaIdentity, server: VideoServer) {
        preferences.edit().putString(preferenceKey(identity), server.id).apply()
    }

    private fun preferenceKey(identity: MediaIdentity): String =
        "last_stream_server:${identity.cacheKey}"

    private data class ProviderOutcome(
        val provider: StreamProvider,
        val result: Result<List<VideoServer>>
    )

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 20_000L
        const val CIRCUIT_BREAKER_THRESHOLD = 5
    }
}
