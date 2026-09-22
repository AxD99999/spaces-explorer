package com.hfspaces.explorer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Browse : Screen("browse")
    object SpaceDetails : Screen("space_details/{spaceId}") {
        fun createRoute(spaceId: String) = "space_details/${spaceId.replace("/", "__")}"
    }
    object SpaceRunner : Screen("space_runner/{spaceId}") {
        fun createRoute(spaceId: String) = "space_runner/${spaceId.replace("/", "__")}"
    }
    object CurrentlyRunning : Screen("currently_running")
    object MediaGallery : Screen("media_gallery")
    object Favourites : Screen("favourites")
    object ApiKeys : Screen("api_keys")
}

data class NavDrawerItem(
    val title: String,
    val route: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

val DRAWER_ITEMS = listOf(
    NavDrawerItem("Browse Spaces", Screen.Browse.route, Icons.Outlined.Explore),
    NavDrawerItem("Running Tasks", Screen.CurrentlyRunning.route, Icons.Outlined.PlayCircle),
    NavDrawerItem("Generated Media", Screen.MediaGallery.route, Icons.Outlined.PhotoLibrary),
    NavDrawerItem("Favorite Spaces", Screen.Favourites.route, Icons.Outlined.FavoriteBorder),
    NavDrawerItem("Hugging Face API Keys", Screen.ApiKeys.route, Icons.Outlined.Key)
)
