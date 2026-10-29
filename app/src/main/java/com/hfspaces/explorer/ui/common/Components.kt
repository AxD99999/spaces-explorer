package com.hfspaces.explorer.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceStage
import com.hfspaces.explorer.ui.theme.LocalStatusColors

@Composable
fun stageColor(stage: SpaceStage): Color {
    val colors = LocalStatusColors.current
    return when (stage) {
        SpaceStage.RUNNING -> colors.running
        SpaceStage.SLEEPING -> colors.sleeping
        SpaceStage.BUILDING -> colors.building
        SpaceStage.ERROR -> colors.error
        SpaceStage.PAUSED, SpaceStage.UNKNOWN -> colors.neutral
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dot) Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun StagePill(stage: SpaceStage, modifier: Modifier = Modifier) {
    Pill(stage.label, stageColor(stage), modifier, dot = true)
}

@Composable
fun SpaceBadges(space: HfSpace, modifier: Modifier = Modifier) {
    val colors = LocalStatusColors.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StagePill(space.stage)
        Pill(space.category, colors.category)
        Pill(space.sdkLabel, colors.sdk)
        space.hardwareLabel?.let { Pill(it, colors.neutral) }
    }
}

@Composable
fun EmojiAvatar(emoji: String, modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size / 2).sp)
    }
}

@Composable
fun SpaceCard(
    space: HfSpace,
    isFavorite: Boolean,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiAvatar(space.displayEmoji)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        space.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "by ${space.displayAuthor}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FavoriteButton(isFavorite, onToggleFavorite)
            }
            space.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SpaceBadges(space)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${space.likes}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "${space.likes} likes" }
                )
                Spacer(Modifier.weight(1f))
                if (space.isRunnable) {
                    FilledTonalButton(onClick = onRun) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Run")
                    }
                } else {
                    OutlinedButton(onClick = onOpen) {
                        Text("Details")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
            tint = if (isFavorite) LocalStatusColors.current.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Placeholder card with a pulsing fill while the first page loads. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(block))
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(14.dp).fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)).background(block))
                    Box(Modifier.height(10.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(4.dp)).background(block))
                }
            }
            Box(Modifier.height(12.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(block))
            Box(Modifier.height(12.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(4.dp)).background(block))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { Box(Modifier.height(20.dp).width(64.dp).clip(RoundedCornerShape(8.dp)).background(block)) }
            }
        }
    }
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        icon = Icons.Outlined.CloudOff,
        title = "Couldn't load",
        message = message,
        modifier = modifier,
        actionLabel = "Try again",
        onAction = onRetry
    )
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier, onCopied: (() -> Unit)? = null) {
    val clipboard = LocalClipboardManager.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
    ) {
        SelectionContainer {
            Text(
                code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 48.dp)
            )
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(code))
                onCopied?.invoke()
            },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy code", modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FullScreenBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Opens a URL without crashing when no app can handle it. */
fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

/** Opens a local or remote file with a viewer app. */
fun Context.openFile(uri: Uri, mimeType: String?) {
    try {
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

fun Context.shareContent(uri: Uri?, text: String?, mimeType: String?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        if (uri != null) {
            type = mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.orEmpty())
        }
    }
    try {
        startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Nothing to share with", Toast.LENGTH_SHORT).show()
    }
}
