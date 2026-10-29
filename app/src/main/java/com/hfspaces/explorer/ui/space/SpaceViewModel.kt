package com.hfspaces.explorer.ui.space

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.InvalidInputException
import com.hfspaces.explorer.data.gradio.PickedFile
import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.gradio.SpaceEndpoint
import com.hfspaces.explorer.data.gradio.UploadedFile
import com.hfspaces.explorer.data.gradio.buildPayload
import com.hfspaces.explorer.data.gradio.initialFormValue
import com.hfspaces.explorer.data.gradio.inputKind
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.UserApiKey
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.FormDraftStore
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.data.repository.TaskManager
import com.hfspaces.explorer.data.repository.UploadStaging
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SpecState {
    data object Loading : SpecState
    data class Ready(val spec: SpaceApiSpec) : SpecState
    data class Failed(val message: String) : SpecState
}

data class SpaceUiState(
    val spaceId: String,
    val space: HfSpace? = null,
    val spaceError: String? = null,
    val spec: SpecState = SpecState.Loading,
    val isFavorite: Boolean = false,
    val apiKeys: List<UserApiKey> = emptyList(),
    /** null means "call without a token". */
    val selectedKeyId: String? = null,
    val selectedEndpoint: String? = null,
    val formValues: Map<Int, FormValue> = emptyMap(),
    /** Files being copied into app storage, by parameter index. */
    val stagingInputs: Set<Int> = emptySet(),
    val inputError: String? = null,
    val task: SpaceTask? = null
) {
    val readySpec: SpaceApiSpec? get() = (spec as? SpecState.Ready)?.spec
    val endpoint: SpaceEndpoint? get() = readySpec?.endpoints?.let { eps -> eps.firstOrNull { it.name == selectedEndpoint } ?: eps.firstOrNull() }
    val selectedKey: UserApiKey? get() = apiKeys.firstOrNull { it.id == selectedKeyId }
    val isRunning: Boolean get() = task?.isActive == true
}

