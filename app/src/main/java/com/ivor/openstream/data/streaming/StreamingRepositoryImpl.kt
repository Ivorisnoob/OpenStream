package com.ivor.openstream.data.streaming

import android.content.SharedPreferences
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.StreamingRepository
import android.util.Log
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.SourceSearchMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class StreamingRepositoryImpl @Inject constructor(
    private val providerRegistry: ExtensionProviderRegistry,
    private val idMappingService: IdMappingService,
    @Named("StreamingClient") private val client: OkHttpClient,
    private val preferences: SharedPreferences,
    private val appSettings: AppSettingsStore
) : StreamingRepository {
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    /** When a provider's breaker last opened; it is retried once [BREAKER_COOLDOWN_MS] has passed. */
    private val breakerOpenedAt = ConcurrentHashMap<String, Long>()

    /**
     * Provider work runs here rather than inside the flow, so a provider stuck in a blocking HTTP
     * call can't hold the flow open past its deadline; it is cancelled when it stops mattering.
     */
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * By default every installed provider runs at once and servers are sent as each one answers,
     * so playback can start on the first. Fallback providers join when direct ones have found
     * nothing, either once all of them are done or after [FALLBACK_HEAD_START_MS], whichever comes
     * first. With [SourceSearchMode.IN_ORDER] providers run one at a time in the user's order and
     * the search stops at the first that finds something ("Find more" then searches the rest).
     *
     * With [preferLastWorking] the source that last played something ([rememberWorkingSource]) is
     * asked first, on its own. If it has streams the search ends there; if it has none, or has not
     * answered within [REMEMBERED_HEAD_START_MS], the search above runs as usual.
     */
    override fun resolveServers(
        identity: MediaIdentity,
        includeFallbacks: Boolean,
        preferLastWorking: Boolean
    ): Flow<ServerResolution> = channelFlow {
        val installedProviders = withContext(Dispatchers.IO) { providerRegistry.activeProviders() }
        val providerPriorities = installedProviders.associate { it.id to it.priority }
        val installedEnabled = installedProviders.filter { it.isEnabled }
        // Never let the breaker leave nothing to try: then everything gets another go.
        val enabledProviders = installedEnabled.filterNot { isBreakerOpen(it.id) }.ifEmpty { installedEnabled }
        // Not when everything is being searched on purpose ("Find more", failover).
        val remembered = if (preferLastWorking && !includeFallbacks) {
            lastWorkingSourceId(identity)?.let { id -> enabledProviders.firstOrNull { it.id == id } }
        } else {
            null
        }
        val otherProviders = if (remembered == null) {
            enabledProviders
        } else {
            enabledProviders.filterNot { it.id == remembered.id }
        }
        val directProviders = if (includeFallbacks) {
            otherProviders
        } else {
            otherProviders.filterNot(ExtensionStreamProvider::isFallback)
        }
        val fallbackProviders = if (includeFallbacks) {
            emptyList()
        } else {
            otherProviders.filter(ExtensionStreamProvider::isFallback)
        }
        val inOrder = appSettings.current.sourceSearchMode == SourceSearchMode.IN_ORDER
        val firstStageProviders = directProviders.ifEmpty { fallbackProviders }
        val deferredFallbackProviders = fallbackProviders.takeIf { directProviders.isNotEmpty() }.orEmpty()
        // Shown straight away, before the id lookup, so the screen says sources are being searched.
        send(
            ServerResolution(
                totalProviders = when {
                    inOrder -> enabledProviders.size
                    remembered != null -> 1
                    else -> firstStageProviders.size
                }
            )
        )
        if (remembered == null && firstStageProviders.isEmpty()) {
            send(ServerResolution(isComplete = true))
            return@channelFlow
        }

        // Providers that only know IMDb ids need this; the rest must not wait long on TMDB for it.
        val enrichedIdentity = withTimeoutOrNull(ID_LOOKUP_TIMEOUT_MS) { idMappingService.enrich(identity) } ?: identity
        val preferredServerId = preferences.getString(preferenceKey(identity), null)

        var servers = emptyList<VideoServer>()
        val failedProviders = mutableListOf<String>()

        fun absorb(outcome: ResolutionEvent.Outcome) {
            outcome.result.fold(
                onSuccess = { incoming ->
                    consecutiveFailures.remove(outcome.provider.id)
                    breakerOpenedAt.remove(outcome.provider.id)
                    providerRegistry.recordOutcome(outcome.provider, incoming.isNotEmpty())
                    if (incoming.isEmpty()) {
                        failedProviders += outcome.provider.displayName
                    } else {
                        servers = ServerRanker.mergeAndRank(
                            existing = servers,
                            incoming = incoming,
                            providerPriorities = providerPriorities,
                            preferredServerId = preferredServerId,
                            providerFirst = inOrder
                        )
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "${outcome.provider.displayName} failed: ${error.message}")
                    recordFailure(outcome.provider.id)
                    providerRegistry.recordOutcome(outcome.provider, false)
                    failedProviders += outcome.provider.displayName
                }
            )
        }

        if (inOrder && !includeFallbacks) {
            // The user's order exactly, fallbacks included wherever they were placed; only the
            // source that played last time jumps the queue.
            val ordered = listOfNotNull(remembered) + otherProviders
            ordered.forEachIndexed { index, provider ->
                absorb(runProvider(provider, enrichedIdentity))
                val found = servers.isNotEmpty()
                send(progress(servers, index + 1, ordered.size, failedProviders, isComplete = found || index == ordered.lastIndex))
                if (found) return@channelFlow
            }
            return@channelFlow
        }

        val events = Channel<ResolutionEvent>(Channel.UNLIMITED)
        var pending = 0
        var total = 0
        var fallbacksStarted = false
        var headStart: Job? = null
        // False while the remembered source is the only one that has been asked.
        var searchStarted = false

        fun start(providers: List<ExtensionStreamProvider>) {
            pending += providers.size
            total += providers.size
            providers.forEach { provider -> launch { events.send(runProvider(provider, enrichedIdentity)) } }
        }

        fun startFallbacks() {
            if (fallbacksStarted || deferredFallbackProviders.isEmpty()) return
            fallbacksStarted = true
            start(deferredFallbackProviders)
        }

        /** The full search: every direct provider at once, fallbacks after their head start. */
        fun startSearch() {
            searchStarted = true
            start(firstStageProviders)
            if (deferredFallbackProviders.isNotEmpty()) {
                headStart = launch {
                    delay(FALLBACK_HEAD_START_MS)
                    events.send(ResolutionEvent.FallbackDeadline)
                }
            }
        }

        val rememberedDeadline = if (remembered != null) {
            start(listOf(remembered))
            launch {
                delay(REMEMBERED_HEAD_START_MS)
                events.send(ResolutionEvent.RememberedDeadline)
            }
        } else {
            startSearch()
            null
        }

        while (pending > 0) {
            when (val event = events.receive()) {
                // The remembered source is taking its time: stop waiting on it alone.
                ResolutionEvent.RememberedDeadline -> if (!searchStarted) {
                    startSearch()
                    send(progress(servers, total - pending, total, failedProviders, isComplete = false))
                }
                ResolutionEvent.FallbackDeadline -> if (servers.isEmpty()) {
                    startFallbacks()
                    send(progress(servers, total - pending, total, failedProviders, isComplete = false))
                }
                is ResolutionEvent.Outcome -> {
                    pending--
                    absorb(event)
                    if (!searchStarted) {
                        // The remembered source answered first. With streams that is the whole
                        // answer (nothing else is pending); with none, everything else is asked.
                        rememberedDeadline?.cancel()
                        if (servers.isEmpty()) startSearch()
                    }
                    // Direct providers are all done and found nothing: fallbacks are the last hope.
                    if (pending == 0 && servers.isEmpty()) startFallbacks()
                    send(progress(servers, total - pending, total, failedProviders, isComplete = pending == 0))
                }
            }
        }
        rememberedDeadline?.cancel()
        headStart?.cancel()
        events.close()
    }

    /**
     * One provider's answer, or a timeout failure at [PROVIDER_TIMEOUT_MS] even if it is stuck in
     * blocking I/O (coroutine timeouts can't interrupt OkHttp's execute(), but awaiting can stop).
     * When the caller goes away this throws CancellationException, and nothing is recorded:
     * leaving a screen is not the provider's fault.
     */
    private suspend fun runProvider(provider: ExtensionStreamProvider, identity: MediaIdentity): ResolutionEvent.Outcome {
        val work = providerScope.async {
            try {
                provider.resolve(identity)
            } catch (cancelled: CancellationException) {
                // Its own internal timeout, not us cancelling it: that is a failure to report.
                if (!isActive) throw cancelled
                Result.failure(IOException("Timed out", cancelled))
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
        try {
            val result = try {
                withTimeoutOrNull(PROVIDER_TIMEOUT_MS) { work.await() }
                    ?: Result.failure(IOException("No answer within ${PROVIDER_TIMEOUT_MS / 1000} s"))
            } catch (cancelled: CancellationException) {
                // Rethrows only if the caller is really gone; otherwise every provider must
                // produce an outcome, or the resolution would wait for it forever.
                currentCoroutineContext().ensureActive()
                Result.failure(IOException("Cancelled", cancelled))
            }
            return ResolutionEvent.Outcome(provider, result)
        } finally {
            work.cancel()
        }
    }

    private fun progress(
        servers: List<VideoServer>,
        completed: Int,
        total: Int,
        failedProviders: List<String>,
        isComplete: Boolean
    ) = ServerResolution(
        servers = servers,
        completedProviders = completed,
        totalProviders = total,
        failedProviders = failedProviders.toList(),
        isComplete = isComplete
    )

    private fun recordFailure(providerId: String) {
        val failures = consecutiveFailures.merge(providerId, 1) { old, added -> old + added } ?: 1
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) breakerOpenedAt[providerId] = System.currentTimeMillis()
    }

    /** Skipped after repeated failures, but only for a while: networks and sites recover. */
    private fun isBreakerOpen(providerId: String): Boolean {
        if ((consecutiveFailures[providerId] ?: 0) < CIRCUIT_BREAKER_THRESHOLD) return false
        val openedAt = breakerOpenedAt[providerId] ?: return false
        return System.currentTimeMillis() - openedAt < BREAKER_COOLDOWN_MS
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

    override fun rememberWorkingSource(identity: MediaIdentity, server: VideoServer) {
        if (server.providerId.isBlank()) return
        preferences.edit()
            .putString("$WORKING_SOURCE_KEY:${identity.tmdbType}", server.providerId)
            .putString(WORKING_SOURCE_KEY, server.providerId)
            .apply()
    }

    /** The source that last played this kind of title (movie or show), else the last to play anything. */
    private fun lastWorkingSourceId(identity: MediaIdentity): String? =
        preferences.getString("$WORKING_SOURCE_KEY:${identity.tmdbType}", null)
            ?: preferences.getString(WORKING_SOURCE_KEY, null)

    private sealed interface ResolutionEvent {
        data class Outcome(val provider: ExtensionStreamProvider, val result: Result<List<VideoServer>>) : ResolutionEvent

        /** Direct providers have had [FALLBACK_HEAD_START_MS] on their own. */
        data object FallbackDeadline : ResolutionEvent

        /** The remembered source has had [REMEMBERED_HEAD_START_MS] on its own. */
        data object RememberedDeadline : ResolutionEvent
    }

    private companion object {
        const val TAG = "StreamingRepository"

        /** Long enough for a WebView source (native hoster pass, then up to 17 s in the WebView). */
        const val PROVIDER_TIMEOUT_MS = 25_000L
        const val ID_LOOKUP_TIMEOUT_MS = 8_000L
        const val FALLBACK_HEAD_START_MS = 8_000L

        /** How long the source that played last time is waited on before the others start too. */
        const val REMEMBERED_HEAD_START_MS = 10_000L
        const val WORKING_SOURCE_KEY = "last_working_source"
        const val CIRCUIT_BREAKER_THRESHOLD = 5
        const val BREAKER_COOLDOWN_MS = 2 * 60_000L
    }
}
