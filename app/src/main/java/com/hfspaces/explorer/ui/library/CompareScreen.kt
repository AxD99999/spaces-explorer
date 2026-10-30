package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.InputKind
import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.gradio.SpaceEndpoint
import com.hfspaces.explorer.data.gradio.chatShape
import com.hfspaces.explorer.data.gradio.initialFormValue
import com.hfspaces.explorer.data.gradio.inputKind
import com.hfspaces.explorer.data.gradio.parseOutputs
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.data.repository.TaskManager
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.EmojiAvatar
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.OutputList
import com.hfspaces.explorer.ui.common.userMessage
import com.hfspaces.explorer.ui.theme.LocalStatusColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompareSlot(
    val spaceId: String,
    val space: HfSpace? = null,
    val spec: SpaceApiSpec? = null,
    val endpoint: SpaceEndpoint? = null,
    val promptIndex: Int? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val taskId: String? = null,
    val task: SpaceTask? = null
)

data class CompareUiState(val slots: List<CompareSlot> = emptyList()) {
    val canRun: Boolean get() = slots.any { it.promptIndex != null } && slots.none { it.task?.isActive == true }
    val canAdd: Boolean get() = slots.size < MAX_SLOTS

    companion object {
        const val MAX_SLOTS = 3
    }
}

