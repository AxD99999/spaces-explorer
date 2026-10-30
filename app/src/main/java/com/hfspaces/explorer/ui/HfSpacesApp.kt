package com.hfspaces.explorer.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TouchApp
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.system.DeepLink
import com.hfspaces.explorer.ui.browse.BrowseScreen
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.library.ApiKeysScreen
import com.hfspaces.explorer.ui.library.CompareScreen
import com.hfspaces.explorer.ui.library.FavouritesScreen
import com.hfspaces.explorer.ui.library.ManageSpaceScreen
import com.hfspaces.explorer.ui.library.MediaGalleryScreen
import com.hfspaces.explorer.ui.library.MySpacesScreen
import com.hfspaces.explorer.ui.library.SettingsScreen
import com.hfspaces.explorer.ui.library.ShareTargetScreen
import com.hfspaces.explorer.ui.library.ShellViewModel
import com.hfspaces.explorer.ui.library.TasksScreen
import com.hfspaces.explorer.ui.library.ViewerScreen
import com.hfspaces.explorer.ui.space.ChatScreen
import com.hfspaces.explorer.ui.space.SpaceDetailsScreen
import com.hfspaces.explorer.ui.space.SpaceRunnerScreen
import com.hfspaces.explorer.ui.space.SpaceViewModel
import kotlinx.coroutines.launch

/** One snackbar host for the whole app, so messages survive screen changes. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

/** Width from which Browse shows a details pane next to the list. */
private const val TWO_PANE_MIN_WIDTH_DP = 840

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
    val pendingLink by shell.pendingLink.collectAsStateWithLifecycle()
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

    // Notifications, shortcuts, the widget and the share sheet all arrive here.
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        when (link) {
            is DeepLink.Runner -> navController.navigate(Routes.runner(link.spaceId, link.fromTaskId)) { launchSingleTop = true }
            is DeepLink.Screen -> navController.navigate(link.route) { launchSingleTop = true }
            DeepLink.ShareTarget -> navController.navigate(Routes.SHARE) { launchSingleTop = true }
        }
        shell.consumeLink(link)
    }

    // Ask for notification permission the first time something runs (Android 13+).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        shell.markNotificationPermissionAsked()
    }
    LaunchedEffect(runningCount > 0) {
        if (runningCount > 0 && Build.VERSION.SDK_INT >= 33 && shell.shouldAskNotificationPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
                    Column(Modifier.verticalScroll(rememberScrollState())) {
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
    val spaceArg = navArgument(SpaceViewModel.ARG_SPACE_ID) { type = NavType.StringType }
    val openKeys: () -> Unit = { navController.navigate(Routes.API_KEYS) { launchSingleTop = true } }

    NavHost(navController = navController, startDestination = Routes.BROWSE) {
        composable(Routes.BROWSE) {
            val wide = LocalConfiguration.current.screenWidthDp >= TWO_PANE_MIN_WIDTH_DP
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            val browse: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier) {
                    BrowseScreen(
                        viewModel = viewModel(factory = AppViewModelFactory),
                        runningCount = runningCount,
                        onOpenDrawer = onOpenDrawer,
                        onOpenSpace = { if (wide) selected = it.id else navController.navigate(Routes.details(it.id)) },
                        onRunSpace = { navController.navigate(Routes.runner(it.id)) },
                        onOpenRuns = { navController.navigate(Routes.TASKS) { launchSingleTop = true } }
                    )
                }
            }
            if (!wide) {
                browse(Modifier.fillMaxSize())
            } else {
                // Tablets and unfolded foldables: list and details side by side.
                Row(Modifier.fillMaxSize()) {
                    browse(Modifier.weight(1f))
                    VerticalDivider()
                    Box(Modifier.weight(1f)) {
                        val id = selected
                        if (id == null) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                MessageState(icon = Icons.Outlined.TouchApp, title = "Pick a Space", message = "Its details and API show up here.")
                            }
                        } else {
                            SpaceDetailsScreen(
                                viewModel = spaceViewModelFor(id),
                                onBack = { selected = null },
                                onRun = { navController.navigate(Routes.runner(it)) }
                            )
                        }
                    }
                }
            }
        }
        composable(Routes.DETAILS, arguments = listOf(spaceArg)) {
            SpaceDetailsScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onRun = { id -> navController.navigate(Routes.runner(id)) }
            )
        }
        composable(
            Routes.RUNNER,
            arguments = listOf(
                spaceArg,
                navArgument(SpaceViewModel.ARG_FROM_TASK) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument(SpaceViewModel.ARG_FROM_SHARE) { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            SpaceRunnerScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenDetails = { id ->
                    // Avoid stacking details -> runner -> details.
                    if (!navController.popBackStack(Routes.DETAILS, inclusive = false)) navController.navigate(Routes.details(id))
                },
                onManageKeys = openKeys,
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) }
            )
        }
        composable(Routes.CHAT, arguments = listOf(spaceArg)) {
            ChatScreen(viewModel = viewModel(factory = AppViewModelFactory), onBack = back)
        }
        composable(Routes.TASKS) {
            TasksScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenRunner = { navController.navigate(Routes.runner(it)) },
                onRerun = { spaceId, taskId -> navController.navigate(Routes.runner(spaceId, fromTask = taskId)) }
            )
        }
        composable(Routes.MEDIA) {
            MediaGalleryScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenSpace = { navController.navigate(Routes.runner(it)) },
                onOpenViewer = { navController.navigate(Routes.viewer(it)) }
            )
        }
        composable(Routes.VIEWER, arguments = listOf(navArgument("mediaId") { type = NavType.StringType })) { entry ->
            // Shares the gallery's ViewModel so both see the same list.
            val galleryEntry = remember(entry) { runCatching { navController.getBackStackEntry(Routes.MEDIA) }.getOrNull() }
            ViewerScreen(
                viewModel = if (galleryEntry != null) viewModel(galleryEntry, factory = AppViewModelFactory) else viewModel(factory = AppViewModelFactory),
                startId = entry.arguments?.getString("mediaId").orEmpty(),
                onBack = back
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
        composable(Routes.SHARE) {
            ShareTargetScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onPick = { id ->
                    navController.navigate(Routes.runner(id, fromShare = true)) {
                        popUpTo(Routes.SHARE) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.COMPARE) {
            CompareScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                picker = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onOpenRunner = { navController.navigate(Routes.runner(it)) }
            )
        }
        composable(Routes.MY_SPACES) {
            MySpacesScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onManage = { navController.navigate(Routes.manage(it)) },
                onRun = { navController.navigate(Routes.runner(it)) },
                onManageKeys = openKeys
            )
        }
        composable(Routes.MANAGE, arguments = listOf(spaceArg)) {
            ManageSpaceScreen(
                viewModel = viewModel(factory = AppViewModelFactory),
                onBack = back,
                onRun = { navController.navigate(Routes.runner(it)) }
            )
        }
        composable(Routes.API_KEYS) {
            ApiKeysScreen(viewModel = viewModel(factory = AppViewModelFactory), onBack = back)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel(factory = AppViewModelFactory), onBack = back)
        }
    }
}

/** A details ViewModel for a Space shown in a pane rather than its own destination. */
@Composable
private fun spaceViewModelFor(spaceId: String): SpaceViewModel {
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val defaults = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
    val extras = (if (defaults != null) MutableCreationExtras(defaults) else MutableCreationExtras()).apply {
        set(DEFAULT_ARGS_KEY, bundleOf(SpaceViewModel.ARG_SPACE_ID to spaceId))
    }
    return viewModel(key = "pane:$spaceId", factory = AppViewModelFactory, extras = extras)
}
