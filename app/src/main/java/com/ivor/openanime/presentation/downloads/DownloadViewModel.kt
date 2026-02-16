package com.ivor.openanime.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.local.entity.DownloadEntity
import com.ivor.openanime.data.repository.DownloadRepositoryImpl
import com.ivor.openanime.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder {
    NEWEST,
    NAME,
    SIZE
}

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder = _sortOrder.asStateFlow()

    private val _downloads: StateFlow<List<DownloadEntity>> = repository.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val downloads: StateFlow<List<DownloadEntity>> = combine(_downloads, _searchQuery, _sortOrder) { list, query, sort ->
        var result = list
        
        // 1. Filter
        if (query.isNotEmpty()) {
            result = result.filter { it.title.contains(query, ignoreCase = true) }
        }

        // 2. Sort
        result = when (sort) {
            SortOrder.NEWEST -> result.sortedByDescending { it.dateAdded }
            SortOrder.NAME -> result.sortedBy { it.title }
            SortOrder.SIZE -> result.sortedByDescending { it.totalBytes }
        }
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        startProgressSync()
    }
    
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onSortOrderChange(order: SortOrder) {
        _sortOrder.value = order
    }

    private fun startProgressSync() {
        viewModelScope.launch {
            while (isActive) {
                if (repository is DownloadRepositoryImpl) {
                    repository.syncProgress()
                }
                delay(1000) // Poll every second
            }
        }
    }

    fun removeDownload(downloadId: String) {
        viewModelScope.launch {
            repository.removeDownload(downloadId)
        }
    }
}
