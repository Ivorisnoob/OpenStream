package com.ivor.openanime.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.local.entity.DownloadEntity
import com.ivor.openanime.data.repository.DownloadRepositoryImpl
import com.ivor.openanime.domain.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val repository: DownloadRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = repository.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        startProgressSync()
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
