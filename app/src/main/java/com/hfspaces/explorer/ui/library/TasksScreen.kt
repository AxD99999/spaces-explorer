package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.model.SpaceTask
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.EmojiAvatar
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.Pill
import com.hfspaces.explorer.ui.common.rememberTicker
import com.hfspaces.explorer.ui.theme.LocalStatusColors
import java.text.DateFormat
import java.util.Date

@Composable
fun TasksScreen(viewModel: TasksViewModel, onBack: () -> Unit, onOpenRunner: (String) -> Unit, onRerun: (spaceId: String, taskId: String) -> Unit) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val hasFinished by viewModel.hasFinished.collectAsStateWithLifecycle()
    val anyRunning = tasks.any { it.isActive }
    val now by rememberTicker(anyRunning, periodMillis = 500)

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("Runs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = { if (hasFinished) TextButton(onClick = viewModel::clearFinished) { Text("Clear finished") } }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            CenteredBox(Modifier.padding(padding)) {
                MessageState(
                    icon = Icons.Outlined.PlayCircle,
                    title = "No runs yet",
                    message = "Runs keep going in the background while you browse, and show up here."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        now = now,
                        onOpen = { onOpenRunner(task.space.id) },
                        onCancel = { viewModel.cancel(task.id) },
                        onRemove = { viewModel.remove(task.id) },
                        onRerun = { onRerun(task.space.id, task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: SpaceTask, now: Long, onOpen: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit, onRerun: () -> Unit) {
    val colors = LocalStatusColors.current
    val (label, color) = when (task.status) {
        TaskStatus.RUNNING -> "Running" to MaterialTheme.colorScheme.primary
        TaskStatus.COMPLETED -> "Done" to colors.running
        TaskStatus.ERROR -> "Failed" to colors.error
        TaskStatus.CANCELLED -> "Stopped" to colors.neutral
    }
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiAvatar(task.space.displayEmoji, size = 36)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.space.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${task.endpoint} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(task.startTime))} · %.1fs".format(task.elapsedSeconds(now)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Pill(label, color, dot = true)
            }
            if (task.isActive) {
                task.progressMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            task.error?.takeIf { task.status == TaskStatus.ERROR }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (task.isActive) {
                    OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Stop") }
                } else {
                    if (task.savedMediaIds.isNotEmpty()) {
                        Text("${task.savedMediaIds.size} saved", style = MaterialTheme.typography.labelSmall, color = colors.running, modifier = Modifier.weight(1f))
                    }
                    IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = "Remove from history") }
                    if (task.inputs != null) {
                        OutlinedButton(onClick = onRerun) {
                            Icon(Icons.Outlined.Replay, contentDescription = null)
                            Text("Run again", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
