package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.ExtensionCatalog
import com.ivor.openstream.domain.model.ExtensionRepo
import com.ivor.openstream.domain.model.MarketplaceExtension
import kotlinx.coroutines.flow.StateFlow

/**
 * Marketplace of installable source extensions served by one or more remote repositories.
 */
interface ExtensionRepository {
    val catalog: StateFlow<ExtensionCatalog>

    /** Re-fetches every repository index. [force] ignores the staleness window. */
    suspend fun refresh(force: Boolean = false)

    suspend fun addRepo(url: String): Result<ExtensionRepo>

    suspend fun removeRepo(repoId: String): Result<Unit>

    fun install(key: String)

    fun uninstall(key: String)

    fun setEnabled(key: String, enabled: Boolean)

    /** Accepts the newest published version of an already installed extension. */
    fun update(key: String)

    /** Updates every installed extension that has a newer version and returns how many changed. */
    fun updateAll(): Int

    /** Installed + enabled + runnable on this build, ordered by engine priority. */
    fun activeExtensions(): List<MarketplaceExtension>

    /** Records whether an extension produced a usable stream, feeding the reliability signal. */
    fun recordOutcome(key: String, success: Boolean)
}
