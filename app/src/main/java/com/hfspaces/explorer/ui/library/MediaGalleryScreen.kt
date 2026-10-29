package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.CodeBlock
import com.hfspaces.explorer.ui.common.MediaPlayer
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.RemoteImage
import com.hfspaces.explorer.ui.common.openFile
import com.hfspaces.explorer.ui.common.prettyJson
import com.hfspaces.explorer.ui.common.shareContent
import kotlinx.coroutines.launch
import java.io.File

private enum class MediaFilter(val label: String, val types: Set<GeneratedMediaType>?) {
    ALL("All", null),
    IMAGES("Images", setOf(GeneratedMediaType.IMAGE)),
    VIDEO("Video", setOf(GeneratedMediaType.VIDEO)),
    AUDIO("Audio", setOf(GeneratedMediaType.AUDIO)),
    TEXT("Text", setOf(GeneratedMediaType.TEXT, GeneratedMediaType.OTHER)),
    FILES("Files", setOf(GeneratedMediaType.FILE))
}

@Composable
fun MediaGalleryScreen(viewModel: MediaViewModel, onBack: () -> Unit, onOpenSpace: (String) -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(MediaFilter.ALL) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val visible = items.filter { filter.types == null || it.type in filter.types!! }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("Saved outputs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (items.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete all outputs") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediaFilter.entries.forEach { option ->
                    val count = if (option.types == null) items.size else items.count { it.type in option.types }
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text("${option.label} ($count)") })
                }
            }
            if (visible.isEmpty()) {
                CenteredBox {
                    MessageState(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Nothing saved yet",
                        message = "Outputs from your runs are saved here automatically, including a copy of each file."
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(visible, key = { it.id }) { item -> MediaTile(item) { selectedId = item.id } }
                }
            }
        }
    }

    items.firstOrNull { it.id == selectedId }?.let { item ->
        MediaDetailSheet(
            item = item,
            onDismiss = { selectedId = null },
            onDelete = {
                viewModel.delete(item.id)
                selectedId = null
            },
            onOpenSpace = {
                selectedId = null
                onOpenSpace(item.spaceId)
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all saved outputs?") },
            text = { Text("This removes ${items.size} items and their saved files from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    confirmClear = false
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MediaTile(item: GeneratedMediaItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box(Modifier.fillMaxWidth().height(130.dp).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            val source = item.displaySource
            if (item.type == GeneratedMediaType.IMAGE && source != null) {
                AsyncImage(model = source, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else if (item.textContent != null && item.type == GeneratedMediaType.TEXT) {
                Text(item.textContent, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp))
            } else {
                Icon(
                    when (item.type) {
                        GeneratedMediaType.VIDEO -> Icons.Outlined.VideoFile
                        GeneratedMediaType.AUDIO -> Icons.Outlined.AudioFile
                        GeneratedMediaType.OTHER -> Icons.Outlined.DataObject
                        GeneratedMediaType.TEXT -> Icons.AutoMirrored.Outlined.Notes
                        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.spaceEmoji ?: "🤗"} ${item.spaceTitle}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun MediaDetailSheet(item: GeneratedMediaItem, onDismiss: () -> Unit, onDelete: () -> Unit, onOpenSpace: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val localUri = item.localPath?.let { FileProvider.getUriForFile(context, "${context.packageName}.files", File(it)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${item.spaceEmoji ?: "🤗"} ${item.spaceTitle} · ${item.createdAt}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val source = item.displaySource
            when (item.type) {
                GeneratedMediaType.IMAGE -> source?.let { RemoteImage(it, item.title) }
                GeneratedMediaType.VIDEO -> source?.let { MediaPlayer(it, isVideo = true) }
                GeneratedMediaType.AUDIO -> source?.let { MediaPlayer(it, isVideo = false) }
                GeneratedMediaType.OTHER -> item.textContent?.let { CodeBlock(prettyJson(it), onCopied = { scope.launch { snackbar.showSnackbar("Copied") } }) }
                GeneratedMediaType.TEXT -> item.textContent?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                GeneratedMediaType.FILE -> Text(item.url?.substringAfterLast('/') ?: "File", style = MaterialTheme.typography.bodyMedium)
            }
            if (item.localPath == null && item.url != null) {
                Text(
                    "Only a link to the Space was saved; it may stop working when the Space clears its files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item.prompt?.let {
                Text("Prompt", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { context.shareContent(localUri, item.textContent ?: item.url, item.mimeType) }) { Text("Share") }
                if (localUri != null || item.url != null) {
                    OutlinedButton(onClick = {
                        context.openFile(localUri ?: android.net.Uri.parse(item.url), item.mimeType)
                    }) { Text("Open") }
                }
                OutlinedButton(onClick = onOpenSpace) { Text("Run again") }
            }
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }
}
