package com.ivor.openanime.presentation.search

import android.content.SharedPreferences
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

enum class SearchFilter {
    ALL, MOVIE, TV
}

data class SearchUiState(
    val query: String = "",
    val history: List<String> = emptyList(),
    val searchResults: List<AnimeDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: SearchFilter = SearchFilter.ALL
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AnimeRepository,
    private val sharedPreferences: SharedPreferences,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val HISTORY_KEY = "search_history_list"

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val historyJson = sharedPreferences.getString(HISTORY_KEY, "[]") ?: "[]"
        try {
            val historyList = json.decodeFromString<List<String>>(historyJson)
            _uiState.update { it.copy(history = historyList) }
        } catch (_: Exception) {
            _uiState.update { it.copy(history = emptyList()) }
        }
    }

    fun onFilterSelected(filter: SearchFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            performSearch(query)
        }
    }

    fun onSearch(query: String) {
        if (query.isBlank()) return
        saveToHistory(query)
        performSearch(query)
    }

    private fun performSearch(query: String) {
        _uiState.update { it.copy(query = query, isLoading = true, error = null) }

        val filterString = when (_uiState.value.filter) {
            SearchFilter.ALL -> "all"
            SearchFilter.MOVIE -> "movie"
            SearchFilter.TV -> "tv"
        }

        viewModelScope.launch {
            repository.searchAnime(query, 1, filterString).fold(
                onSuccess = { animeList ->
                    _uiState.update { it.copy(isLoading = false, searchResults = animeList) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    private fun saveToHistory(query: String) {
        val currentList = _uiState.value.history.toMutableList()
        currentList.remove(query)
        currentList.add(0, query)
        if (currentList.size > 10) {
            currentList.removeAt(currentList.lastIndex)
        }

        _uiState.update { it.copy(history = currentList) }
        persistHistory(currentList)
    }

    fun clearHistory() {
        _uiState.update { it.copy(history = emptyList()) }
        sharedPreferences.edit().remove(HISTORY_KEY).apply()
    }

    fun removeHistoryItem(query: String) {
        val currentList = _uiState.value.history.toMutableList()
        currentList.remove(query)
        _uiState.update { it.copy(history = currentList) }
        persistHistory(currentList)
    }

    private fun persistHistory(list: List<String>) {
        viewModelScope.launch {
            val jsonString = json.encodeToString(list)
            sharedPreferences.edit().putString(HISTORY_KEY, jsonString).apply()
        }
    }
}
