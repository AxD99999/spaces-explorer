package com.hfspaces.explorer.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.model.HF_CATEGORIES
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.ErrorState
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.SkeletonCard
import com.hfspaces.explorer.ui.common.SpaceCard
import com.hfspaces.explorer.ui.library.FavouritesViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hfspaces.explorer.ui.AppViewModelFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    runningCount: Int,
    onOpenDrawer: () -> Unit,
    onOpenSpace: (HfSpace) -> Unit,
    onRunSpace: (HfSpace) -> Unit,
    onOpenRuns: () -> Unit,
    favouritesViewModel: FavouritesViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favourites by favouritesViewModel.favourites.collectAsStateWithLifecycle()
    val favouriteIds = remember(favourites) { favourites.mapTo(HashSet()) { it.id } }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val gridState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Errors while results are on screen go to a snackbar; the full-screen state covers the rest.
    LaunchedEffect(state.error, state.spaces.isNotEmpty()) {
        val error = state.error
        if (error != null && state.spaces.isNotEmpty()) snackbar.showSnackbar(error)
    }

    // Infinite scroll: fetch the next page when the user nears the end.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }
    LaunchedEffect(state.filter.category, state.filter.search.trim(), state.filter.sortBy) {
        gridState.scrollToItem(0)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) { Icon(Icons.Outlined.Menu, contentDescription = "Open menu") }
                },
                title = {
                    Column {
                        Text("Spaces", fontWeight = FontWeight.Bold)
                        if (state.loadedCount > 0) {
                            Text(
                                "${state.activeOnlineCount} running · ${state.spaces.size} shown",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (runningCount > 0) {
                        FilledTonalButton(onClick = onOpenRuns, contentPadding = PaddingValues(horizontal = 12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("$runningCount running")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchRow(
                search = state.filter.search,
                filterCount = state.filter.sheetFilterCount,
                recent = state.recentSearches,
                onSearchChange = viewModel::onSearchChange,
                onPickRecent = viewModel::searchNow,
                onRemoveRecent = viewModel::removeRecentSearch,
                onOpenFilters = { showFilters = true }
            )
            CategoryChips(selected = state.filter.category, onSelect = viewModel::onCategorySelected)
            if (state.showingCached) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Showing saved results", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        if (!state.isLoading) TextButton(onClick = viewModel::retry) { Text("Retry") }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.spaces.isEmpty() && state.isLoading -> SkeletonList()
                    state.spaces.isEmpty() && state.error != null && state.loadedCount == 0 ->
                        ErrorState(state.error!!, onRetry = viewModel::retry, modifier = Modifier.align(Alignment.Center))
                    state.spaces.isEmpty() -> NoResults(
                        canLoadMore = state.canLoadMore,
                        isLoadingMore = state.isLoadingMore,
                        filtersActive = !state.filter.isDefault,
                        onLoadMore = viewModel::loadMore,
                        onReset = viewModel::resetFilters
                    )
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.spaces, key = { it.id }) { space ->
                            SpaceCard(
                                space = space,
                                isFavorite = space.id in favouriteIds,
                                onOpen = { onOpenSpace(space) },
                                onRun = { onRunSpace(space) },
                                onToggleFavorite = { favouritesViewModel.toggle(space) }
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                            ListFooter(state, onRetry = viewModel::loadMore)
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            current = state.filter,
            hardwareOptions = state.hardwareOptions,
            onDismiss = { showFilters = false },
            onApply = viewModel::applyFilters
        )
    }
}

@Composable
private fun SearchRow(
    search: String,
    filterCount: Int,
    recent: List<String>,
    onSearchChange: (String) -> Unit,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onOpenFilters: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    Column {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            placeholder = { Text("Search Spaces") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )
        BadgedBox(badge = { if (filterCount > 0) Badge { Text("$filterCount") } }) {
            IconButton(onClick = onOpenFilters) {
                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = if (filterCount > 0) "Filters, $filterCount active" else "Filters",
                    tint = if (filterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    // Recent searches, offered while the empty search box has focus.
    if (focused && search.isEmpty() && recent.isNotEmpty()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.History, contentDescription = "Recent searches", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.forEach { query ->
                InputChip(
                    selected = false,
                    onClick = {
                        onPickRecent(query)
                        focusManager.clearFocus()
                    },
                    label = { Text(query) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Forget $query",
                            modifier = Modifier.size(16.dp).clickable { onRemoveRecent(query) }
                        )
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HF_CATEGORIES.forEach { category ->
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(category.id) },
                label = { Text("${category.emoji}  ${category.name}") }
            )
        }
    }
}

@Composable
private fun SkeletonList() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) { SkeletonCard() }
    }
}

@Composable
private fun NoResults(
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    filtersActive: Boolean,
    onLoadMore: () -> Unit,
    onReset: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (canLoadMore) {
            MessageState(
                icon = Icons.Outlined.SearchOff,
                title = "No matches yet",
                message = "None of the Spaces loaded so far match these filters.",
                actionLabel = if (isLoadingMore) null else "Load more",
                onAction = onLoadMore
            )
        } else {
            MessageState(
                icon = Icons.Outlined.SearchOff,
                title = "No Spaces found",
                message = if (filtersActive) "Try a different search or clear your filters." else "Nothing to show right now.",
                actionLabel = if (filtersActive) "Clear filters" else null,
                onAction = onReset
            )
        }
    }
}

@Composable
private fun ListFooter(state: BrowseUiState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when {
            state.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            state.loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.loadMoreError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
            !state.canLoadMore && state.loadedCount > 0 -> Text(
                "You've reached the end",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
