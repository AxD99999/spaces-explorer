package com.hfspaces.explorer.ui.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.gradio.inputKind
import com.hfspaces.explorer.data.gradio.parseOutputs
import com.hfspaces.explorer.data.model.SpaceStage
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.ErrorState
import com.hfspaces.explorer.ui.common.FullScreenBox
import com.hfspaces.explorer.ui.common.OutputList
import com.hfspaces.explorer.ui.common.SectionCard
import com.hfspaces.explorer.ui.common.rememberTicker
import com.hfspaces.explorer.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

@Composable
fun SpaceRunnerScreen(
    viewModel: SpaceViewModel,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onManageKeys: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPresets by rememberSaveable { mutableStateOf(false) }
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    val endpoint = state.endpoint
    val canRun = endpoint != null && !state.isRunning && state.stagingInputs.isEmpty()

    if (showPresets) {
        PresetsDialog(
            presets = state.presets,
            canSave = state.endpoint != null,
            onApply = {
                viewModel.applyPreset(it)
                showPresets = false
            },
            onDelete = { viewModel.deletePreset(it.id) },
            onSave = viewModel::savePreset,
            onDismiss = { showPresets = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.space?.displayTitle ?: state.spaceId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.spaceId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.chatShape != null) {
                        IconButton(onClick = { onOpenChat(state.spaceId) }) { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Open as chat") }
                    }
                    if (state.readySpec != null) {
                        IconButton(onClick = { showPresets = true }) { Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Presets") }
                    }
                    IconButton(onClick = { onOpenDetails(state.spaceId) }) { Icon(Icons.Outlined.Info, contentDescription = "Space details and API") }
                }
            )
        },
        bottomBar = {
            if (state.readySpec != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(16.dp)) {
                        if (state.isRunning) {
                            OutlinedButton(
                                onClick = viewModel::cancel,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Stop")
                            }
                        } else {
                            Button(onClick = viewModel::run, enabled = canRun, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.task != null) "Run again" else "Run")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val spec = state.spec
        when {
            state.space == null && state.spaceError != null -> FullScreenBox { ErrorState(state.spaceError!!, onRetry = viewModel::retry) }
            spec is SpecState.Failed -> FullScreenBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    if (state.canWake || state.wake !is WakeState.Idle) WakeCard(state.wake, onWake = viewModel::wake)
                    ErrorState(spec.message, onRetry = viewModel::retry)
                }
            }
            spec is SpecState.Loading -> FullScreenBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Connecting to the Space…", style = MaterialTheme.typography.bodyMedium)
                    if (state.space?.stage == SpaceStage.SLEEPING) {
                        Text("It's asleep, so waking it can take a minute.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.canWake || state.wake !is WakeState.Idle) WakeCard(state.wake, onWake = viewModel::wake)
                if (state.space?.stage == SpaceStage.ERROR) Banner(Icons.Outlined.ErrorOutline, "Hugging Face reports this Space as broken; runs will probably fail.")

                val endpoints = state.readySpec?.endpoints.orEmpty()
                if (endpoints.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Endpoint", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            endpoints.forEach { ep ->
                                FilterChip(
                                    selected = ep.name == endpoint?.name,
                                    enabled = !state.isRunning,
                                    onClick = { viewModel.selectEndpoint(ep.name) },
                                    label = { Text(ep.name, fontFamily = FontFamily.Monospace) }
                                )
                            }
                        }
                    }
                }

                TokenSelector(state, onSelect = viewModel::selectKey, onManageKeys = onManageKeys)

                if (endpoint == null) {
                    SectionCard("No endpoints") { Text("This Space doesn't publish an API that can be called.") }
                } else {
                    SectionCard(
                        title = "Inputs",
                        action = {
                            TextButton(onClick = viewModel::resetForm, enabled = !state.isRunning) {
                                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    ) {
                        endpoint.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (endpoint.parameters.isEmpty()) Text("This endpoint takes no inputs.", style = MaterialTheme.typography.bodyMedium)
                        endpoint.parameters.forEachIndexed { index, param ->
                            val kind = remember(param) { param.inputKind() }
                            ParameterField(
                                param = param,
                                kind = kind,
                                value = state.formValues[index],
                                enabled = !state.isRunning,
                                isStaging = index in state.stagingInputs,
                                onChange = { viewModel.updateValue(index, it) },
                                onPickFiles = { uris -> viewModel.addFiles(index, uris, (kind as? com.hfspaces.explorer.data.gradio.InputKind.File)?.multiple == true) },
                                onRemoveFile = { viewModel.removeFile(index, it) }
                            )
                        }
                        state.inputError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    }
                }

                state.task?.let { task ->
                    TaskStatusCard(task)
                    val result = task.result
                    if (result != null) {
                        val host = state.readySpec?.host ?: state.space?.let(SpaceIds::resolveHost)
                        val returns = state.readySpec?.endpoints?.firstOrNull { it.name == task.endpoint }?.returns.orEmpty()
                        val outputs = remember(result, host) {
                            host?.let { parseOutputs(result, it, state.readySpec?.apiPrefix ?: "/gradio_api", returns) }.orEmpty()
                        }
                        SectionCard(if (task.isActive) "Output so far" else "Output") {
                            if (outputs.isEmpty()) Text("The Space returned no output.", style = MaterialTheme.typography.bodyMedium)
                            OutputList(outputs, onCopied = { scope.launch { snackbar.showSnackbar("Copied") } })
                            if (task.savedMediaIds.isNotEmpty()) {
                                Text(
                                    "Saved to your outputs library",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalStatusColors.current.running
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun TokenSelector(state: SpaceUiState, onSelect: (String?) -> Unit, onManageKeys: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Key, contentDescription = null, tint = if (state.selectedKey != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("Token", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Column {
            TextButton(onClick = { open = true }, enabled = !state.isRunning, modifier = Modifier.widthIn(max = 220.dp)) {
                Text(state.selectedKey?.name ?: "None (anonymous)", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text("None (anonymous)") }, onClick = {
                    onSelect(null)
                    open = false
                })
                state.apiKeys.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.name + if (key.isDefault) " · default" else "") },
                        onClick = {
                            onSelect(key.id)
                            open = false
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Manage tokens…") }, onClick = {
                    open = false
                    onManageKeys()
                })
            }
        }
    }
    if (state.apiKeys.isEmpty()) {
        Text(
            "Some Spaces (private, gated or ZeroGPU) need a Hugging Face token.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskStatusCard(task: SpaceTask) {
    val colors = LocalStatusColors.current
    val now by rememberTicker(task.isActive)
    val (title, color) = when (task.status) {
        TaskStatus.RUNNING -> (task.progressMessage ?: "Running…") to MaterialTheme.colorScheme.primary
        TaskStatus.COMPLETED -> "Finished" to colors.running
        TaskStatus.ERROR -> "Failed" to colors.error
        TaskStatus.CANCELLED -> "Stopped" to colors.neutral
    }
    SectionCard(title) {
        val eta = task.etaSeconds?.takeIf { task.isActive && it >= 1 }?.let { " · ~${it.roundToInt()}s left" } ?: ""
        Text(
            "${task.endpoint} · %.1fs".format(task.elapsedSeconds(now)) + eta,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
        if (task.isActive) {
            val rank = task.queueRank
            val size = task.queueSize
            if (rank != null && size != null && size > 1) {
                // Queue position as a bar that fills as we move to the front.
                LinearProgressIndicator(progress = { 1f - rank.toFloat() / size }, modifier = Modifier.fillMaxWidth())
            } else {
                val fraction = task.progressFraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        task.error?.takeIf { task.status == TaskStatus.ERROR }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error)
        }
    }
}
