package com.hfspaces.explorer.ui.screens.running

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hfspaces.explorer.data.model.ActiveSpaceTask
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentlyRunningScreen(
    tasks: List<ActiveSpaceTask>,
    onBack: () -> Unit,
    onOpenSpaceRunner: (HfSpace) -> Unit,
    onCancelTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onClearCompleted: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Running Tasks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (tasks.any { it.status != TaskStatus.RUNNING }) {
                        TextButton(onClick = onClearCompleted) {
                            Text("Clear Completed")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "No active background tasks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tasks started in Space Runner will be tracked here in real-time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = tasks, key = { it.id }) { task ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(enabled = true)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(text = task.space.displayEmoji, fontSize = 20.sp)
                                    Column {
                                        Text(
                                            text = task.space.displayTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = task.endpoint,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (task.status) {
                                        TaskStatus.RUNNING -> AmberSleepBg
                                        TaskStatus.COMPLETED -> EmeraldBg
                                        TaskStatus.ERROR -> RedErrorBg
                                    }
                                ) {
                                    Text(
                                        text = "${task.status.name} (${task.elapsedSeconds}s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (task.status) {
                                            TaskStatus.RUNNING -> AmberPrimary
                                            TaskStatus.COMPLETED -> EmeraldRunning
                                            TaskStatus.ERROR -> RedError
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (task.status == TaskStatus.RUNNING) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = AmberPrimary
                                )
                            }

                            if (task.status == TaskStatus.ERROR) {
                                Text(
                                    text = task.error ?: "Failed to execute model",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RedError
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (task.status == TaskStatus.RUNNING) {
                                    OutlinedButton(
                                        onClick = { onCancelTask(task.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                                    ) {
                                        Text("Cancel")
                                    }
                                } else {
                                    IconButton(onClick = { onRemoveTask(task.id) }) {
                                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Remove")
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { onOpenSpaceRunner(task.space) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("View Details", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
