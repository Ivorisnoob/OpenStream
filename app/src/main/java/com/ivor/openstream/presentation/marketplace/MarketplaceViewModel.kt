package com.ivor.openstream.presentation.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.extensions.MarketplaceRanker
import com.ivor.openstream.domain.model.ExtensionCatalog
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MarketplaceSort
import com.ivor.openstream.domain.repository.ExtensionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketplaceUiState(
    val catalog: ExtensionCatalog = ExtensionCatalog(),
    val results: List<MarketplaceExtension> = emptyList(),
    val charts: List<MarketplaceExtension> = emptyList(),
    val tags: List<String> = emptyList(),
    val query: String = "",
    val sort: MarketplaceSort = MarketplaceSort.POPULAR,
    val tag: String? = null,
    val message: String? = null
) {
    val installed: List<MarketplaceExtension> get() = catalog.installed
    val updatable: List<MarketplaceExtension> get() = catalog.updatable
    val isFiltered: Boolean get() = query.isNotBlank() || tag != null
}

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val repository: ExtensionRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(MarketplaceSort.POPULAR)
    private val tag = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<MarketplaceUiState> = combine(
        repository.catalog,
        query,
        sort,
        tag,
        message
    ) { catalog, currentQuery, currentSort, currentTag, currentMessage ->
        val filtered = MarketplaceRanker.search(
            MarketplaceRanker.filterByTag(catalog.extensions, currentTag),
            currentQuery
        )
        MarketplaceUiState(
            catalog = catalog,
            results = MarketplaceRanker.sort(filtered, currentSort),
            charts = MarketplaceRanker.topCharts(catalog.extensions),
            tags = MarketplaceRanker.tags(catalog.extensions),
            query = currentQuery,
            sort = currentSort,
            tag = currentTag,
            message = currentMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketplaceUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSort(value: MarketplaceSort) {
        sort.value = value
    }

    fun toggleTag(value: String) {
        tag.value = if (tag.value == value) null else value
    }

    fun clearTag() {
        tag.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refresh(force = true) }
                .onFailure { message.value = it.message ?: "Could not refresh repositories" }
        }
    }

    fun install(extension: MarketplaceExtension) {
        repository.install(extension.key)
        message.value = "${extension.manifest.name} installed"
    }

    fun uninstall(extension: MarketplaceExtension) {
        repository.uninstall(extension.key)
        message.value = "${extension.manifest.name} removed"
    }

    fun setEnabled(extension: MarketplaceExtension, enabled: Boolean) {
        repository.setEnabled(extension.key, enabled)
    }

    fun update(extension: MarketplaceExtension) {
        repository.update(extension.key)
        message.value = "${extension.manifest.name} updated to v${extension.manifest.versionName}"
    }

    fun updateAll() {
        val count = repository.updateAll()
        message.value = if (count == 0) "Everything is up to date" else "Updated $count extensions"
    }

    fun addRepo(url: String) {
        viewModelScope.launch {
            repository.addRepo(url)
                .onSuccess { message.value = "Added ${it.name}" }
                .onFailure { message.value = it.message ?: "Could not add that repository" }
        }
    }

    fun removeRepo(repoId: String) {
        viewModelScope.launch {
            repository.removeRepo(repoId)
                .onSuccess { message.value = "Repository removed" }
                .onFailure { message.value = it.message ?: "Could not remove that repository" }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
