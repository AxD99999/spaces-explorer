package com.hfspaces.explorer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.local.AppDatabase
import com.hfspaces.explorer.data.model.*
import com.hfspaces.explorer.data.remote.HfApiClient
import com.hfspaces.explorer.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val api = HfApiClient()

    val spacesRepository = SpacesRepository(api)
    val favoritesRepository = FavoritesRepository(database)
    val apiKeysRepository = ApiKeysRepository(database)
    val mediaRepository = MediaRepository(database)
    val taskManager = TaskManager(api, mediaRepository)

    // State: Spaces List & UI
    private val _spaces = MutableStateFlow<List<HfSpace>>(emptyList())
    val spaces: StateFlow<List<HfSpace>> = _spaces.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _filterState = MutableStateFlow(SpaceFilterState())
    val filterState: StateFlow<SpaceFilterState> = _filterState.asStateFlow()

    // State: Selected Space details and API specs
    private val _selectedSpace = MutableStateFlow<HfSpace?>(null)
    val selectedSpace: StateFlow<HfSpace?> = _selectedSpace.asStateFlow()

    private val _spaceApiSpec = MutableStateFlow<SpaceApiSpec?>(null)
    val spaceApiSpec: StateFlow<SpaceApiSpec?> = _spaceApiSpec.asStateFlow()

    private val _isLoadingSpec = MutableStateFlow(false)
    val isLoadingSpec: StateFlow<Boolean> = _isLoadingSpec.asStateFlow()

    // State: Reactive database collections
    val favoritesList: StateFlow<List<HfSpace>> = favoritesRepository.getAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritesIdsSet: StateFlow<Set<String>> = favoritesList
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val apiKeysList: StateFlow<List<UserApiKey>> = apiKeysRepository.getAllApiKeys()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mediaList: StateFlow<List<GeneratedMediaItem>> = mediaRepository.getAllMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasksList: StateFlow<List<ActiveSpaceTask>> = taskManager.tasksFlow

    val runningTasksCount: StateFlow<Int> = activeTasksList
        .map { list -> list.count { it.status == TaskStatus.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeOnlineCount: StateFlow<Int> = _spaces
        .map { list -> list.count { it.isRunning } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadSpaces()
    }

    fun loadSpaces() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentFilter = _filterState.value
                val page = _currentPage.value

                val rawList = if (currentFilter.category != "all" && currentFilter.category.isNotBlank()) {
                    spacesRepository.getSemanticSpaces(
                        category = currentFilter.category,
                        query = currentFilter.search.ifBlank { null }
                    )
                } else {
                    spacesRepository.getSpaces(
                        page = page,
                        search = currentFilter.search.ifBlank { null },
                        filter = null
                    )
                }

                // Apply client-side filters and sorting
                var result = rawList

                // Filter by status
                if (currentFilter.status == "running") {
                    result = result.filter { it.isRunning }
                } else if (currentFilter.status == "sleeping") {
                    result = result.filter { it.isSleeping }
                }

                // Filter by SDK
                if (currentFilter.sdk != "all") {
                    result = result.filter { it.sdk.equals(currentFilter.sdk, ignoreCase = true) }
                }

                // Filter by pinned/featured
                if (currentFilter.pinnedOnly) {
                    result = result.filter { it.pinned == true }
                }
                if (currentFilter.featuredOnly) {
                    result = result.filter { it.featured == true }
                }

                // Sort
                result = when (currentFilter.sortBy) {
                    SortOption.TRENDING_DESC -> result.sortedByDescending { it.trendingScore ?: 0.0 }
                    SortOption.LIKES_DESC -> result.sortedByDescending { it.likes ?: 0 }
                    SortOption.NAME_ASC -> result.sortedBy { it.displayTitle.lowercase() }
                    SortOption.NAME_DESC -> result.sortedByDescending { it.displayTitle.lowercase() }
                    SortOption.RECENT_DESC -> result.sortedByDescending { it.lastModified ?: "" }
                    SortOption.CREATED_DESC -> result.sortedByDescending { it.createdAt ?: "" }
                }

                _spaces.value = result
            } catch (e: Exception) {
                // Keep existing spaces if fetch fails
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setFilterState(filter: SpaceFilterState) {
        _filterState.value = filter
        _currentPage.value = 0
        loadSpaces()
    }

    fun setPage(page: Int) {
        _currentPage.value = page
        loadSpaces()
    }

    fun selectSpace(space: HfSpace) {
        _selectedSpace.value = space
        loadSpaceSpec(space)
    }

    fun selectSpaceById(spaceId: String) {
        val found = _spaces.value.find { it.id == spaceId }
            ?: favoritesList.value.find { it.id == spaceId }
            ?: HfSpace(id = spaceId)
        _selectedSpace.value = found
        loadSpaceSpec(found)
    }

    private fun loadSpaceSpec(space: HfSpace) {
        viewModelScope.launch {
            _isLoadingSpec.value = true
            _spaceApiSpec.value = null
            try {
                val defaultKey = apiKeysRepository.getDefaultKey()
                val spec = spacesRepository.getSpaceInfo(space.id, defaultKey?.token)
                _spaceApiSpec.value = spec
            } catch (e: Exception) {
                // Fallback default spec
                _spaceApiSpec.value = SpaceApiSpec(
                    spaceId = space.id,
                    host = "https://${space.subdomain ?: space.id.replace('/', '-').lowercase()}.hf.space",
                    endpoints = listOf(
                        SpaceEndpointSpec(
                            name = "/predict",
                            parameters = listOf(
                                SpaceEndpointParam(label = "Prompt", parameter_name = "prompt", component = "textbox", example_input = "")
                            )
                        )
                    )
                )
            } finally {
                _isLoadingSpec.value = false
            }
        }
    }

    fun toggleFavorite(space: HfSpace) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(space)
        }
    }

    fun startInference(
        space: HfSpace,
        endpoint: String,
        formValues: Map<String, Any>,
        payload: List<Any?>,
        token: String?
    ): String {
        return taskManager.startTask(
            space = space,
            endpoint = endpoint,
            formValues = formValues,
            payloadData = payload,
            token = token,
            subdomain = _spaceApiSpec.value?.subdomain ?: space.subdomain
        )
    }

    fun cancelTask(taskId: String) {
        taskManager.cancelTask(taskId)
    }

    fun removeTask(taskId: String) {
        taskManager.removeTask(taskId)
    }

    fun clearCompletedTasks() {
        taskManager.clearCompleted()
    }

    fun addApiKey(name: String, token: String) {
        viewModelScope.launch {
            val isFirst = apiKeysList.value.isEmpty()
            apiKeysRepository.addApiKey(name, token, isFirst)
        }
    }

    fun deleteApiKey(id: String) {
        viewModelScope.launch {
            apiKeysRepository.deleteApiKey(id)
        }
    }

    fun setDefaultApiKey(id: String) {
        viewModelScope.launch {
            apiKeysRepository.setDefaultApiKey(id)
        }
    }

    fun validateApiKey(token: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (valid, name) = api.validateHfToken(token)
            onResult(valid, name)
        }
    }

    fun deleteMedia(id: String) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(id)
        }
    }

    fun clearAllMedia() {
        viewModelScope.launch {
            mediaRepository.clearAll()
        }
    }
}
