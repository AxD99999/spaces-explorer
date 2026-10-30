package com.hfspaces.explorer.ui.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfspaces.explorer.data.repository.InputPreset

/** Explains that the Space is asleep and lets the user wake it, showing progress. */
@Composable
fun WakeCard(wake: WakeState, onWake: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (wake) {
                is WakeState.Waking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                is WakeState.Failed -> Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                WakeState.Idle -> Icon(Icons.Outlined.Bedtime, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (wake) {
                        is WakeState.Waking -> "Waking up: ${wake.stage}"
                        is WakeState.Failed -> "Couldn't wake it"
                        WakeState.Idle -> "This Space is asleep"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when (wake) {
                        is WakeState.Waking -> "Usually takes 30 seconds to a few minutes. You can keep browsing."
                        is WakeState.Failed -> wake.message
                        WakeState.Idle -> "Wake it now so your first run doesn't wait."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (wake !is WakeState.Waking) {
                FilledTonalButton(onClick = onWake) { Text(if (wake is WakeState.Failed) "Retry" else "Wake up") }
            }
        }
    }
}

/** Lists saved input presets for this Space and saves the current inputs as a new one. */
@Composable
fun PresetsDialog(
    presets: List<InputPreset>,
    canSave: Boolean,
    onApply: (InputPreset) -> Unit,
    onDelete: (InputPreset) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (presets.isEmpty()) {
                    Text("No presets yet. Save the current inputs to reuse them later.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            ListItem(
                                headlineContent = { Text(preset.name) },
                                supportingContent = { Text(preset.endpoint, style = MaterialTheme.typography.labelSmall) },
                                trailingContent = {
                                    Row {
                                        TextButton(onClick = { onApply(preset) }) { Text("Apply") }
                                        IconButton(onClick = { onDelete(preset) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Delete ${preset.name}")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                if (canSave) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text("Save current inputs as…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (canSave) {
                TextButton(
                    onClick = {
                        onSave(name)
                        name = ""
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
