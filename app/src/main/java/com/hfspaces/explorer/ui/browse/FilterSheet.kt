package com.hfspaces.explorer.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfspaces.explorer.data.model.ALL
import com.hfspaces.explorer.data.model.SortOption
import com.hfspaces.explorer.data.model.SpaceFilterState
import com.hfspaces.explorer.data.model.StatusFilter

private val SDKS = listOf(ALL to "All", "gradio" to "Gradio", "docker" to "Docker", "streamlit" to "Streamlit", "static" to "Static")

@Composable
fun FilterSheet(
    current: SpaceFilterState,
    hardwareOptions: List<String>,
    onDismiss: () -> Unit,
    onApply: (SpaceFilterState) -> Unit
) {
    var draft by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filter & sort", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { draft = SpaceFilterState(search = draft.search, category = draft.category) }) { Text("Reset") }
            }
            Text(
                "These apply to the Spaces loaded so far. Search and categories query Hugging Face directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ChipSection("Sort by") {
                SortOption.entries.forEach { option ->
                    FilterChip(selected = draft.sortBy == option, onClick = { draft = draft.copy(sortBy = option) }, label = { Text(option.displayName) })
                }
            }
            ChipSection("Status") {
                StatusFilter.entries.forEach { status ->
                    FilterChip(selected = draft.status == status, onClick = { draft = draft.copy(status = status) }, label = { Text(status.label) })
                }
            }
            ChipSection("SDK") {
                SDKS.forEach { (key, label) ->
                    FilterChip(selected = draft.sdk == key, onClick = { draft = draft.copy(sdk = key) }, label = { Text(label) })
                }
            }
            if (hardwareOptions.isNotEmpty()) {
                ChipSection("Hardware") {
                    (listOf(ALL) + hardwareOptions).forEach { hw ->
                        FilterChip(
                            selected = draft.hardware == hw,
                            onClick = { draft = draft.copy(hardware = hw) },
                            label = { Text(if (hw == ALL) "Any" else hw) }
                        )
                    }
                }
            }
            HorizontalDivider()
            SwitchRow("Pinned Spaces only", draft.pinnedOnly) { draft = draft.copy(pinnedOnly = it) }
            SwitchRow("Featured Spaces only", draft.featuredOnly) { draft = draft.copy(featuredOnly = it) }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun ChipSection(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
