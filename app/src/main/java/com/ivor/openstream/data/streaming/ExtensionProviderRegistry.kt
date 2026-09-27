package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.streaming.anime.AnikotoProvider
import com.ivor.openstream.data.streaming.anime.AnimeEpisodeMapper
import com.ivor.openstream.data.streaming.anime.AnimePaheProvider
import com.ivor.openstream.data.streaming.anime.AnimeSiteSpec
import com.ivor.openstream.data.streaming.anime.CloudflareClearance
import com.ivor.openstream.data.streaming.anime.MegaplayExtractor
import com.ivor.openstream.data.streaming.anime.ReAnimeProvider
import com.ivor.openstream.data.streaming.providers.VidkingDirectApi
import com.ivor.openstream.data.streaming.providers.VidkingDirectProvider
import com.ivor.openstream.data.streaming.providers.VidkingServerSpec
import com.ivor.openstream.data.streaming.providers.WebEmbedProvider
import com.ivor.openstream.data.streaming.providers.WebEmbedResolver
import com.ivor.openstream.data.streaming.providers.WebEmbedSpec
import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.domain.repository.ExtensionRepository
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
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
    private val webEmbedResolver: WebEmbedResolver,
    private val animeEpisodeMapper: AnimeEpisodeMapper,
    private val megaplayExtractor: MegaplayExtractor,
    private val cloudflareClearance: CloudflareClearance,
    @Named("StreamingClient") private val streamingClient: OkHttpClient,
    private val json: Json
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

        val cacheKey = "${manifest.key}@${manifest.versionCode}@${engine.type.key}@${engine.endpoint}" +
            "@${engine.movieUrl}@${engine.tvUrl}"
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
            ExtensionEngineType.VIDKING_WEBVIEW -> WebEmbedProvider(
                resolver = webEmbedResolver,
                spec = WebEmbedSpec.VIDKING_FALLBACK
            )
            ExtensionEngineType.WEB_EMBED -> WebEmbedProvider(
                resolver = webEmbedResolver,
                spec = WebEmbedSpec(
                    id = "web-${manifest.id}",
                    name = manifest.name,
                    movieUrl = engine.movieUrl,
                    tvUrl = engine.tvUrl,
                    priority = engine.priority,
                    isFallback = manifest.isFallback
                )
            )
            ExtensionEngineType.ANIKOTO -> AnikotoProvider(
                spec = animeSiteSpec(manifest),
                mapper = animeEpisodeMapper,
                client = streamingClient,
                json = json,
                megaplay = megaplayExtractor
            )
            ExtensionEngineType.REANIME -> ReAnimeProvider(
                spec = animeSiteSpec(manifest),
                mapper = animeEpisodeMapper,
                megaplay = megaplayExtractor
            )
            ExtensionEngineType.ANIMEPAHE -> AnimePaheProvider(
                spec = animeSiteSpec(manifest),
                mapper = animeEpisodeMapper,
                client = streamingClient,
                json = json,
                clearance = cloudflareClearance
            )
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

    private fun animeSiteSpec(manifest: ExtensionManifest) = AnimeSiteSpec(
        id = manifest.id,
        name = manifest.name,
        baseUrl = manifest.engine.endpoint,
        priority = manifest.engine.priority
    )
}
