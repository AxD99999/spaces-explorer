package com.hfspaces.explorer.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.model.ALL
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceFilterState
import com.hfspaces.explorer.data.model.appendDistinct
import com.hfspaces.explorer.data.model.applyLocalFilters
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUiState(
    val filter: SpaceFilterState = SpaceFilterState(),
    /** Loaded Spaces after local filters and sorting. */
    val spaces: List<HfSpace> = emptyList(),
    val loadedCount: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    /** Error loading the first page; shown full-screen when nothing is loaded. */
    val error: String? = null,
    val loadMoreError: String? = null,
    /** True while showing the offline copy of the last trending page. */
    val showingCached: Boolean = false,
    val hardwareOptions: List<String> = emptyList()
) {
    val activeOnlineCount: Int get() = spaces.count { it.stage == com.hfspaces.explorer.data.model.SpaceStage.RUNNING }
}

private data class LoadState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val showingCached: Boolean = false
)

class BrowseViewModel(private val repository: SpacesRepository) : ViewModel() {
    private val filter = MutableStateFlow(SpaceFilterState())
    private val loaded = MutableStateFlow<List<HfSpace>>(emptyList())
    private val load = MutableStateFlow(LoadState())

    private var loadJob: Job? = null
    private var searchDebounce: Job? = null
    private var nextPage = 0

    val uiState: StateFlow<BrowseUiState> = combine(filter, loaded, load) { filter, loaded, load ->
        BrowseUiState(
            filter = filter,
            spaces = loaded.applyLocalFilters(filter),
            loadedCount = loaded.size,
            isLoading = load.isLoading,
            isRefreshing = load.isRefreshing,
            isLoadingMore = load.isLoadingMore,
            canLoadMore = load.canLoadMore,
            error = load.error,
            loadMoreError = load.loadMoreError,
            showingCached = load.showingCached,
            hardwareOptions = loaded.mapNotNull { it.hardwareLabel }.distinct().sorted()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseUiState())

    init {
        viewModelScope.launch {
            repository.cachedFirstPage()?.let { cached ->
                if (loaded.value.isEmpty() && load.value.isLoading) {
                    loaded.value = cached
                    load.update { it.copy(showingCached = true) }
                }
            }
        }
        reload(clearList = false)
    }

    fun onSearchChange(text: String) {
        val previous = filter.value
        filter.value = previous.copy(search = text)
        if (previous.search.trim() == text.trim()) return
        searchDebounce?.cancel()
        searchDebounce = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload()
        }
    }

    fun onCategorySelected(categoryId: String) {
        if (filter.value.category == categoryId) return
        filter.update { it.copy(category = categoryId) }
        searchDebounce?.cancel()
        reload()
    }

    fun applyFilters(newFilter: SpaceFilterState) {
        val previous = filter.value
        filter.value = newFilter
        if (previous.remoteQueryDiffers(newFilter)) {
            searchDebounce?.cancel()
            reload()
        }
    }

    fun resetFilters() = applyFilters(SpaceFilterState())

    fun refresh() = reload(clearList = false, refreshing = true)

    fun retry() = reload(clearList = false)

    fun loadMore() {
        val state = load.value
        if (state.isLoading || state.isLoadingMore || !state.canLoadMore || loadJob?.isActive == true) return
        val search = filter.value.search
        loadJob = viewModelScope.launch {
            load.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            try {
                val page = repository.fetchPage(nextPage, search)
                loaded.update { it.appendDistinct(page.spaces) }
                nextPage++
                load.update { it.copy(isLoadingMore = false, canLoadMore = page.hasMore && page.spaces.isNotEmpty()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                load.update { it.copy(isLoadingMore = false, loadMoreError = e.userMessage()) }
            }
        }
    }

    private fun reload(clearList: Boolean = true, refreshing: Boolean = false) {
        loadJob?.cancel()
        val query = filter.value
        if (clearList) loaded.value = emptyList()
        loadJob = viewModelScope.launch {
            load.update {
                it.copy(isLoading = !refreshing, isRefreshing = refreshing, isLoadingMore = false, error = null, loadMoreError = null)
            }
            try {
                if (query.category != ALL) {
                    val results = repository.semanticSearch(query.category, query.search)
                    loaded.value = results
                    load.value = LoadState(isLoading = false, canLoadMore = false)
                } else {
                    val page = repository.fetchPage(0, query.search)
                    loaded.value = page.spaces
                    nextPage = 1
                    load.value = LoadState(isLoading = false, canLoadMore = page.hasMore)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                load.update { it.copy(isLoading = false, isRefreshing = false, error = e.userMessage()) }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
