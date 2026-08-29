package com.ivor.openstream.data.extensions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The official repository ships inside the APK as well as being served over HTTP, so a fresh
 * install has a working catalog before it ever reaches the network. The bundled copy is the same
 * document published at [OFFICIAL_REPO_URL].
 */
@Singleton
class BundledExtensionCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: ExtensionIndexParser
) {
    fun load(): CachedRepoSnapshot? = runCatching {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val repo = parser.parseRepo(raw)
        CachedRepoSnapshot(
            url = OFFICIAL_REPO_URL,
            name = repo.name?.trim().orEmpty().ifEmpty { "OpenStream Official" },
            description = repo.description?.trim().orEmpty(),
            iconUrl = repo.iconUrl,
            website = repo.website,
            fetchedAt = 0L,
            extensions = repo.extensions
        )
    }.getOrNull()

    companion object {
        const val ASSET_PATH = "extensions/official-repo.json"
        const val OFFICIAL_REPO_URL =
            "https://raw.githubusercontent.com/Ivorisnoob/OpenStream/main/extensions/index.json"
        val OFFICIAL_REPO_ID: String = RepoUrlNormalizer.repoId(OFFICIAL_REPO_URL)
    }
}
