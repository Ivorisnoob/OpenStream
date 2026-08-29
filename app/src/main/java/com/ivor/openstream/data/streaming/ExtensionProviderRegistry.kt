package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.streaming.providers.VidkingDirectApi
import com.ivor.openstream.data.streaming.providers.VidkingDirectProvider
import com.ivor.openstream.data.streaming.providers.VidkingServerSpec
import com.ivor.openstream.data.streaming.providers.VidkingWebViewProvider
import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.ExtensionRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A provider that knows which marketplace extension it came from. */
class ExtensionStreamProvider(
    val extensionKey: String,
    private val delegate: StreamProvider,
    override val priority: Int
) : StreamProvider {
    override val id: String = delegate.id
    override val displayName: String = delegate.displayName
    override val isEnabled: Boolean = delegate.isEnabled
    override val isFallback: Boolean = delegate.isFallback

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> =
        delegate.resolve(identity)
}

/**
 * Builds runtime stream providers from installed extension manifests.
 *
 * This is the seam that makes the marketplace real: adding a source is a data change in a
 * repository index, not a new `@Provides` in a Dagger module.
 */
@Singleton
class ExtensionProviderRegistry @Inject constructor(
    private val extensionRepository: ExtensionRepository,
    private val vidkingApi: VidkingDirectApi,
    private val webViewProvider: VidkingWebViewProvider
) {
    private val cache = ConcurrentHashMap<String, ExtensionStreamProvider>()

    fun activeProviders(): List<ExtensionStreamProvider> =
        extensionRepository.activeExtensions()
            .mapNotNull(::providerFor)
            .distinctBy { it.id }
            .sortedBy { it.priority }

    fun recordOutcome(provider: ExtensionStreamProvider, success: Boolean) {
        extensionRepository.recordOutcome(provider.extensionKey, success)
    }

    private fun providerFor(extension: MarketplaceExtension): ExtensionStreamProvider? {
        val manifest = extension.manifest
        val engine = manifest.engine
        if (!engine.isRunnable) return null

        val cacheKey = "${manifest.key}@${manifest.versionCode}@${engine.type.key}@${engine.endpoint}"
        cache[cacheKey]?.let { return it }

        val delegate: StreamProvider = when (engine.type) {
            ExtensionEngineType.VIDKING_DIRECT -> VidkingDirectProvider(
                api = vidkingApi,
                spec = VidkingServerSpec(
                    id = manifest.id,
                    name = manifest.name,
                    endpoint = engine.endpoint,
                    priority = engine.priority,
                    language = engine.language,
                    qualityFilter = engine.qualityFilter
                )
            )
            ExtensionEngineType.VIDKING_WEBVIEW -> webViewProvider
            ExtensionEngineType.UNSUPPORTED -> return null
        }

        val provider = ExtensionStreamProvider(
            extensionKey = manifest.key,
            delegate = delegate,
            priority = engine.priority
        )
        cache[cacheKey] = provider
        return provider
    }
}
