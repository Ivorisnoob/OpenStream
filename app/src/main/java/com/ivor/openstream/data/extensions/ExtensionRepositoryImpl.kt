package com.ivor.openstream.data.extensions

import android.content.SharedPreferences
import com.ivor.openstream.domain.model.SourceExtension
import com.ivor.openstream.domain.model.SourceExtensionManifest
import com.ivor.openstream.domain.repository.ExtensionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionRepositoryImpl @Inject constructor(
    private val preferences: SharedPreferences
) : ExtensionRepository {
    private val catalog = BundledExtensionCatalog.extensions
    private val _extensions = MutableStateFlow(catalog.map(::readState))
    override val extensions: StateFlow<List<SourceExtension>> = _extensions.asStateFlow()

    @Synchronized
    override fun install(extensionId: String) {
        val manifest = requireManifest(extensionId)
        preferences.edit()
            .putBoolean(installedKey(manifest.id), true)
            .putBoolean(enabledKey(manifest.id), true)
            .apply()
        publishState()
    }

    @Synchronized
    override fun uninstall(extensionId: String) {
        val manifest = requireManifest(extensionId)
        preferences.edit()
            .putBoolean(installedKey(manifest.id), false)
            .putBoolean(enabledKey(manifest.id), false)
            .apply()
        publishState()
    }

    @Synchronized
    override fun setEnabled(extensionId: String, enabled: Boolean) {
        val current = _extensions.value.firstOrNull { it.manifest.id == extensionId }
            ?: throw IllegalArgumentException("Unknown extension: $extensionId")
        if (!current.isInstalled) return

        preferences.edit().putBoolean(enabledKey(extensionId), enabled).apply()
        publishState()
    }

    override fun isProviderEnabled(providerId: String): Boolean =
        _extensions.value.any { extension ->
            extension.isInstalled &&
                extension.isEnabled &&
                providerId in extension.manifest.providerIds
        }

    private fun readState(manifest: SourceExtensionManifest): SourceExtension {
        val installed = preferences.getBoolean(
            installedKey(manifest.id),
            manifest.installedByDefault
        )
        val enabled = installed && preferences.getBoolean(enabledKey(manifest.id), true)
        return SourceExtension(
            manifest = manifest,
            isInstalled = installed,
            isEnabled = enabled
        )
    }

    private fun publishState() {
        _extensions.value = catalog.map(::readState)
    }

    private fun requireManifest(extensionId: String): SourceExtensionManifest =
        catalog.firstOrNull { it.id == extensionId }
            ?: throw IllegalArgumentException("Unknown extension: $extensionId")

    private fun installedKey(extensionId: String) = "extension:$extensionId:installed"
    private fun enabledKey(extensionId: String) = "extension:$extensionId:enabled"
}
