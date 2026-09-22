package com.hfspaces.explorer.ui.screens.browse

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceFilterState
import com.hfspaces.explorer.ui.components.*

@Composable
fun BrowseScreen(
    spaces: List<HfSpace>,
    isLoading: Boolean,
    filterState: SpaceFilterState,
    currentPage: Int,
    favoritesSet: Set<String>,
    onFilterChange: (SpaceFilterState) -> Unit,
    onPageChange: (Int) -> Unit,
    onSpaceClick: (HfSpace) -> Unit,
    onRunSpace: (HfSpace) -> Unit,
    onToggleFavorite: (HfSpace) -> Unit,
    onOpenFilterSheet: () -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to top when page or category changes
    LaunchedEffect(currentPage, filterState.category) {
        listState.scrollToItem(0)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Search & category chips
        AppSearchBar(
            filterState = filterState,
            onFilterChange = onFilterChange,
            onOpenFilterSheet = onOpenFilterSheet
        )

        // Main List or States
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading && spaces.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Loading Hugging Face Spaces...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (!isLoading && spaces.isEmpty()) {
                EmptyResultsState(
                    onResetFilters = { onFilterChange(SpaceFilterState()) }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = spaces,
                        key = { it.id }
                    ) { space ->
                        SpaceCard(
                            space = space,
                            isFavorite = favoritesSet.contains(space.id),
                            onCardClick = { onSpaceClick(space) },
                            onRunClick = { onRunSpace(space) },
                            onFavoriteToggle = { onToggleFavorite(space) }
                        )
                    }

                    item {
                        PaginationBar(
                            currentPage = currentPage,
                            isLoading = isLoading,
                            onPrevious = { if (currentPage > 0) onPageChange(currentPage - 1) },
                            onNext = { onPageChange(currentPage + 1) }
                        )
                    }
                }
            }
        }
    }
}
