package com.ivor.openstream.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.domain.repository.ExtensionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val installedCount: Int = 0,
    val enabledCount: Int = 0,
    val availableCount: Int = 0,
    val updateCount: Int = 0,
    val repoCount: Int = 0,
    val isSyncing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val extensionRepository: ExtensionRepository
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = extensionRepository.catalog
        .map { catalog ->
            SettingsUiState(
                installedCount = catalog.installed.size,
                enabledCount = catalog.enabled.size,
                availableCount = catalog.extensions.size,
                updateCount = catalog.updatable.size,
                repoCount = catalog.repos.size,
                isSyncing = catalog.isSyncing
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun refresh() {
        viewModelScope.launch {
            runCatching { extensionRepository.refresh(force = true) }
        }
    }
}
