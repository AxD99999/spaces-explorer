package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.SpaceCard

@Composable
fun FavouritesScreen(
    viewModel: FavouritesViewModel,
    onBack: () -> Unit,
    onOpenSpace: (HfSpace) -> Unit,
    onRunSpace: (HfSpace) -> Unit
) {
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("Favourites") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh(force = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (favourites.isEmpty()) {
                MessageState(
                    icon = Icons.Outlined.FavoriteBorder,
                    title = "No favourites yet",
                    message = "Tap the heart on any Space to keep it here.",
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(favourites, key = { it.id }) { space ->
                        SpaceCard(
                            space = space,
                            isFavorite = true,
                            onOpen = { onOpenSpace(space) },
                            onRun = { onRunSpace(space) },
                            onToggleFavorite = { viewModel.toggle(space) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
