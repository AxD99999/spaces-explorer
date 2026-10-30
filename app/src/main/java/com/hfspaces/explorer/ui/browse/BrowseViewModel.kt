package com.hfspaces.explorer.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.model.ALL
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceFilterState
import com.hfspaces.explorer.data.model.appendDistinct
import com.hfspaces.explorer.data.model.applyLocalFilters
import com.hfspaces.explorer.data.model.hidingAdult
import com.hfspaces.explorer.data.remote.HubSort
import com.hfspaces.explorer.data.repository.RecentSearches
import com.hfspaces.explorer.data.repository.SettingsRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val hardwareOptions: List<String> = emptyList(),
    val recentSearches: List<String> = emptyList()
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

class BrowseViewModel(
    private val repository: SpacesRepository,
    private val recentSearches: RecentSearches,
    private val settings: SettingsRepository
) : ViewModel() {
    private val filter = MutableStateFlow(SpaceFilterState())
    private val loaded = MutableStateFlow<List<HfSpace>>(emptyList())
    private val load = MutableStateFlow(LoadState())

    private var loadJob: Job? = null
    private var searchDebounce: Job? = null
    private var nextPage = 0

    private val showAdult = settings.settings.map { it.showAdultSpaces }.distinctUntilChanged()

    val uiState: StateFlow<BrowseUiState> = combine(filter, loaded, load, recentSearches.items, showAdult) { filter, loaded, load, recents, adult ->
        BrowseUiState(
            filter = filter,
            spaces = loaded.hidingAdult(adult).applyLocalFilters(filter),
            loadedCount = loaded.size,
            isLoading = load.isLoading,
            isRefreshing = load.isRefreshing,
            isLoadingMore = load.isLoadingMore,
            canLoadMore = load.canLoadMore,
            error = load.error,
            loadMoreError = load.loadMoreError,
            showingCached = load.showingCached,
            hardwareOptions = loaded.mapNotNull { it.hardwareLabel }.distinct().sorted(),
            recentSearches = recents
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
        val query = filter.value
        loadJob = viewModelScope.launch {
            load.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            try {
                val page = fetchFrom(nextPage, query)
                loaded.update { it.appendDistinct(page.spaces) }
                nextPage = page.nextPage
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
                    if (query.search.isNotBlank()) recentSearches.add(query.search)
                    val results = repository.semanticSearch(query.category, query.search)
                    loaded.value = results
                    load.value = LoadState(isLoading = false, canLoadMore = false)
                } else {
                    if (query.search.isNotBlank()) recentSearches.add(query.search)
                    val page = fetchFrom(0, query)
                    loaded.value = page.spaces
                    nextPage = page.nextPage
                    load.value = LoadState(isLoading = false, canLoadMore = page.hasMore)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                load.update { it.copy(isLoading = false, isRefreshing = false, error = e.userMessage()) }
            }
        }
    }

    /**
     * Fetches the page at [start]. When adult Spaces are hidden and left fewer than a screenful
     * visible, fetches a few more pages, so infinite scroll doesn't stall on a page of hidden results.
     */
    private suspend fun fetchFrom(start: Int, query: SpaceFilterState): FetchedPages {
        val hideAdult = !settings.settings.value.showAdultSpaces
        val spaces = mutableListOf<HfSpace>()
        var page = start
        while (true) {
            val result = repository.fetchPage(page, query.search, query.hubSort(), query.hubSdk())
            spaces += result.spaces
            page++
            val visible = spaces.count { !hideAdult || !it.isAdult }
            val keepGoing = hideAdult && visible < MIN_VISIBLE_PER_LOAD && result.hasMore && result.spaces.isNotEmpty() &&
                page - start <= MAX_EXTRA_PAGES
            if (!keepGoing) return FetchedPages(spaces, page, result.hasMore)
        }
    }

    private class FetchedPages(val spaces: List<HfSpace>, val nextPage: Int, val hasMore: Boolean)

    fun removeRecentSearch(query: String) = recentSearches.remove(query)

    /** Runs a search immediately (e.g. from a recent-search chip), skipping the debounce. */
    fun searchNow(text: String) {
        searchDebounce?.cancel()
        filter.update { it.copy(search = text) }
        reload()
    }

    private fun SpaceFilterState.hubSort(): HubSort =
        HubSort.entries.firstOrNull { it.param != null && it.param == sortBy.serverKey } ?: HubSort.TRENDING

    private fun SpaceFilterState.hubSdk(): String? = sdk.takeIf { it != ALL }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        const val MIN_VISIBLE_PER_LOAD = 6
        const val MAX_EXTRA_PAGES = 3
    }
}
