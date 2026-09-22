package com.hfspaces.explorer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfspaces.explorer.data.model.HF_CATEGORIES
import com.hfspaces.explorer.data.model.SortOption
import com.hfspaces.explorer.data.model.SpaceFilterState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    filterState: SpaceFilterState,
    onDismiss: () -> Unit,
    onApply: (SpaceFilterState) -> Unit
) {
    var tempState by remember { mutableStateOf(filterState) }
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = modalBottomSheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Filter & Sort Spaces",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(onClick = { tempState = SpaceFilterState() }) {
                    Text("Reset All", color = MaterialTheme.colorScheme.primary)
                }
            }

            HorizontalDivider()

            // Sort By Section
            Text(
                text = "Sort By",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SortOption.values().forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = tempState.sortBy == option,
                            onClick = { tempState = tempState.copy(sortBy = option) }
                        )
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            HorizontalDivider()

            // Runtime Status Section
            Text(
                text = "Runtime Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "running" to "Running", "sleeping" to "Sleeping").forEach { (key, label) ->
                    FilterChip(
                        selected = tempState.status == key,
                        onClick = { tempState = tempState.copy(status = key) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            HorizontalDivider()

            // SDK Section
            Text(
                text = "Framework / SDK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All", "gradio" to "Gradio", "docker" to "Docker", "streamlit" to "Streamlit", "static" to "Static").forEach { (key, label) ->
                    FilterChip(
                        selected = tempState.sdk == key,
                        onClick = { tempState = tempState.copy(sdk = key) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            HorizontalDivider()

            // Toggles (Pinned & Featured)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Pinned Spaces Only",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = tempState.pinnedOnly,
                    onCheckedChange = { tempState = tempState.copy(pinnedOnly = it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Featured Spaces Only",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = tempState.featuredOnly,
                    onCheckedChange = { tempState = tempState.copy(featuredOnly = it) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onApply(tempState)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Apply Filters", color = Color.White)
                }
            }
        }
    }
}
