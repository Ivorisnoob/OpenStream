package com.ivor.openanime.presentation.watch_later

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openanime.data.local.entity.WatchLaterEntity
import com.ivor.openanime.domain.repository.WatchLaterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchLaterViewModel @Inject constructor(
    private val repository: WatchLaterRepository
) : ViewModel() {

    val watchLaterList: StateFlow<List<WatchLaterEntity>> = repository.getWatchLaterList()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeFromWatchLater(item: WatchLaterEntity) {
        viewModelScope.launch {
            repository.removeFromWatchLater(item)
        }
    }
}
