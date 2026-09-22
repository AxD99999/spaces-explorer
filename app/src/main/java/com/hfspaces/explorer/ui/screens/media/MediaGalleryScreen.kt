package com.hfspaces.explorer.ui.screens.media

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    mediaList: List<GeneratedMediaItem>,
    onBack: () -> Unit,
    onDeleteMedia: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedMediaItem by remember { mutableStateOf<GeneratedMediaItem?>(null) }

    val filteredList = mediaList.filter {
        when (selectedTypeFilter) {
            "IMAGE" -> it.type == GeneratedMediaType.IMAGE
            "TEXT" -> it.type == GeneratedMediaType.TEXT
            "AUDIO" -> it.type == GeneratedMediaType.AUDIO
            "VIDEO" -> it.type == GeneratedMediaType.VIDEO
            else -> true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Generated Media Gallery",
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
                    if (mediaList.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(imageVector = Icons.Outlined.DeleteSweep, contentDescription = "Clear All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Type filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ALL" to "All (${mediaList.size})", "IMAGE" to "Images", "TEXT" to "Text", "AUDIO" to "Audio", "VIDEO" to "Video").forEach { (typeKey, label) ->
                    FilterChip(
                        selected = selectedTypeFilter == typeKey,
                        onClick = { selectedTypeFilter = typeKey },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                                imageVector = Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "No generated media found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Outputs from Hugging Face model runs will be saved here automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items = filteredList, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMediaItem = item },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(enabled = true)
                        ) {
                            Column {
                                if (item.type == GeneratedMediaType.IMAGE && !item.url.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .background(Slate900)
                                    ) {
                                        AsyncImage(
                                            model = item.url,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (item.type) {
                                                GeneratedMediaType.TEXT -> "📄 Text"
                                                GeneratedMediaType.AUDIO -> "🎵 Audio"
                                                GeneratedMediaType.VIDEO -> "🎬 Video"
                                                else -> "📦 Output"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${item.spaceEmoji} ${item.spaceTitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = item.createdAt,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Details Dialog
    if (selectedMediaItem != null) {
        val item = selectedMediaItem!!
        AlertDialog(
            onDismissRequest = { selectedMediaItem = null },
            title = {
                Text(text = item.title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.type == GeneratedMediaType.IMAGE && !item.url.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate900)
                        ) {
                            AsyncImage(
                                model = item.url,
                                contentDescription = item.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (!item.prompt.isNullOrBlank()) {
                        Text(text = "Prompt:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text(text = item.prompt, style = MaterialTheme.typography.bodyMedium)
                    }

                    if (!item.textContent.isNullOrBlank()) {
                        Text(text = "Output:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = item.textContent,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(text = "Space: ${item.spaceTitle} (${item.spaceId})", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                if (!item.url.isNullOrBlank()) {
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                        context.startActivity(intent)
                    }) {
                        Text("Open Media Link")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    onDeleteMedia(item.id)
                    selectedMediaItem = null
                }) {
                    Text("Delete", color = RedError)
                }
            }
        )
    }
}