/** Shared by the details and runner screens of one Space. */
class SpaceViewModel(
    savedStateHandle: SavedStateHandle,
    private val spaces: SpacesRepository,
    private val favorites: FavoritesRepository,
    apiKeys: ApiKeysRepository,
    private val taskManager: TaskManager,
    private val drafts: FormDraftStore,
    private val staging: UploadStaging
) : ViewModel() {
    val spaceId: String = checkNotNull(savedStateHandle.get<String>(ARG_SPACE_ID)) { "Missing space id" }

    private val local = MutableStateFlow(SpaceUiState(spaceId = spaceId))
    private val messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for a snackbar. */
    val events: Flow<String> = messages.receiveAsFlow()

    private val keys = apiKeys.observe().map { list -> list.filter { it.token.isNotBlank() } }
    private var specJob: Job? = null
    private var keyChosen = false

    val uiState: StateFlow<SpaceUiState> = combine(
        local,
        keys,
        favorites.observe().map { list -> list.any { it.id == spaceId } },
        taskManager.tasks.map { list -> list.firstOrNull { it.space.id == spaceId } }
    ) { state, keys, isFavorite, task ->
        val keyId = when {
            keyChosen -> state.selectedKeyId?.takeIf { id -> keys.any { it.id == id } }
            else -> keys.firstOrNull { it.isDefault }?.id ?: keys.firstOrNull()?.id
        }
        state.copy(apiKeys = keys, selectedKeyId = keyId, isFavorite = isFavorite, task = task)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        if (!SpaceIds.isValidSpaceId(spaceId)) {
            local.update { it.copy(spaceError = "\"$spaceId\" is not a valid Space id", spec = SpecState.Failed("Invalid Space id")) }
        } else {
            viewModelScope.launch { loadSpace() }
        }
    }

    private suspend fun loadSpace() {
        val cached = spaces.knownSpace(spaceId) ?: favorites.get(spaceId)
        if (cached != null) {
            local.update { it.copy(space = cached) }
            loadSpec()
        }
        // Fresh metadata: current status, host, and an up-to-date favourite entry.
        try {
            val fresh = spaces.fetchSpace(spaceId, currentToken())
            favorites.refreshIfFavorite(fresh)
            local.update { it.copy(space = fresh, spaceError = null) }
            if (cached == null) loadSpec()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (cached == null) {
                local.update { it.copy(spaceError = e.userMessage(), spec = SpecState.Failed(e.userMessage())) }
            }
        }
    }

    fun retry() {
        if (local.value.space == null) viewModelScope.launch { loadSpace() } else loadSpec(force = true)
    }

    private fun loadSpec(force: Boolean = false) {
        val space = local.value.space ?: return
        specJob?.cancel()
        specJob = viewModelScope.launch {
            local.update { it.copy(spec = SpecState.Loading) }
            try {
                val token = currentToken()
                val spec = if (force) spaces.discoverApi(space, token, maxAgeMillis = 0) else spaces.discoverApi(space, token)
                local.update { state ->
                    val endpoint = state.selectedEndpoint?.takeIf { name -> spec.endpoints.any { it.name == name } }
                        ?: spec.endpoints.firstOrNull()?.name
                    state.copy(spec = SpecState.Ready(spec), selectedEndpoint = endpoint, formValues = formFor(spec, endpoint))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(spec = SpecState.Failed(e.userMessage())) }
            }
        }
    }

    fun selectEndpoint(name: String) {
        local.update { state ->
            val spec = state.readySpec ?: return@update state
            state.copy(selectedEndpoint = name, formValues = formFor(spec, name), inputError = null)
        }
    }

    fun selectKey(keyId: String?) {
        keyChosen = true
        local.update { it.copy(selectedKeyId = keyId) }
    }

    fun updateValue(index: Int, value: FormValue) {
        local.update { state ->
            val values = state.formValues + (index to value)
            state.selectedEndpoint?.let { drafts.put(spaceId, it, values) }
            state.copy(formValues = values, inputError = null)
        }
    }

    fun resetForm() {
        local.update { state ->
            val spec = state.readySpec ?: return@update state
            state.selectedEndpoint?.let { drafts.clear(spaceId, it) }
            state.copy(formValues = formFor(spec, state.selectedEndpoint), inputError = null)
        }
    }

    fun addFiles(index: Int, uris: List<Uri>, multiple: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            local.update { it.copy(stagingInputs = it.stagingInputs + index) }
            try {
                val staged = uris.map { staging.stage(it) }
                val current = (local.value.formValues[index] as? FormValue.Files)?.files.orEmpty()
                updateValue(index, FormValue.Files(if (multiple) current + staged else staged.take(1)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.trySend("Couldn't read that file: ${e.userMessage()}")
            } finally {
                local.update { it.copy(stagingInputs = it.stagingInputs - index) }
            }
        }
    }

    fun removeFile(index: Int, file: PickedFile) {
        val current = (local.value.formValues[index] as? FormValue.Files)?.files.orEmpty()
        updateValue(index, FormValue.Files(current - file))
    }

    fun run() {
        val state = uiState.value
        val space = state.space ?: return
        val spec = state.readySpec ?: return
        val endpoint = state.endpoint ?: return
        if (state.isRunning || state.stagingInputs.isNotEmpty()) return
        try {
            // Dry run: catches bad numbers etc. before anything is uploaded.
            val placeholders = state.formValues.values.filterIsInstance<FormValue.Files>()
                .flatMap { it.files }
                .associate { it.uri to UploadedFile("", it) }
            buildPayload(endpoint, state.formValues, placeholders)
        } catch (e: InvalidInputException) {
            local.update { it.copy(inputError = e.message) }
            return
        }
        taskManager.start(space, spec, endpoint, state.formValues, state.selectedKey?.token)
    }

    fun cancel() {
        uiState.value.task?.takeIf { it.isActive }?.let { taskManager.cancel(it.id) }
    }

    fun toggleFavorite() {
        val space = local.value.space ?: return
        viewModelScope.launch {
            val added = favorites.toggle(space)
            messages.trySend(if (added) "Added to favourites" else "Removed from favourites")
        }
    }

    private fun formFor(spec: SpaceApiSpec, endpointName: String?): Map<Int, FormValue> {
        val endpoint = spec.endpoints.firstOrNull { it.name == endpointName } ?: return emptyMap()
        val defaults = endpoint.parameters.mapIndexed { index, param -> index to initialFormValue(param, param.inputKind()) }.toMap()
        return defaults + drafts.get(spaceId, endpoint.name).orEmpty().filterKeys { it in defaults }
    }

    private suspend fun currentToken(): String? {
        val keys = keys.first()
        val selected = local.value.selectedKeyId
        return (if (keyChosen) keys.firstOrNull { it.id == selected } else keys.firstOrNull { it.isDefault } ?: keys.firstOrNull())?.token
    }

    companion object {
        const val ARG_SPACE_ID = "spaceId"
    }
}
