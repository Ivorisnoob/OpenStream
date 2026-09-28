package com.ivor.openstream.presentation.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.local.entity.CustomListEntity
import com.ivor.openstream.data.local.entity.CustomListItemEntity
import com.ivor.openstream.data.repository.CustomListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomListUiState(
    val isLoading: Boolean = true,
    /** Null once loaded means the list was deleted. */
    val list: CustomListEntity? = null,
    val items: List<CustomListItemEntity> = emptyList()
)

@HiltViewModel
class CustomListViewModel @Inject constructor(
    private val repository: CustomListRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val listId: Long = checkNotNull(savedStateHandle.get<Long>("listId"))

    val uiState: StateFlow<CustomListUiState> = combine(repository.list(listId), repository.items(listId)) { list, items ->
        CustomListUiState(isLoading = false, list = list, items = items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomListUiState())

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(listId, name) }
    }

    fun delete() {
        viewModelScope.launch { repository.delete(listId) }
    }

    fun remove(item: CustomListItemEntity) {
        viewModelScope.launch { repository.remove(listId, item.mediaType, item.tmdbId) }
    }

    /** Undo: puts the title back with its original date so its position doesn't change. */
    fun restore(item: CustomListItemEntity) {
        viewModelScope.launch { repository.add(item) }
    }
}
