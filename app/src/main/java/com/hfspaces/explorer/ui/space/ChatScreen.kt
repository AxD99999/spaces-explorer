package com.hfspaces.explorer.ui.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.gradio.ChatMessage
import com.hfspaces.explorer.data.gradio.ChatRole
import com.hfspaces.explorer.data.gradio.ChatShape
import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.gradio.SpaceEndpoint
import com.hfspaces.explorer.data.gradio.buildChatPayload
import com.hfspaces.explorer.data.gradio.chatShape
import com.hfspaces.explorer.data.gradio.extractChatReply
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.CallUpdate
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.ErrorState
import com.hfspaces.explorer.ui.common.FullScreenBox
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class ChatUiState(
    val title: String,
    val loading: Boolean = true,
    val error: String? = null,
    val endpoints: List<SpaceEndpoint> = emptyList(),
    val endpoint: SpaceEndpoint? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** Reply being streamed, shown below the transcript. */
    val streaming: String? = null,
    val sending: Boolean = false
) {
    val shape: ChatShape? get() = endpoint?.chatShape()
}

/** Conversation with a chat-style endpoint; the transcript lives for the process. */
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val spaces: SpacesRepository,
    private val favorites: FavoritesRepository,
    private val apiKeys: ApiKeysRepository,
    private val gradio: GradioClient,
    private val transcripts: ConcurrentHashMap<String, List<ChatMessage>>
) : ViewModel() {
    val spaceId: String = checkNotNull(savedStateHandle.get<String>(SpaceViewModel.ARG_SPACE_ID))

    private val _state = MutableStateFlow(ChatUiState(title = spaceId.substringAfter('/'), messages = transcripts[spaceId].orEmpty()))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var spec: SpaceApiSpec? = null
    private var space: HfSpace? = null
    private var sendJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val token = token()
                val s = spaces.knownSpace(spaceId) ?: favorites.get(spaceId) ?: spaces.fetchSpace(spaceId, token)
                space = s
                val apiSpec = spaces.discoverApi(s, token)
                spec = apiSpec
                val chatEndpoints = apiSpec.endpoints.filter { it.chatShape() != null }
                _state.update {
                    it.copy(
                        title = s.displayTitle,
                        loading = false,
                        endpoints = chatEndpoints,
                        endpoint = chatEndpoints.firstOrNull { e -> e.name.contains("chat") } ?: chatEndpoints.firstOrNull(),
                        error = if (chatEndpoints.isEmpty()) "This Space doesn't have a chat-style endpoint" else null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun selectEndpoint(endpoint: SpaceEndpoint) = _state.update { it.copy(endpoint = endpoint) }

    fun send(text: String) {
        val message = text.trim()
        val apiSpec = spec ?: return
        val endpoint = _state.value.endpoint ?: return
        val shape = endpoint.chatShape() ?: return
        if (message.isEmpty() || _state.value.sending) return
        val history = _state.value.messages
        setMessages(history + ChatMessage(ChatRole.USER, message))
        sendJob = viewModelScope.launch {
            _state.update { it.copy(sending = true, streaming = "") }
            try {
                val payload = buildChatPayload(endpoint, shape, message, history)
                val result = gradio.predict(apiSpec.host, apiSpec.apiPrefix, endpoint.name, payload, token()) { update ->
                    if (update is CallUpdate.Generating) {
                        extractChatReply(update.partial, shape)?.let { partial -> _state.update { it.copy(streaming = partial) } }
                    }
                }
                val reply = extractChatReply(result, shape) ?: _state.value.streaming.orEmpty()
                setMessages(_state.value.messages + ChatMessage(ChatRole.ASSISTANT, reply.ifEmpty { "(empty reply)" }))
            } catch (e: CancellationException) {
                _state.value.streaming?.takeIf { it.isNotEmpty() }?.let {
                    setMessages(_state.value.messages + ChatMessage(ChatRole.ASSISTANT, "$it …"))
                }
                throw e
            } catch (e: Exception) {
                setMessages(_state.value.messages + ChatMessage(ChatRole.ASSISTANT, e.userMessage(), isError = true))
            } finally {
                _state.update { it.copy(sending = false, streaming = null) }
            }
        }
    }

    fun stop() {
        sendJob?.cancel()
    }

    fun clear() {
        stop()
        setMessages(emptyList())
    }

    private fun setMessages(messages: List<ChatMessage>) {
        transcripts[spaceId] = messages
        _state.update { it.copy(messages = messages) }
    }

    private suspend fun token(): String? =
        apiKeys.observe().first().let { keys -> (keys.firstOrNull { it.isDefault } ?: keys.firstOrNull())?.token?.takeIf { it.isNotBlank() } }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val count = state.messages.size + if (state.streaming != null) 1 else 0
    LaunchedEffect(count, state.streaming?.length) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        state.endpoint?.let { Text("Chat · ${it.name}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear conversation") }
                    }
                }
            )
        },
        bottomBar = {
            if (state.endpoint != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Message") },
                            maxLines = 5,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (state.sending) {
                            FilledIconButton(onClick = viewModel::stop) { Icon(Icons.Filled.Stop, contentDescription = "Stop") }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    viewModel.send(input)
                                    input = ""
                                },
                                enabled = input.isNotBlank()
                            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.loading -> FullScreenBox { CircularProgressIndicator() }
            state.error != null && state.endpoint == null -> FullScreenBox { ErrorState(state.error!!, onRetry = viewModel::load) }
            state.messages.isEmpty() && state.streaming == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MessageState(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "Start a conversation",
                    message = if (state.shape?.historyIndex == null) {
                        "This Space answers each message on its own; it doesn't see earlier turns."
                    } else {
                        "Earlier turns are sent along, so you can ask follow-ups."
                    }
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(state.messages) { _, message -> Bubble(message) }
                state.streaming?.let { partial ->
                    item("streaming") {
                        if (partial.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Bubble(ChatMessage(ChatRole.ASSISTANT, partial))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val mine = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        val color = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            mine -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        SelectionContainer {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
