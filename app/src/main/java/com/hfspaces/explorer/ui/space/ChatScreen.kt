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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import com.hfspaces.explorer.data.remote.CallUpdate
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.ChatRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.ErrorState
import com.hfspaces.explorer.ui.common.FullScreenBox
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.userMessage
import com.hfspaces.explorer.ui.markdown.MarkdownText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val title: String,
    val loading: Boolean = true,
    val error: String? = null,
    val endpoints: List<SpaceEndpoint> = emptyList(),
    val endpoint: SpaceEndpoint? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** Reply being streamed, shown below the transcript. */
    val streaming: String? = null,
    val sending: Boolean = false,
    /** How many of the first [messages] come from before the current session. */
    val earlierCount: Int = 0
) {
    val shape: ChatShape? get() = endpoint?.chatShape()

    /**
     * True when the Space, not this app, keeps the conversation: there is no history input, so
     * what it remembers is whatever its session state holds.
     */
    val spaceKeepsHistory: Boolean get() = shape.let { it != null && it.historyIndex == null }
}

/** Conversation with a chat-style endpoint. The transcript is stored, so it survives restarts. */
class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val spaces: SpacesRepository,
    private val favorites: FavoritesRepository,
    private val apiKeys: ApiKeysRepository,
    private val gradio: GradioClient,
    private val chats: ChatRepository
) : ViewModel() {
    val spaceId: String = checkNotNull(savedStateHandle.get<String>(SpaceViewModel.ARG_SPACE_ID))

    private val _state = MutableStateFlow(ChatUiState(title = spaceId.substringAfter('/')))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var spec: SpaceApiSpec? = null
    private var sendJob: Job? = null

    /** Bumped by [clear], so a reply that was still on its way doesn't land in the new conversation. */
    private var conversation = 0

    init {
        viewModelScope.launch {
            // Shown straight away, even when the Space can't be reached.
            val stored = chats.load(spaceId)
            _state.update { it.copy(messages = stored, earlierCount = chats.session(spaceId)?.firstMessage ?: stored.size) }
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val token = apiKeys.defaultToken()
                val space = spaces.knownSpace(spaceId) ?: favorites.get(spaceId) ?: spaces.fetchSpace(spaceId, token)
                val apiSpec = spaces.discoverApi(space, token)
                spec = apiSpec
                val chatEndpoints = apiSpec.endpoints.filter { it.chatShape() != null }
                _state.update {
                    it.copy(
                        title = space.displayTitle,
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

    fun send(text: String) = send(text, retrying = false)

    /** Asks the last question again after a failed reply. */
    fun retry() {
        val messages = _state.value.messages
        val failed = messages.lastOrNull()?.takeIf { it.isError } ?: return
        val question = messages.getOrNull(messages.size - 2)?.takeIf { it.role == ChatRole.USER && failed.role == ChatRole.ASSISTANT } ?: return
        send(question.text, retrying = true)
    }

    private fun send(text: String, retrying: Boolean) {
        val message = text.trim()
        val apiSpec = spec ?: return
        val endpoint = _state.value.endpoint ?: return
        val shape = endpoint.chatShape() ?: return
        if (message.isEmpty() || _state.value.sending) return

        // A retry replaces the failed turn (question and error) rather than adding to it.
        val history = _state.value.messages.let { if (retrying) it.dropLast(2) else it }
        val question = ChatMessage(ChatRole.USER, message)
        val mine = conversation
        _state.update { it.copy(messages = history + question, sending = true, streaming = "") }

        sendJob = viewModelScope.launch {
            var reply: ChatMessage? = null
            try {
                if (retrying) chats.removeLast(spaceId, 2)
                chats.append(spaceId, question)
                // One session per conversation: ChatInterface Spaces remember earlier turns through it.
                val session = chats.session(spaceId) ?: chats.startSession(spaceId, messagesSoFar = history.size)
                val payload = buildChatPayload(endpoint, shape, message, history)
                val result = gradio.predict(apiSpec.host, apiSpec.apiPrefix, endpoint.name, payload, apiKeys.defaultToken(), session.hash) { update ->
                    if (update is CallUpdate.Generating && mine == conversation) {
                        extractChatReply(update.partial, shape)?.let { partial -> _state.update { it.copy(streaming = partial) } }
                    }
                }
                val answer = extractChatReply(result, shape) ?: _state.value.streaming.orEmpty()
                reply = ChatMessage(ChatRole.ASSISTANT, answer.ifEmpty { "(empty reply)" })
            } catch (e: CancellationException) {
                // Stopped by the user: keep what had arrived.
                reply = _state.value.streaming?.takeIf { it.isNotEmpty() && mine == conversation }?.let { ChatMessage(ChatRole.ASSISTANT, "$it …") }
                throw e
            } catch (e: Exception) {
                reply = ChatMessage(ChatRole.ASSISTANT, e.userMessage(), isError = true)
            } finally {
                val answer = reply
                if (mine == conversation) {
                    _state.update { it.copy(messages = if (answer != null) it.messages + answer else it.messages, sending = false, streaming = null) }
                    if (answer != null) withContext(NonCancellable) { chats.append(spaceId, answer) }
                }
            }
        }
    }

    fun stop() {
        sendJob?.cancel()
    }

    /** Forgets the conversation here and starts a fresh session, so the Space forgets it too. */
    fun clear() {
        conversation++
        sendJob?.cancel()
        _state.update { it.copy(messages = emptyList(), streaming = null, sending = false, earlierCount = 0) }
        viewModelScope.launch { chats.clear(spaceId) }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val snackbar = LocalSnackbarHostState.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copied: () -> Unit = { scope.launch { snackbar.showSnackbar("Copied") } }

    // The "earlier messages" note only matters when the Space is the one keeping the history.
    val earlier = state.earlierCount.takeIf { state.spaceKeepsHistory && it in 1..state.messages.size } ?: 0
    val rows = state.messages.size + (if (earlier > 0) 1 else 0) + (if (state.streaming != null) 1 else 0)
    LaunchedEffect(rows) {
        if (rows > 0) listState.animateScrollToItem(rows - 1, Int.MAX_VALUE / 2)
    }
    // Follow a reply as it grows, unless the user is scrolling back through the conversation.
    LaunchedEffect(state.streaming?.length) {
        if (state.streaming != null && rows > 0 && !listState.isScrollInProgress) listState.scrollToItem(rows - 1, Int.MAX_VALUE / 2)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
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
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear conversation") }
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
            state.loading && state.messages.isEmpty() -> FullScreenBox(Modifier.padding(padding)) { CircularProgressIndicator() }
            state.error != null && state.endpoint == null && state.messages.isEmpty() ->
                FullScreenBox(Modifier.padding(padding)) { ErrorState(state.error!!, onRetry = viewModel::load) }
            state.messages.isEmpty() && state.streaming == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MessageState(
                    icon = Icons.AutoMirrored.Outlined.Chat,
                    title = "Start a conversation",
                    message = if (state.spaceKeepsHistory) {
                        "Your messages stay in one session, so chat Spaces remember what was said before. The conversation is saved on this device."
                    } else {
                        "Earlier turns are sent along with each message, so you can ask follow-ups. The conversation is saved on this device."
                    }
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // The transcript can be read while the Space is unreachable; say why sending is off.
                if (state.endpoint == null && !state.loading) {
                    item("offline") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.error ?: "Can't reach the Space right now",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::load) { Text("Retry") }
                        }
                    }
                }
                itemsIndexed(state.messages) { index, message ->
                    if (earlier > 0 && index == earlier) EarlierDivider()
                    Bubble(
                        message = message,
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.text))
                            copied()
                        },
                        onCopied = copied,
                        onRetry = viewModel::retry.takeIf { message.isError && index == state.messages.lastIndex && !state.sending }
                    )
                }
                if (earlier > 0 && earlier == state.messages.size) item("earlier") { EarlierDivider() }
                state.streaming?.let { partial ->
                    item("streaming") {
                        if (partial.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Bubble(ChatMessage(ChatRole.ASSISTANT, partial), onCopy = null, onCopied = copied, onRetry = null)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this conversation?") },
            text = { Text("The messages are removed from this device and the Space starts over.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

/** Marks where a saved conversation was picked up again in a new session. */
@Composable
private fun EarlierDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "The Space no longer remembers the messages above",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun Bubble(message: ChatMessage, onCopy: (() -> Unit)?, onCopied: () -> Unit, onRetry: (() -> Unit)?) {
    val mine = message.role == ChatRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        val background = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            mine -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
        val content = when {
            message.isError -> MaterialTheme.colorScheme.onErrorContainer
            mine -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(
            Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                // Replies are usually Markdown; what the user typed and error text are shown as written.
                if (mine || message.isError) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge, color = content)
                } else {
                    MarkdownText(message.text, style = MaterialTheme.typography.bodyLarge, color = content, onCopied = onCopied)
                }
            }
        }
        if (!mine && (onCopy != null && !message.isError || onRetry != null)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCopy != null && !message.isError) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy reply", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onRetry != null) TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
}
