package com.hfspaces.explorer.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hfspaces.explorer.AppContainer
import com.hfspaces.explorer.HfSpacesApplication
import com.hfspaces.explorer.ui.browse.BrowseViewModel
import com.hfspaces.explorer.ui.library.ApiKeysViewModel
import com.hfspaces.explorer.ui.library.FavouritesViewModel
import com.hfspaces.explorer.ui.library.MediaViewModel
import com.hfspaces.explorer.ui.library.ShellViewModel
import com.hfspaces.explorer.ui.library.TasksViewModel
import com.hfspaces.explorer.ui.space.SpaceViewModel

private fun CreationExtras.container(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as HfSpacesApplication).container

val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { container().run { ShellViewModel(taskManager, favoritesRepository, mediaRepository) } }
    initializer { BrowseViewModel(container().spacesRepository) }
    initializer {
        val c = container()
        SpaceViewModel(
            savedStateHandle = createSavedStateHandle(),
            spaces = c.spacesRepository,
            favorites = c.favoritesRepository,
            apiKeys = c.apiKeysRepository,
            taskManager = c.taskManager,
            drafts = c.formDrafts,
            staging = c.uploadStaging
        )
    }
    initializer { container().run { FavouritesViewModel(favoritesRepository, spacesRepository) } }
    initializer { MediaViewModel(container().mediaRepository) }
    initializer { TasksViewModel(container().taskManager) }
    initializer { container().run { ApiKeysViewModel(apiKeysRepository, hubClient) } }
}
