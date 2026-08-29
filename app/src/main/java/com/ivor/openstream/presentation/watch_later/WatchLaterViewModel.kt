package com.ivor.openstream.presentation.watch_later

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.domain.repository.WatchLaterRepository
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