/** Runs one prompt on up to three Spaces through the normal task pipeline (so results are kept). */
class CompareViewModel(
    private val savedState: SavedStateHandle,
    private val spaces: SpacesRepository,
    private val favorites: FavoritesRepository,
    private val apiKeys: ApiKeysRepository,
    private val tasks: TaskManager
) : ViewModel() {
    private val slots = MutableStateFlow<List<CompareSlot>>(emptyList())

    val state: StateFlow<CompareUiState> = combine(slots, tasks.tasks) { list, all ->
        CompareUiState(list.map { slot -> slot.copy(task = slot.taskId?.let { id -> all.firstOrNull { it.id == id } }) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    init {
        savedState.get<ArrayList<String>>(KEY_IDS)?.forEach(::add)
    }

    fun add(spaceId: String) {
        if (slots.value.any { it.spaceId == spaceId } || slots.value.size >= CompareUiState.MAX_SLOTS) return
        slots.update { it + CompareSlot(spaceId) }
        persistIds()
        viewModelScope.launch {
            try {
                val token = token()
                val space = spaces.knownSpace(spaceId) ?: favorites.get(spaceId) ?: spaces.fetchSpace(spaceId, token)
                val spec = spaces.discoverApi(space, token)
                val endpoint = pickEndpoint(spec.endpoints)
                    ?: throw IllegalStateException("No endpoint with a text input to compare")
                val promptIndex = endpoint.chatShape()?.messageIndex
                    ?: endpoint.parameters.indexOfFirst { it.inputKind() is InputKind.Text }
                updateSlot(spaceId) { it.copy(space = space, spec = spec, endpoint = endpoint, promptIndex = promptIndex, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateSlot(spaceId) { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun remove(spaceId: String) {
        slots.update { list -> list.filterNot { it.spaceId == spaceId } }
        persistIds()
    }

    fun runAll(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val token = token()
            slots.value.forEach { slot ->
                val space = slot.space ?: return@forEach
                val spec = slot.spec ?: return@forEach
                val endpoint = slot.endpoint ?: return@forEach
                val index = slot.promptIndex ?: return@forEach
                val values = endpoint.parameters.mapIndexed { i, p -> i to initialFormValue(p, p.inputKind()) }.toMap() +
                    (index to FormValue.Text(prompt))
                val taskId = tasks.start(space, spec, endpoint, values, token)
                updateSlot(slot.spaceId) { it.copy(taskId = taskId, error = null) }
            }
        }
    }

    /** Prefer an endpoint whose only required input is text, so defaults can fill the rest. */
    private fun pickEndpoint(endpoints: List<SpaceEndpoint>): SpaceEndpoint? {
        val withText = endpoints.filter { ep -> ep.parameters.any { it.inputKind() is InputKind.Text } }
        return withText.firstOrNull { ep ->
            ep.parameters.count { !it.hasDefault && it.inputKind() !is InputKind.Text } == 0
        } ?: withText.firstOrNull()
    }

    private fun updateSlot(spaceId: String, change: (CompareSlot) -> CompareSlot) {
        slots.update { list -> list.map { if (it.spaceId == spaceId) change(it) else it } }
    }

    private fun persistIds() {
        savedState[KEY_IDS] = ArrayList(slots.value.map { it.spaceId })
    }

    private suspend fun token(): String? =
        apiKeys.observe().first().let { keys -> (keys.firstOrNull { it.isDefault } ?: keys.firstOrNull())?.token?.takeIf { it.isNotBlank() } }

    private companion object {
        const val KEY_IDS = "compare_ids"
    }
}

@Composable
fun CompareScreen(viewModel: CompareViewModel, picker: SpacePickerViewModel, onBack: () -> Unit, onOpenRunner: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Compare") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.canAdd) IconButton(onClick = { picking = true }) { Icon(Icons.Filled.Add, contentDescription = "Add a Space") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt") },
                    maxLines = 4,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.runAll(prompt) }, enabled = state.canRun && prompt.isNotBlank()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Run all")
                }
            }
            if (state.slots.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MessageState(
                        icon = Icons.Outlined.CompareArrows,
                        title = "Compare Spaces",
                        message = "Add up to three Spaces, type one prompt, and see their outputs side by side.",
                        actionLabel = "Add a Space",
                        onAction = { picking = true }
                    )
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.slots, key = { it.spaceId }) { slot ->
                        CompareColumn(
                            slot = slot,
                            onRemove = { viewModel.remove(slot.spaceId) },
                            onOpen = { onOpenRunner(slot.spaceId) },
                            onCopied = { scope.launch { snackbar.showSnackbar("Copied") } }
                        )
                    }
                    if (state.canAdd) {
                        item("add") {
                            OutlinedButton(onClick = { picking = true }, modifier = Modifier.width(160.dp).height(120.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Space")
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Box(Modifier.fillMaxHeight(0.85f)) {
                SpacePicker(
                    viewModel = picker,
                    exclude = remember(state.slots) { state.slots.map { it.spaceId }.toSet() },
                    onPick = {
                        viewModel.add(it)
                        picking = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CompareColumn(slot: CompareSlot, onRemove: () -> Unit, onOpen: () -> Unit, onCopied: () -> Unit) {
    val colors = LocalStatusColors.current
    Card(
        modifier = Modifier.width(300.dp).fillMaxHeight().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiAvatar(slot.space?.displayEmoji ?: "🤗", size = 32)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(slot.space?.displayTitle ?: slot.spaceId, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    slot.endpoint?.let { Text(it.name, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${slot.spaceId}") }
            }
            val task = slot.task
            when {
                slot.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting…", style = MaterialTheme.typography.bodySmall)
                }
                slot.error != null -> Text(slot.error, color = colors.error, style = MaterialTheme.typography.bodySmall)
                task == null -> Text("Ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                task.isActive -> {
                    Text(task.progressMessage ?: "Running…", style = MaterialTheme.typography.bodySmall)
                    val fraction = task.progressFraction
                    if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                task.status == TaskStatus.ERROR -> Text(task.error ?: "Failed", color = colors.error, style = MaterialTheme.typography.bodySmall)
                else -> Text("%.1fs".format(task.elapsedSeconds()), style = MaterialTheme.typography.labelSmall, color = colors.running)
            }
            val spec = slot.spec
            val result = task?.result
            if (spec != null && result != null) {
                val outputs = remember(result) { parseOutputs(result, spec.host, spec.apiPrefix, slot.endpoint?.returns.orEmpty()) }
                OutputList(outputs, onCopied = onCopied)
            }
            if (slot.space != null) OutlinedButton(onClick = onOpen) { Text("Open runner") }
        }
    }
}
