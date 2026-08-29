package com.ivor.openstream.presentation.settings

import androidx.lifecycle.ViewModel
import com.ivor.openstream.domain.repository.ExtensionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val extensionRepository: ExtensionRepository
) : ViewModel() {
    val extensions = extensionRepository.extensions

    fun install(extensionId: String) {
        extensionRepository.install(extensionId)
    }

    fun uninstall(extensionId: String) {
        extensionRepository.uninstall(extensionId)
    }

    fun setEnabled(extensionId: String, enabled: Boolean) {
        extensionRepository.setEnabled(extensionId, enabled)
    }
}
