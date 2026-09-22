package com.hfspaces.explorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.hfspaces.explorer.ui.components.AppTopBar
import com.hfspaces.explorer.ui.components.FilterBottomSheet
import com.hfspaces.explorer.ui.navigation.DRAWER_ITEMS
import com.hfspaces.explorer.ui.navigation.Screen
import com.hfspaces.explorer.ui.screens.apikeys.ApiKeysScreen
import com.hfspaces.explorer.ui.screens.browse.BrowseScreen
import com.hfspaces.explorer.ui.screens.details.SpaceDetailsScreen
import com.hfspaces.explorer.ui.screens.favourites.FavouritesScreen
import com.hfspaces.explorer.ui.screens.media.MediaGalleryScreen
import com.hfspaces.explorer.ui.screens.runner.SpaceRunnerScreen
import com.hfspaces.explorer.ui.screens.running.CurrentlyRunningScreen
import com.hfspaces.explorer.ui.theme.HfSpacesTheme
import com.hfspaces.explorer.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HfSpacesTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var showFilterSheet by remember { mutableStateOf(false) }

                val spaces by viewModel.spaces.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val currentPage by viewModel.currentPage.collectAsState()
                val filterState by viewModel.filterState.collectAsState()
                val selectedSpace by viewModel.selectedSpace.collectAsState()
                val spaceApiSpec by viewModel.spaceApiSpec.collectAsState()
                val isLoadingSpec by viewModel.isLoadingSpec.collectAsState()
                val favorites by viewModel.favoritesList.collectAsState()
                val favoritesSet by viewModel.favoritesIdsSet.collectAsState()
                val apiKeys by viewModel.apiKeysList.collectAsState()
                val mediaList by viewModel.mediaList.collectAsState()
                val activeTasks by viewModel.activeTasksList.collectAsState()
                val runningTasksCount by viewModel.runningTasksCount.collectAsState()
                val activeOnlineCount by viewModel.activeOnlineCount.collectAsState()

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            modifier = Modifier.width(300.dp),
                            drawerContainerColor = MaterialTheme.colorScheme.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(text = "🤗", fontSize = 28.sp)
                                    Column {
                                        Text(
                                            text = "Hugging Face",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Spaces Native Explorer",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            DRAWER_ITEMS.forEach { item ->
                                val isSelected = currentRoute == item.route
                                NavigationDrawerItem(
                                    icon = { Icon(imageVector = item.icon, contentDescription = item.title) },
                                    label = { Text(text = item.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        if (currentRoute != item.route) {
                                            navController.navigate(item.route) {
                                                popUpTo(Screen.Browse.route) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    badge = {
                                        if (item.route == Screen.CurrentlyRunning.route && runningTasksCount > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                Text(text = runningTasksCount.toString(), color = Color.White)
                                            }
                                        } else if (item.route == Screen.Favourites.route && favorites.isNotEmpty()) {
                                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                                Text(text = favorites.size.toString())
                                            }
                                        } else if (item.route == Screen.MediaGallery.route && mediaList.isNotEmpty()) {
                                            Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                                Text(text = mediaList.size.toString())
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            if (currentRoute == Screen.Browse.route) {
                                AppTopBar(
                                    title = "Hugging Face Spaces",
                                    activeCount = activeOnlineCount,
                                    runningTasksCount = runningTasksCount,
                                    isLoading = isLoading,
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onRefreshClick = { viewModel.loadSpaces() },
                                    onRunningTasksClick = { navController.navigate(Screen.CurrentlyRunning.route) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Browse.route
                            ) {
                                composable(Screen.Browse.route) {
                                    BrowseScreen(
                                        spaces = spaces,
                                        isLoading = isLoading,
                                        filterState = filterState,
                                        currentPage = currentPage,
                                        favoritesSet = favoritesSet,
                                        onFilterChange = { viewModel.setFilterState(it) },
                                        onPageChange = { viewModel.setPage(it) },
                                        onSpaceClick = { space ->
                                            viewModel.selectSpace(space)
                                            navController.navigate(Screen.SpaceDetails.createRoute(space.id))
                                        },
                                        onRunSpace = { space ->
                                            viewModel.selectSpace(space)
                                            navController.navigate(Screen.SpaceRunner.createRoute(space.id))
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onOpenFilterSheet = { showFilterSheet = true }
                                    )
                                }

                                composable(
                                    route = Screen.SpaceDetails.route,
                                    arguments = listOf(navArgument("spaceId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val spaceId = backStackEntry.arguments?.getString("spaceId")?.replace("__", "/") ?: ""
                                    val space = selectedSpace ?: spaces.find { it.id == spaceId } ?: favorites.find { it.id == spaceId }

                                    if (space != null) {
                                        SpaceDetailsScreen(
                                            space = space,
                                            spec = spaceApiSpec,
                                            isLoadingSpec = isLoadingSpec,
                                            isFavorite = favoritesSet.contains(space.id),
                                            onBack = { navController.popBackStack() },
                                            onRunSpace = {
                                                navController.navigate(Screen.SpaceRunner.createRoute(space.id))
                                            },
                                            onToggleFavorite = { viewModel.toggleFavorite(it) }
                                        )
                                    }
                                }

                                composable(
                                    route = Screen.SpaceRunner.route,
                                    arguments = listOf(navArgument("spaceId") { type = NavType.StringType })
                                ) { backStackEntry ->
                                    val spaceId = backStackEntry.arguments?.getString("spaceId")?.replace("__", "/") ?: ""
                                    val space = selectedSpace ?: spaces.find { it.id == spaceId } ?: favorites.find { it.id == spaceId }

                                    if (space != null) {
                                        val activeTask = activeTasks.find { it.space.id == space.id }
                                        SpaceRunnerScreen(
                                            space = space,
                                            spec = spaceApiSpec,
                                            isLoadingSpec = isLoadingSpec,
                                            apiKeys = apiKeys,
                                            activeTask = activeTask,
                                            onBack = { navController.popBackStack() },
                                            onStartInference = { endpoint, formValues, payload, token ->
                                                viewModel.startInference(space, endpoint, formValues, payload, token)
                                            },
                                            onCancelInference = { taskId ->
                                                viewModel.cancelTask(taskId)
                                            },
                                            onOpenApiKeys = {
                                                navController.navigate(Screen.ApiKeys.route)
                                            }
                                        )
                                    }
                                }

                                composable(Screen.CurrentlyRunning.route) {
                                    CurrentlyRunningScreen(
                                        tasks = activeTasks,
                                        onBack = { navController.popBackStack() },
                                        onOpenSpaceRunner = { space ->
                                            viewModel.selectSpace(space)
                                            navController.navigate(Screen.SpaceRunner.createRoute(space.id))
                                        },
                                        onCancelTask = { viewModel.cancelTask(it) },
                                        onRemoveTask = { viewModel.removeTask(it) },
                                        onClearCompleted = { viewModel.clearCompletedTasks() }
                                    )
                                }

                                composable(Screen.MediaGallery.route) {
                                    MediaGalleryScreen(
                                        mediaList = mediaList,
                                        onBack = { navController.popBackStack() },
                                        onDeleteMedia = { viewModel.deleteMedia(it) },
                                        onClearAll = { viewModel.clearAllMedia() }
                                    )
                                }

                                composable(Screen.Favourites.route) {
                                    FavouritesScreen(
                                        favorites = favorites,
                                        onBack = { navController.popBackStack() },
                                        onSpaceClick = { space ->
                                            viewModel.selectSpace(space)
                                            navController.navigate(Screen.SpaceDetails.createRoute(space.id))
                                        },
                                        onRunSpace = { space ->
                                            viewModel.selectSpace(space)
                                            navController.navigate(Screen.SpaceRunner.createRoute(space.id))
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) }
                                    )
                                }

                                composable(Screen.ApiKeys.route) {
                                    ApiKeysScreen(
                                        apiKeys = apiKeys,
                                        onBack = { navController.popBackStack() },
                                        onAddKey = { name, token -> viewModel.addApiKey(name, token) },
                                        onDeleteKey = { viewModel.deleteApiKey(it) },
                                        onSetDefaultKey = { viewModel.setDefaultApiKey(it) },
                                        onValidateKey = { token, callback -> viewModel.validateApiKey(token, callback) }
                                    )
                                }
                            }

                            // Filter Bottom Sheet Modal
                            if (showFilterSheet) {
                                FilterBottomSheet(
                                    filterState = filterState,
                                    onDismiss = { showFilterSheet = false },
                                    onApply = { newFilter ->
                                        viewModel.setFilterState(newFilter)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
