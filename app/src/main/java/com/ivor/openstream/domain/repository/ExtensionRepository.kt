package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.SourceExtension
import kotlinx.coroutines.flow.StateFlow

interface ExtensionRepository {
    val extensions: StateFlow<List<SourceExtension>>

    fun install(extensionId: String)
    fun uninstall(extensionId: String)
    fun setEnabled(extensionId: String, enabled: Boolean)
    fun isProviderEnabled(providerId: String): Boolean
}
