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
import com.hfspaces.explorer.ui.library.CompareViewModel
import com.hfspaces.explorer.ui.library.FavouritesLibraryViewModel
import com.hfspaces.explorer.ui.library.FavouritesViewModel
import com.hfspaces.explorer.ui.library.ManageSpaceViewModel
import com.hfspaces.explorer.ui.library.MediaViewModel
import com.hfspaces.explorer.ui.library.MySpacesViewModel
import com.hfspaces.explorer.ui.library.SettingsViewModel
import com.hfspaces.explorer.ui.library.ShellViewModel
import com.hfspaces.explorer.ui.library.SpacePickerViewModel
import com.hfspaces.explorer.ui.library.TasksViewModel
import com.hfspaces.explorer.ui.space.ChatViewModel
import com.hfspaces.explorer.ui.space.SpaceViewModel

private fun CreationExtras.container(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as HfSpacesApplication).container

val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { container().run { ShellViewModel(taskManager, favoritesRepository, mediaRepository, settingsRepository, deepLinks) } }
    initializer { container().run { BrowseViewModel(spacesRepository, recentSearches) } }
    initializer {
        val c = container()
        SpaceViewModel(
            savedStateHandle = createSavedStateHandle(),
            spaces = c.spacesRepository,
            favorites = c.favoritesRepository,
            apiKeys = c.apiKeysRepository,
            taskManager = c.taskManager,
            drafts = c.formDrafts,
            staging = c.uploadStaging,
            presets = c.presetsRepository,
            deepLinks = c.deepLinks
        )
    }
    initializer {
        val c = container()
        ChatViewModel(createSavedStateHandle(), c.spacesRepository, c.favoritesRepository, c.apiKeysRepository, c.gradioClient, c.chatTranscripts)
    }
    initializer { container().run { FavouritesViewModel(favoritesRepository, spacesRepository) } }
    initializer {
        container().run { FavouritesLibraryViewModel(favoritesRepository, collectionsRepository, spacesRepository, hubClient, apiKeysRepository) }
    }
    initializer { container().run { MediaViewModel(mediaRepository, mediaExporter) } }
    initializer { TasksViewModel(container().taskManager) }
    initializer { container().run { ApiKeysViewModel(apiKeysRepository, hubClient) } }
    initializer { container().run { SpacePickerViewModel(spacesRepository, favoritesRepository, taskManager, deepLinks) } }
    initializer {
        val c = container()
        CompareViewModel(createSavedStateHandle(), c.spacesRepository, c.favoritesRepository, c.apiKeysRepository, c.taskManager)
    }
    initializer { container().run { MySpacesViewModel(hubClient, apiKeysRepository) } }
    initializer { container().run { ManageSpaceViewModel(createSavedStateHandle(), hubClient, apiKeysRepository) } }
    initializer { container().run { SettingsViewModel(settingsRepository, recentSearches, cacheDir) } }
}
