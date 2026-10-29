package com.hfspaces.explorer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.ui.browse.BrowseScreen
import com.hfspaces.explorer.ui.library.ApiKeysScreen
import com.hfspaces.explorer.ui.library.FavouritesScreen
import com.hfspaces.explorer.ui.library.MediaGalleryScreen
import com.hfspaces.explorer.ui.library.ShellViewModel
import com.hfspaces.explorer.ui.library.TasksScreen
import com.hfspaces.explorer.ui.space.SpaceDetailsScreen
import com.hfspaces.explorer.ui.space.SpaceRunnerScreen
import com.hfspaces.explorer.ui.space.SpaceViewModel
import kotlinx.coroutines.launch

/** One snackbar host for the whole app, so messages survive screen changes. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun HfSpacesApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val shell: ShellViewModel = viewModel(factory = AppViewModelFactory)

    val runningCount by shell.runningCount.collectAsStateWithLifecycle()
    val favoritesCount by shell.favoritesCount.collectAsStateWithLifecycle()
    val mediaCount by shell.mediaCount.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // Runs finish in the background; tell the user wherever they are.
    LaunchedEffect(Unit) {
        shell.finishedTasks.collect { task ->
            val viewingIt = navController.currentBackStackEntry?.let { entry ->
                entry.destination.route == Routes.RUNNER &&
                    entry.arguments?.getString(SpaceViewModel.ARG_SPACE_ID) == task.space.id
            } == true
            if (viewingIt || task.status == TaskStatus.CANCELLED) return@collect
            val message = when (task.status) {
                TaskStatus.COMPLETED -> "${task.space.displayTitle} finished"
                else -> "${task.space.displayTitle} failed"
            }
            val result = snackbarHostState.showSnackbar(message, actionLabel = "View", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) navController.navigate(Routes.runner(task.space.id))
        }
    }

    fun navigateTopLevel(route: String) {
        scope.launch { drawerState.close() }
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(Routes.BROWSE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = currentRoute == Routes.BROWSE || drawerState.isOpen,
            drawerContent = {
                ModalDrawerSheet {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤗", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Spaces Explorer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Hugging Face", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    DrawerDestination.entries.forEach { destination ->
                        val badge = when (destination) {
                            DrawerDestination.TASKS -> runningCount
                            DrawerDestination.MEDIA -> mediaCount
                            DrawerDestination.FAVOURITES -> favoritesCount
                            else -> 0
                        }
                        NavigationDrawerItem(
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) },
                            selected = currentRoute == destination.route,
                            onClick = { navigateTopLevel(destination.route) },
                            badge = {
                                if (badge > 0) {
                                    Badge(
                                        containerColor = if (destination == DrawerDestination.TASKS) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) { Text(badge.toString()) }
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        ) {
            AppNavHost(
                navController = navController,
                runningCount = runningCount,
                onOpenDrawer = { scope.launch { drawerState.open() } }
            )
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, runningCount: Int, onOpenDrawer: () -> Unit) {
    val back: () -> Unit = { navController.popBackStack() }
    val spaceArg = listOf(navArgument(SpaceViewModel.ARG_SPACE_ID) { type = NavType.StringType })

    NavHost(navController = navController, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) {
            BrowseScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                runningCount = runningCount,
                onOpenDrawer = onOpenDrawer,
                onOpenSpace = { navController.navigate(Routes.details(it.id)) },
                onRunSpace = { navController.navigate(Routes.runner(it.id)) },
                onOpenRuns = { navController.navigate(Routes.TASKS) { launchSingleTop = true } }
            )
        }
        composable(Routes.DETAILS, arguments = spaceArg) {
            SpaceDetailsScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onRun = { id -> navController.navigate(Routes.runner(id)) }
            )
        }
        composable(Routes.RUNNER, arguments = spaceArg) {
            SpaceRunnerScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenDetails = { id ->
                    // Avoid stacking details -> runner -> details.
                    if (!navController.popBackStack(Routes.DETAILS, inclusive = false)) navController.navigate(Routes.details(id))
                },
                onManageKeys = { navController.navigate(Routes.API_KEYS) { launchSingleTop = true } }
            )
        }
        composable(Routes.TASKS) {
            TasksScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenRunner = { navController.navigate(Routes.runner(it)) }
            )
        }
        composable(Routes.MEDIA) {
            MediaGalleryScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenSpace = { navController.navigate(Routes.runner(it)) }
            )
        }
        composable(Routes.FAVOURITES) {
            FavouritesScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenSpace = { navController.navigate(Routes.details(it.id)) },
                onRunSpace = { navController.navigate(Routes.runner(it.id)) }
            )
        }
        composable(Routes.API_KEYS) {
            ApiKeysScreen(viewModel = viewModel(factory = AppViewModelFactory), onBack = back)
        }
    }
}
