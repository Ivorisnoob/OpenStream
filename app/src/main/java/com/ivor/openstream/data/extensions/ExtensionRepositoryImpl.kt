package com.ivor.openstream.data.extensions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import com.ivor.openstream.R
import com.ivor.openstream.domain.model.ExtensionCatalog
import com.ivor.openstream.domain.model.ExtensionManifest
import com.ivor.openstream.domain.model.ExtensionRepo
import com.ivor.openstream.domain.model.ExtensionUsage
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.repository.ExtensionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
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
    private val bundled: BundledExtensionCatalog,
    @ApplicationContext private val context: Context
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

    /**
     * Runs in the repository's own scope, not the caller's: a screen that starts a refresh and is
     * then closed must not cut it short halfway through the repositories.
     */
    override suspend fun refresh(force: Boolean) {
        scope.async { refreshNow(force) }.await()
    }

    private suspend fun refreshNow(force: Boolean) {
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
                    .onSuccess { fetched ->
                        cache.write(repoId, fetched)
                        val snapshot = if (repoId == BundledExtensionCatalog.OFFICIAL_REPO_ID) {
                            bundled.reconcile(fetched)
                        } else {
                            fetched
                        }
                        synchronized(lock) {
                            snapshots[repoId] = snapshot
                            repoErrors[repoId] = null
                        }
                    }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        val message = describeFailure(error, R.string.st_could_not_reach_repo)
                        synchronized(lock) { repoErrors[repoId] = message }
                    }
            }
            store.setLastSyncedAt(System.currentTimeMillis())
            synchronized(lock) { rebuildManifests() }
            seedDefaultInstalls()
            publish(isSyncing = false)
        }
    }

    /**
     * Failures carry a [RepoException] with a message fit to show. Like [refresh], the work runs in
     * the repository's own scope, so leaving the screen does not abandon an add halfway.
     */
    override suspend fun addRepo(url: String): Result<ExtensionRepo> =
        scope.async {
            try {
                addRepoNow(url)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(RepoException(describeFailure(error, R.string.st_could_not_add_repo)))
            }
        }.await()

    private suspend fun addRepoNow(url: String): Result<ExtensionRepo> {
        ensureLoaded()
        val normalized = RepoUrlNormalizer.normalize(url)
            ?: return Result.failure(RepoException(context.getString(R.string.st_bad_repo_link)))
        val repoId = RepoUrlNormalizer.repoId(normalized)
        if (synchronized(lock) { snapshots.containsKey(repoId) }) {
            return Result.failure(RepoException(context.getString(R.string.st_repo_already_added)))
        }

        val fetched = client.fetch(normalized)
        if (fetched.extensions.isEmpty()) {
            return Result.failure(RepoException(context.getString(R.string.st_repo_no_extensions)))
        }
        cache.write(repoId, fetched)
        store.addCustomRepo(CustomRepoRecord(id = repoId, url = normalized, name = fetched.name))
        val repo = synchronized(lock) {
            snapshots[repoId] = fetched
            repoErrors[repoId] = null
            rebuildManifests()
            toRepo(repoId, fetched)
        }
        // A pasted Stremio add-on is ready to use without a separate install step.
        seedDefaultInstalls()
        publish(isSyncing = false)
        return Result.success(repo)
    }

    override suspend fun removeRepo(repoId: String): Result<Unit> {
        ensureLoaded()
        if (repoId == BundledExtensionCatalog.OFFICIAL_REPO_ID) {
            return Result.failure(RepoException(context.getString(R.string.st_official_cannot_remove)))
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

    /** Already in source order: [publish] sorts the catalog that way. */
    override fun activeExtensions(): List<MarketplaceExtension> {
        ensureLoaded()
        return _catalog.value.extensions.filter { it.isActive }
    }

    override fun setSourceOrder(keys: List<String>) {
        ensureLoaded()
        // Keep ranks for sources not in this list (uninstalled ones the user may reinstall).
        val rest = store.sourceOrder().filterNot { it in keys }
        store.setSourceOrder(keys + rest)
        publish(isSyncing = _catalog.value.isSyncing)
    }

    override fun recordOutcome(key: String, success: Boolean) {
        if (!loaded) return
        // Called once per provider on every stream resolve, from whichever thread collects the
        // resolution flow — keep the write off it.
        scope.launch {
            store.recordOutcome(key, success)
            publish(isSyncing = _catalog.value.isSyncing)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val officialId = BundledExtensionCatalog.OFFICIAL_REPO_ID
            val official = cache.read(officialId)?.let(bundled::reconcile)
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
        val order = store.sourceOrder().withIndex().associate { (index, key) -> key to index }
        // The user's ranking first; the rest by the catalog's priority, as before ranking existed.
        val ranked = manifests.sortedWith(
            compareBy<ExtensionManifest> { order[it.key] ?: Int.MAX_VALUE }
                .thenBy { it.engine.priority }
                .thenBy { it.name.lowercase() }
        )
        val extensions = ranked.map { manifest ->
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

    /**
     * A plain, localized reason for a failed fetch; the exception's own text (often English, or
     * internal like an HTTP status) is never shown. [fallback] covers anything unexpected.
     */
    private fun describeFailure(error: Throwable, @StringRes fallback: Int): String = when (error) {
        is RepoException -> error.message ?: context.getString(fallback)
        is IOException -> context.getString(
            if (isOnline()) R.string.st_could_not_reach_repo else R.string.st_repo_no_internet
        )
        // Also kotlinx.serialization's errors: the link answered with something that isn't JSON.
        is IllegalArgumentException -> context.getString(R.string.st_repo_not_valid)
        else -> context.getString(fallback)
    }

    private fun isOnline(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
