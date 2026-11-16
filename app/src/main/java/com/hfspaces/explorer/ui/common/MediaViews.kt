package com.hfspaces.explorer.ui.common

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
import com.hfspaces.explorer.data.gradio.MARKDOWN_MIME
import com.hfspaces.explorer.data.gradio.OutputItem
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.repository.shareUriFor
import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.ui.markdown.MarkdownText
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

/**
 * Shows a run's outputs.
 *
 * @param saved the copies kept in app storage, one per item and in the same order (empty until
 * the run has finished). They are shown instead of the Space's links, which stop working once
 * the Space clears its temporary files and need a token on private Spaces.
 * @param onOpenSaved opens a saved image in the full-screen viewer, by media id.
 */
@Composable
fun OutputList(
    items: List<OutputItem>,
    onCopied: () -> Unit,
    saved: List<GeneratedMediaItem> = emptyList(),
    onOpenSaved: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val copies = remember(items, saved) { savedCopies(items, saved) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            val copy = copies[index]
            val source = copy?.displaySource ?: item.url
            item.label?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
            when (item.type) {
                GeneratedMediaType.IMAGE -> source?.let { image ->
                    RemoteImage(image, item.label ?: item.fileName) {
                        if (copy != null && onOpenSaved != null) onOpenSaved(copy.id) else item.url?.let(context::openUrl)
                    }
                }
                GeneratedMediaType.VIDEO -> source?.let { MediaPlayer(it, isVideo = true) }
                GeneratedMediaType.AUDIO -> source?.let { MediaPlayer(it, isVideo = false) }
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
                        TextButton(onClick = {
                            // A saved file is handed to other apps through the FileProvider, never as a file path.
                            val path = copy?.localPath
                            if (path != null) context.openFile(context.shareUriFor(path), item.mimeType ?: copy.mimeType) else context.openUrl(url)
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Open")
                        }
                    }
                }
                GeneratedMediaType.TEXT -> SelectionContainer {
                    if (item.mimeType == MARKDOWN_MIME) {
                        MarkdownText(item.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, onCopied = onCopied)
                    } else {
                        Text(item.text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                GeneratedMediaType.OTHER -> CodeBlock(prettyJson(item.text.orEmpty()), onCopied = onCopied)
            }
        }
    }
}

/**
 * The saved copy to show for each output, or null where the Space's own link has to do. Copies
 * line up with the outputs by position; when the counts differ they belong to a different run,
 * and a copy without a file on disk is no better than the link.
 */
fun savedCopies(items: List<OutputItem>, saved: List<GeneratedMediaItem>): List<GeneratedMediaItem?> =
    items.mapIndexed { index, item ->
        saved.takeIf { it.size == items.size }?.get(index)?.takeIf { it.localPath != null && it.type == item.type }
    }

private val PrettyJson = Json { prettyPrint = true }

fun prettyJson(raw: String): String = runCatching {
    PrettyJson.encodeToString(JsonElement.serializer(), AppJson.parseToJsonElement(raw))
}.getOrDefault(raw)
