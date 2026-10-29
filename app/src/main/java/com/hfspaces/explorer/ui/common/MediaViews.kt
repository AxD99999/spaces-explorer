package com.hfspaces.explorer.ui.common

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import com.hfspaces.explorer.data.gradio.OutputItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.remote.AppJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Inline audio/video player; the player is released when it leaves composition. */
@OptIn(UnstableApi::class)
@Composable
fun MediaPlayer(source: String, isVideo: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(source) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(source))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = if (isVideo) 3000 else 0
                controllerHideOnTouch = isVideo
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(if (isVideo) 240.dp else 96.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

@Composable
fun RemoteImage(source: String, description: String?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    SubcomposeAsyncImage(
        model = source,
        contentDescription = description,
        contentScale = ContentScale.Fit,
        loading = { Text("Loading image…", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall) },
        error = { Text("Image unavailable — it may have expired on the Space.", modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 420.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    )
}

@Composable
fun OutputList(items: List<OutputItem>, onCopied: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            item.label?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
            when (item.type) {
                GeneratedMediaType.IMAGE -> item.url?.let { url ->
                    RemoteImage(url, item.label ?: item.fileName) { context.openUrl(url) }
                }
                GeneratedMediaType.VIDEO -> item.url?.let { MediaPlayer(it, isVideo = true) }
                GeneratedMediaType.AUDIO -> item.url?.let { MediaPlayer(it, isVideo = false) }
                GeneratedMediaType.FILE -> item.url?.let { url ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(item.fileName ?: url.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        TextButton(onClick = { context.openFile(Uri.parse(url), item.mimeType) }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Open")
                        }
                    }
                }
                GeneratedMediaType.TEXT -> SelectionContainer {
                    Text(item.text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
                GeneratedMediaType.OTHER -> CodeBlock(prettyJson(item.text.orEmpty()), onCopied = onCopied)
            }
        }
    }
}

private val PrettyJson = Json { prettyPrint = true }

fun prettyJson(raw: String): String = runCatching {
    PrettyJson.encodeToString(JsonElement.serializer(), AppJson.parseToJsonElement(raw))
}.getOrDefault(raw)
