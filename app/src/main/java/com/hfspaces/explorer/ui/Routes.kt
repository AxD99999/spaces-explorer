package com.hfspaces.explorer.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.hfspaces.explorer.ui.space.SpaceViewModel

object Routes {
    private const val ID = SpaceViewModel.ARG_SPACE_ID

    const val BROWSE = "browse"
    const val DETAILS = "space/{$ID}"
    const val RUNNER = "space/{$ID}/run?${SpaceViewModel.ARG_FROM_TASK}={${SpaceViewModel.ARG_FROM_TASK}}&${SpaceViewModel.ARG_FROM_SHARE}={${SpaceViewModel.ARG_FROM_SHARE}}"
    const val CHAT = "space/{$ID}/chat"
    const val TASKS = "tasks"
    const val MEDIA = "media"
    const val VIEWER = "viewer/{mediaId}"
    const val FAVOURITES = "favourites"
    const val API_KEYS = "api_keys"
    const val SHARE = "share"
    const val COMPARE = "compare"
    const val MY_SPACES = "my_spaces"
    const val MANAGE = "manage/{$ID}"
    const val SETTINGS = "settings"

    // Space ids contain "/", so they are percent-encoded into a single path segment.
    fun details(spaceId: String) = "space/${Uri.encode(spaceId)}"
    fun runner(spaceId: String, fromTask: String? = null, fromShare: Boolean = false): String {
        val base = "space/${Uri.encode(spaceId)}/run"
        val params = listOfNotNull(
            fromTask?.let { "${SpaceViewModel.ARG_FROM_TASK}=${Uri.encode(it)}" },
            if (fromShare) "${SpaceViewModel.ARG_FROM_SHARE}=true" else null
        )
        return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
    }
    fun chat(spaceId: String) = "space/${Uri.encode(spaceId)}/chat"
    fun manage(spaceId: String) = "manage/${Uri.encode(spaceId)}"
    fun viewer(mediaId: String) = "viewer/${Uri.encode(mediaId)}"
}

enum class DrawerDestination(val route: String, val title: String, val icon: ImageVector) {
    BROWSE(Routes.BROWSE, "Browse Spaces", Icons.Outlined.Explore),
    TASKS(Routes.TASKS, "Runs", Icons.Outlined.PlayCircle),
    MEDIA(Routes.MEDIA, "Saved outputs", Icons.Outlined.PhotoLibrary),
    FAVOURITES(Routes.FAVOURITES, "Favourites", Icons.Outlined.FavoriteBorder),
    COMPARE(Routes.COMPARE, "Compare", Icons.AutoMirrored.Outlined.CompareArrows),
    MY_SPACES(Routes.MY_SPACES, "My Spaces", Icons.Outlined.Dns),
    API_KEYS(Routes.API_KEYS, "Access tokens", Icons.Outlined.Key),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Outlined.Settings)
}
