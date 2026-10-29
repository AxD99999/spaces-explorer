package com.hfspaces.explorer.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector
import com.hfspaces.explorer.ui.space.SpaceViewModel

object Routes {
    const val BROWSE = "browse"
    const val DETAILS = "space/{${SpaceViewModel.ARG_SPACE_ID}}"
    const val RUNNER = "space/{${SpaceViewModel.ARG_SPACE_ID}}/run"
    const val TASKS = "tasks"
    const val MEDIA = "media"
    const val FAVOURITES = "favourites"
    const val API_KEYS = "api_keys"

    // Space ids contain "/", so they are percent-encoded into a single path segment.
    fun details(spaceId: String) = "space/${Uri.encode(spaceId)}"
    fun runner(spaceId: String) = "space/${Uri.encode(spaceId)}/run"
}

enum class DrawerDestination(val route: String, val title: String, val icon: ImageVector) {
    BROWSE(Routes.BROWSE, "Browse Spaces", Icons.Outlined.Explore),
    TASKS(Routes.TASKS, "Runs", Icons.Outlined.PlayCircle),
    MEDIA(Routes.MEDIA, "Saved outputs", Icons.Outlined.PhotoLibrary),
    FAVOURITES(Routes.FAVOURITES, "Favourites", Icons.Outlined.FavoriteBorder),
    API_KEYS(Routes.API_KEYS, "Access tokens", Icons.Outlined.Key)
}
