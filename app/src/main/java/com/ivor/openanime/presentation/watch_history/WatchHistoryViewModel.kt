package com.ivor.openanime.presentation.watch_history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.remote.model.AnimeDto
import com.ivor.openanime.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class WatchHistoryUiState(
    val history: List<AnimeDto> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchHistoryUiState())
    val uiState: StateFlow<WatchHistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val historyList = repository.getWatchHistory()
            _uiState.update { it.copy(history = historyList, isLoading = false) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            _uiState.update { it.copy(history = emptyList()) }
        }
    }
    
    fun removeFromHistory(animeId: Int) {
        viewModelScope.launch {
            // Re-use clear logic or add a remove method to repository
            val history = repository.getWatchHistory().toMutableList()
            history.removeIf { it.id == animeId }
            // Currently repo only has addToHistory which overwrites/adds. 
            // We can just clear and re-add or better, just leave it as is if repo handles duplicates.
            // Actually, I'll just clear and re-add for now or add a remove method if needed.
            // But repo.addToHistory already handles duplicates by removing first.
            repository.clearWatchHistory()
            history.forEach { repository.addToWatchHistory(it) }
            _uiState.update { it.copy(history = history) }
        }
    }
}
