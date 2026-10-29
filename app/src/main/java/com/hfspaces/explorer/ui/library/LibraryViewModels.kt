package com.hfspaces.explorer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.data.model.UserApiKey
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.MediaRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.data.repository.TaskManager
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private fun <T> Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** Drawer badges and app-wide notifications. */
class ShellViewModel(
    taskManager: TaskManager,
    favorites: FavoritesRepository,
    media: MediaRepository
) : ViewModel() {
    val runningCount = taskManager.tasks.map { list -> list.count { it.isActive } }.stateIn(this, 0)
    val favoritesCount = favorites.observe().map { it.size }.stateIn(this, 0)
    val mediaCount = media.observe().map { it.size }.stateIn(this, 0)
    val finishedTasks: Flow<SpaceTask> = taskManager.finished
}

class FavouritesViewModel(
    private val favorites: FavoritesRepository,
    private val spaces: SpacesRepository
) : ViewModel() {
    val favourites = favorites.observe().stateIn(this, emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private var refreshed = false

    /** Pulls current status for saved Spaces once per screen visit (or on pull-to-refresh). */
    fun refresh(force: Boolean = false) {
        if ((refreshed && !force) || _isRefreshing.value) return
        refreshed = true
        viewModelScope.launch {
            _isRefreshing.value = true
            val limit = Semaphore(4)
            favorites.getAll().map { space ->
                async {
                    limit.withPermit {
                        runCatching { favorites.refreshIfFavorite(spaces.fetchSpace(space.id, null)) }
                    }
                }
            }.awaitAll()
            _isRefreshing.value = false
        }
    }

    fun toggle(space: HfSpace) {
        viewModelScope.launch { favorites.toggle(space) }
    }
}

class MediaViewModel(private val media: MediaRepository) : ViewModel() {
    val items: StateFlow<List<GeneratedMediaItem>> = media.observe().stateIn(this, emptyList())

    fun delete(id: String) {
        viewModelScope.launch { media.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { media.clear() }
    }
}

class TasksViewModel(private val taskManager: TaskManager) : ViewModel() {
    val tasks: StateFlow<List<SpaceTask>> = taskManager.tasks

    val hasFinished = taskManager.tasks.map { list -> list.any { it.status != TaskStatus.RUNNING } }.stateIn(this, false)

    fun cancel(id: String) = taskManager.cancel(id)
    fun remove(id: String) = taskManager.remove(id)
    fun clearFinished() = taskManager.clearFinished()
}

sealed interface TokenCheck {
    data object Idle : TokenCheck
    data object Checking : TokenCheck
    data class Valid(val account: String) : TokenCheck
    data class Invalid(val message: String) : TokenCheck
}

class ApiKeysViewModel(
    private val repository: ApiKeysRepository,
    private val hub: HfHubClient
) : ViewModel() {
    val keys: StateFlow<List<UserApiKey>> = repository.observe().stateIn(this, emptyList())

    private val _check = MutableStateFlow<TokenCheck>(TokenCheck.Idle)
    val check = _check.asStateFlow()

    fun validate(token: String) {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _check.value = TokenCheck.Checking
            _check.value = try {
                hub.whoAmI(trimmed)?.let { TokenCheck.Valid(it.fullname ?: it.name ?: "Hugging Face user") }
                    ?: TokenCheck.Invalid("Hugging Face rejected this token")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TokenCheck.Invalid(e.userMessage())
            }
        }
    }

    fun resetCheck() {
        _check.value = TokenCheck.Idle
    }

    fun add(name: String, token: String) {
        viewModelScope.launch { repository.add(name, token) }
        resetCheck()
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { repository.setDefault(id) }
    }
}
