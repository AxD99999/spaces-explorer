package com.hfspaces.explorer.ui.library

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.repository.shareUriFor
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.shareContent
import kotlinx.coroutines.launch

/** Full-screen, swipeable, zoomable view of saved images. */
@Composable
fun ViewerScreen(viewModel: MediaViewModel, startId: String, onBack: () -> Unit) {
    val all by viewModel.items.collectAsStateWithLifecycle()
    val images = remember(all) { all.filter { it.type == GeneratedMediaType.IMAGE && it.displaySource != null } }
    if (images.isEmpty()) return
    val start = images.indexOfFirst { it.id == startId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { images.size }
    var chrome by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val current = images.getOrNull(pager.currentPage)

    val save: (GeneratedMediaItem) -> Unit = { item ->
        scope.launch {
            val message = viewModel.saveToGallery(item)
            snackbar.showSnackbar(message)
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) current?.let(save) else scope.launch { snackbar.showSnackbar("Storage permission is needed to save on this Android version") }
    }

    // Edge to edge on purpose: the image sits under the system bars, only the chrome is inset.
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { images[it].id }) { page ->
            ZoomableImage(images[page], onTap = { chrome = !chrome })
        }
        AnimatedVisibility(visible = chrome, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val white = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    IconButton(onClick = onBack, colors = white) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                    Text(
                        "${pager.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    current?.let { item ->
                        IconButton(onClick = {
                            context.shareContent(item.localPath?.let(context::shareUriFor), item.url, item.mimeType)
                        }, colors = white) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
                        IconButton(onClick = {
                            if (viewModel.needsLegacyPermission) legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save(item)
                        }, colors = white) { Icon(Icons.Outlined.Download, contentDescription = "Save to gallery") }
                    }
                }
                current?.let { item ->
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(16.dp)
                    ) {
                        Text(item.prompt ?: item.title, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text("${item.spaceEmoji ?: "🤗"} ${item.spaceTitle} · ${item.createdAt}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp))
    }
}

@Composable
private fun ZoomableImage(item: GeneratedMediaItem, onTap: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = item.displaySource,
        contentDescription = item.title,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = (Offset(size.width / 2f, size.height / 2f) - tap) * 1.5f
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                // Only consume touches while pinching or zoomed in, so the pager can still swipe at 1x.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    )
}
