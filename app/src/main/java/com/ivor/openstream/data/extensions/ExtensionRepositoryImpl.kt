package com.ivor.openstream.data.extensions

import com.ivor.openstream.domain.model.ExtensionCatalog
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.ExtensionRepo
import com.ivor.openstream.domain.model.ExtensionUsage
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.repository.ExtensionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Marketplace backed by remote repository indexes.
 *
 * The official repository is bundled in the APK and refreshed over HTTP; users can add more
 * repositories by URL. Nothing here downloads code — a manifest only selects and configures an
 * engine that already ships with the app, which is what keeps third-party repositories safe.
 */
@Singleton
class ExtensionRepositoryImpl @Inject constructor(
    private val client: ExtensionRepoClient,
    private val parser: ExtensionIndexParser,
    private val cache: ExtensionCacheStore,
    private val store: ExtensionStateStore,
    private val bundled: BundledExtensionCatalog
) : ExtensionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val lock = Any()

    private val snapshots = LinkedHashMap<String, CachedRepoSnapshot>()
    private val repoErrors = mutableMapOf<String, String?>()
    @Volatile
    private var manifests: List<ExtensionManifest> = emptyList()

    @Volatile
    private var loaded = false

    private val _catalog = MutableStateFlow(ExtensionCatalog())
    override val catalog: StateFlow<ExtensionCatalog> = _catalog.asStateFlow()

    init {
        scope.launch {
            ensureLoaded()
            runCatching { refresh(force = false) }
        }
    }

    override suspend fun refresh(force: Boolean) {
        ensureLoaded()
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            val lastSync = store.lastSyncedAt()
            val isStale = now - lastSync >= STALE_WINDOW_MS
            if (!force && !isStale && manifests.isNotEmpty()) return

            publish(isSyncing = true)
            val targets = synchronized(lock) { snapshots.map { (id, snapshot) -> id to snapshot.url } }
            targets.forEach { (repoId, url) ->
                runCatching { client.fetch(url) }
                    .onSuccess { snapshot ->
                        cache.write(repoId, snapshot)
                        synchronized(lock) {
                            snapshots[repoId] = snapshot
                            repoErrors[repoId] = null
                        }
                    }
                    .onFailure { error ->
                        synchronized(lock) {
                            repoErrors[repoId] = error.message ?: "Could not reach repository"
                        }
                    }
            }
            store.setLastSyncedAt(System.currentTimeMillis())
            synchronized(lock) { rebuildManifests() }
            seedDefaultInstalls()
            publish(isSyncing = false)
        }
    }

    override suspend fun addRepo(url: String): Result<ExtensionRepo> {
        ensureLoaded()
        val normalized = RepoUrlNormalizer.normalize(url)
            ?: return Result.failure(IllegalArgumentException("That does not look like a repository link"))
        val repoId = RepoUrlNormalizer.repoId(normalized)
        if (synchronized(lock) { snapshots.containsKey(repoId) }) {
            return Result.failure(IllegalStateException("That repository is already added"))
        }

        return runCatching { client.fetch(normalized) }
            .mapCatching { snapshot ->
                if (snapshot.extensions.isEmpty()) {
                    throw IllegalStateException("Repository published no extensions")
                }
                cache.write(repoId, snapshot)
                store.addCustomRepo(CustomRepoRecord(id = repoId, url = normalized, name = snapshot.name))
                val repo = synchronized(lock) {
                    snapshots[repoId] = snapshot
                    repoErrors[repoId] = null
                    rebuildManifests()
                    toRepo(repoId, snapshot)
                }
                publish(isSyncing = false)
                repo
            }
    }

    override suspend fun removeRepo(repoId: String): Result<Unit> {
        ensureLoaded()
        if (repoId == BundledExtensionCatalog.OFFICIAL_REPO_ID) {
            return Result.failure(IllegalStateException("The official repository cannot be removed"))
        }
        store.removeCustomRepo(repoId)
        store.removeInstallsForRepo(repoId)
        cache.delete(repoId)
        synchronized(lock) {
            snapshots.remove(repoId)
            repoErrors.remove(repoId)
            rebuildManifests()
        }
        publish(isSyncing = false)
        return Result.success(Unit)
    }

    override fun install(key: String) {
        ensureLoaded()
        val manifest = manifestFor(key) ?: return
        if (!manifest.isSupported) return
        store.putInstall(
            key,
            InstallRecord(
                versionCode = manifest.versionCode,
                enabled = true,
                installedAt = System.currentTimeMillis()
            )
        )
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun uninstall(key: String) {
        ensureLoaded()
        store.removeInstall(key)
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun setEnabled(key: String, enabled: Boolean) {
        ensureLoaded()
        val record = store.installs()[key] ?: return
        store.putInstall(key, record.copy(enabled = enabled))
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun update(key: String) {
        ensureLoaded()
        val manifest = manifestFor(key) ?: return
        val record = store.installs()[key] ?: return
        store.putInstall(key, record.copy(versionCode = manifest.versionCode))
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun updateAll(): Int {
        ensureLoaded()
        val installs = store.installs()
        val updates = manifests
            .filter { manifest ->
                val record = installs[manifest.key]
                record != null && manifest.versionCode > record.versionCode
            }
            .associate { manifest ->
                manifest.key to installs.getValue(manifest.key).copy(versionCode = manifest.versionCode)
            }
        if (updates.isEmpty()) return 0
        store.putInstalls(updates)
        publish(isSyncing = _catalog.value.isSyncing)
        return updates.size
    }

    override fun activeExtensions(): List<MarketplaceExtension> {
        ensureLoaded()
        return _catalog.value.extensions
            .filter { it.isActive }
            .sortedBy { it.manifest.engine.priority }
    }

    override fun recordOutcome(key: String, success: Boolean) {
        if (!loaded) return
        store.recordOutcome(key, success)
        publish(isSyncing = _catalog.value.isSyncing)
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val officialId = BundledExtensionCatalog.OFFICIAL_REPO_ID
            val official = cache.read(officialId)
                ?: bundled.load()
                ?: CachedRepoSnapshot(url = BundledExtensionCatalog.OFFICIAL_REPO_URL, name = "OpenStream Official")
            snapshots[officialId] = official

            store.customRepos().forEach { record ->
                snapshots[record.id] = cache.read(record.id)
                    ?: CachedRepoSnapshot(url = record.url, name = record.name.ifEmpty { record.url })
            }
            rebuildManifests()
            loaded = true
        }
        migrateLegacyInstalls()
        seedDefaultInstalls()
        publish(isSyncing = _catalog.value.isSyncing)
    }

    /** Carries over the pre-marketplace on/off state so upgrading users keep their line-up. */
    private fun migrateLegacyInstalls() {
        val officialId = BundledExtensionCatalog.OFFICIAL_REPO_ID
        val official = manifests.filter { it.repoId == officialId }
        if (official.isEmpty()) return

        val installs = store.installs()
        val migrated = official.mapNotNull { manifest ->
            if (installs.containsKey(manifest.key)) return@mapNotNull null
            val legacyEnabled = store.legacyInstallState(manifest.id) ?: return@mapNotNull null
            manifest.key to InstallRecord(
                versionCode = manifest.versionCode,
                enabled = legacyEnabled,
                installedAt = System.currentTimeMillis()
            )
        }.toMap()

        if (migrated.isNotEmpty()) store.putInstalls(migrated)
        store.clearLegacyState(official.map { it.id })
    }

    /**
     * Extensions flagged `installedByDefault` are installed once, then the user is in charge —
     * a default that was removed must not come back on the next repository refresh.
     */
    private fun seedDefaultInstalls() {
        val installs = store.installs()
        val alreadySeeded = store.seededKeys()
        val candidates = manifests.filter { it.installedByDefault && it.isSupported }
        val seeded = candidates
            .filter { !installs.containsKey(it.key) && it.key !in alreadySeeded }
            .associate { manifest ->
                manifest.key to InstallRecord(
                    versionCode = manifest.versionCode,
                    enabled = true,
                    installedAt = System.currentTimeMillis()
                )
            }
        if (seeded.isNotEmpty()) store.putInstalls(seeded)
        store.markSeeded(candidates.map { it.key })
    }

    private fun rebuildManifests() {
        manifests = snapshots.entries
            .flatMap { (repoId, snapshot) ->
                snapshot.extensions.mapNotNull { parser.toManifest(it, repoId) }
            }
            .distinctBy { it.key }
    }

    private fun manifestFor(key: String): ExtensionManifest? = manifests.firstOrNull { it.key == key }

    private fun publish(isSyncing: Boolean) {
        val installs = store.installs()
        val usage = store.usage()
        val extensions = manifests.map { manifest ->
            val record = installs[manifest.key]
            val stats = usage[manifest.key]
            MarketplaceExtension(
                manifest = manifest,
                isInstalled = record != null,
                isEnabled = record?.enabled == true,
                installedVersionCode = record?.versionCode ?: 0,
                installedAt = record?.installedAt ?: 0L,
                usage = ExtensionUsage(
                    successes = stats?.successes ?: 0,
                    failures = stats?.failures ?: 0
                )
            )
        }
        val repos = synchronized(lock) {
            snapshots.map { (repoId, snapshot) -> toRepo(repoId, snapshot) }
        }
        _catalog.value = ExtensionCatalog(
            repos = repos,
            extensions = extensions,
            isSyncing = isSyncing,
            lastSyncedAt = store.lastSyncedAt(),
            syncError = repos.firstNotNullOfOrNull { it.error }
        )
    }

    private fun toRepo(repoId: String, snapshot: CachedRepoSnapshot) = ExtensionRepo(
        id = repoId,
        url = snapshot.url,
        name = snapshot.name.ifEmpty { snapshot.url },
        description = snapshot.description,
        iconUrl = snapshot.iconUrl,
        website = snapshot.website,
        isBuiltIn = repoId == BundledExtensionCatalog.OFFICIAL_REPO_ID,
        lastSyncedAt = snapshot.fetchedAt,
        extensionCount = snapshot.extensions.size,
        error = repoErrors[repoId]
    )

    private companion object {
        val STALE_WINDOW_MS = 6 * 60 * 60 * 1000L
    }
}
