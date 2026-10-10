package com.ivor.openstream.presentation.marketplace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.R
import com.ivor.openstream.data.extensions.MarketplaceRanker
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.SourceSearchMode
import com.ivor.openstream.domain.model.ExtensionCatalog
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.MarketplaceSort
import com.ivor.openstream.domain.repository.ExtensionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    private val repository: ExtensionRepository,
    private val appSettings: AppSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** How the player searches the installed sources; set on the Installed tab. */
    val searchMode: StateFlow<SourceSearchMode> = appSettings.settings
        .map { it.sourceSearchMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), appSettings.current.sourceSearchMode)

    fun setSearchMode(mode: SourceSearchMode) = appSettings.update { it.copy(sourceSearchMode = mode) }

    /** Moves an installed source [offset] places up (negative) or down in the search order. */
    fun moveSource(extension: MarketplaceExtension, offset: Int) {
        val keys = state.value.installed.map { it.key }.toMutableList()
        val from = keys.indexOf(extension.key)
        val to = (from + offset).coerceIn(0, keys.lastIndex)
        if (from < 0 || from == to) return
        keys.add(to, keys.removeAt(from))
        repository.setSourceOrder(keys)
    }

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
                .onFailure { message.value = it.message ?: context.getString(R.string.st_could_not_refresh) }
        }
    }

    fun install(extension: MarketplaceExtension) {
        repository.install(extension.key)
        message.value = context.getString(R.string.st_extension_installed, extension.manifest.name)
    }

    fun uninstall(extension: MarketplaceExtension) {
        repository.uninstall(extension.key)
        message.value = context.getString(R.string.st_extension_removed, extension.manifest.name)
    }

    fun setEnabled(extension: MarketplaceExtension, enabled: Boolean) {
        repository.setEnabled(extension.key, enabled)
    }

    fun update(extension: MarketplaceExtension) {
        repository.update(extension.key)
        message.value = context.getString(R.string.st_extension_updated, extension.manifest.name, extension.manifest.versionName)
    }

    fun updateAll() {
        val count = repository.updateAll()
        message.value = if (count == 0) context.getString(R.string.st_everything_uptodate)
        else context.getString(R.string.st_updated_count, count)
    }

    fun addRepo(url: String) {
        viewModelScope.launch {
            repository.addRepo(url)
                .onSuccess { message.value = context.getString(R.string.st_repo_added, it.name) }
                .onFailure { message.value = it.message ?: context.getString(R.string.st_could_not_add_repo) }
        }
    }

    fun removeRepo(repoId: String) {
        viewModelScope.launch {
            repository.removeRepo(repoId)
                .onSuccess { message.value = context.getString(R.string.st_repo_removed) }
                .onFailure { message.value = it.message ?: context.getString(R.string.st_could_not_remove_repo) }
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
