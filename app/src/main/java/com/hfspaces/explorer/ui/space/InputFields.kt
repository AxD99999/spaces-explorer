package com.hfspaces.explorer.ui.space

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.GradioParameter
import com.hfspaces.explorer.data.gradio.InputKind
import com.hfspaces.explorer.data.gradio.PickedFile
import com.hfspaces.explorer.data.gradio.asInputText
import com.hfspaces.explorer.data.gradio.formatNumber
import kotlin.math.roundToInt

@Composable
fun ParameterField(
    param: GradioParameter,
    kind: InputKind,
    value: FormValue?,
    enabled: Boolean,
    isStaging: Boolean,
    onChange: (FormValue) -> Unit,
    onPickFiles: (List<Uri>) -> Unit,
    onRemoveFile: (PickedFile) -> Unit
) {
    val label = param.displayLabel
    val required = !param.hasDefault
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (kind !is InputKind.Text && kind !is InputKind.Number && kind !is InputKind.Json) {
            Text(
                if (required) label else "$label (optional)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
        val text = (value as? FormValue.Text)?.text.orEmpty()
        when (kind) {
            is InputKind.Text -> OutlinedTextField(
                value = text,
                onValueChange = { onChange(FormValue.Text(it)) },
                label = { Text(if (required) label else "$label (optional)") },
                placeholder = param.example?.asInputText()?.takeIf { it.isNotBlank() }?.let { { Text("e.g. $it", maxLines = 2, overflow = TextOverflow.Ellipsis) } },
                enabled = enabled,
                minLines = if (kind.multiline) 2 else 1,
                maxLines = if (kind.multiline) 8 else 1,
                singleLine = !kind.multiline,
                modifier = Modifier.fillMaxWidth()
            )
            is InputKind.Number -> OutlinedTextField(
                value = text,
                onValueChange = { onChange(FormValue.Text(it)) },
                label = { Text(if (required) label else "$label (optional)") },
                supportingText = rangeHint(kind.min, kind.max, kind.integer)?.let { { Text(it) } },
                isError = text.isNotBlank() && text.trim().toDoubleOrNull() == null,
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (kind.integer) KeyboardType.Number else KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            is InputKind.Slider -> SliderField(kind, text, enabled) { onChange(FormValue.Text(it)) }
            is InputKind.Checkbox -> {
                val checked = (value as? FormValue.Bool)?.checked ?: false
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, enabled = enabled, role = Role.Switch) { onChange(FormValue.Bool(it)) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (checked) "On" else "Off", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
                }
            }
            is InputKind.Choice -> ChoiceField(kind.options, text, enabled) { onChange(FormValue.Text(it)) }
            is InputKind.MultiChoice -> {
                val selected = (value as? FormValue.Multi)?.selected.orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kind.options.forEach { option ->
                        val isOn = option in selected
                        FilterChip(
                            selected = isOn,
                            enabled = enabled,
                            onClick = { onChange(FormValue.Multi(if (isOn) selected - option else selected + option)) },
                            label = { Text(option) }
                        )
                    }
                }
            }
            is InputKind.File -> FileField(
                kind = kind,
                files = (value as? FormValue.Files)?.files.orEmpty(),
                enabled = enabled,
                isStaging = isStaging,
                onPick = onPickFiles,
                onRemove = onRemoveFile
            )
            InputKind.Json -> OutlinedTextField(
                value = text,
                onValueChange = { onChange(FormValue.Text(it)) },
                label = { Text(if (required) label else "$label (optional)") },
                supportingText = { Text("JSON value${param.component?.let { " for $it" } ?: ""}") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                enabled = enabled,
                minLines = 2,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun rangeHint(min: Double?, max: Double?, integer: Boolean): String? = when {
    min != null && max != null -> "Between ${formatNumber(min, integer)} and ${formatNumber(max, integer)}"
    integer -> "Whole number"
    else -> null
}

@Composable
private fun SliderField(kind: InputKind.Slider, text: String, enabled: Boolean, onChange: (String) -> Unit) {
    val current = (text.toDoubleOrNull() ?: kind.min).coerceIn(kind.min, kind.max)
    val span = kind.max - kind.min
    // Discrete ticks only for small integer ranges; otherwise Material draws a tick wall.
    val steps = if (kind.integer && span in 1.0..100.0) span.toInt() - 1 else 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(
            value = current.toFloat(),
            onValueChange = { raw ->
                val v = if (kind.integer) raw.roundToInt().toDouble() else (raw * 100).roundToInt() / 100.0
                onChange(formatNumber(v, kind.integer))
            },
            valueRange = kind.min.toFloat()..kind.max.toFloat(),
            steps = steps.coerceAtLeast(0),
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(formatNumber(current, kind.integer), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ChoiceField(options: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    if (options.size <= 4) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { onSelect(option) },
                    label = { Text(option) }
                )
            }
        }
        return
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            placeholder = { Text("Choose…") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun FileField(
    kind: InputKind.File,
    files: List<PickedFile>,
    enabled: Boolean,
    isStaging: Boolean,
    onPick: (List<Uri>) -> Unit,
    onRemove: (PickedFile) -> Unit
) {
    val single = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { onPick(listOf(it)) } }
    val multiple = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> onPick(uris) }
    val (icon, noun) = when {
        kind.mimeType.startsWith("image") -> Icons.Outlined.Image to if (kind.multiple) "images" else "an image"
        kind.mimeType.startsWith("audio") -> Icons.Outlined.AudioFile to "audio"
        kind.mimeType.startsWith("video") -> Icons.Outlined.VideoFile to "a video"
        else -> Icons.Outlined.AttachFile to if (kind.multiple) "files" else "a file"
    }

    files.forEach { file ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (file.mimeType?.startsWith("image") == true) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                file.sizeBytes?.let { Text(formatBytes(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = { onRemove(file) }, enabled = enabled) { Icon(Icons.Filled.Close, contentDescription = "Remove ${file.name}") }
        }
    }
    if (kind.multiple || files.isEmpty()) {
        OutlinedButton(
            onClick = { if (kind.multiple) multiple.launch(kind.mimeType) else single.launch(kind.mimeType) },
            enabled = enabled && !isStaging
        ) {
            if (isStaging) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (files.isEmpty()) "Choose $noun" else "Add more")
        }
    } else {
        OutlinedButton(onClick = { single.launch(kind.mimeType) }, enabled = enabled && !isStaging) { Text("Replace") }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
