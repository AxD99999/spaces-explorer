package com.hfspaces.explorer.ui.screens.details

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceApiSpec
import com.hfspaces.explorer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceDetailsScreen(
    space: HfSpace,
    spec: SpaceApiSpec?,
    isLoadingSpec: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onRunSpace: (HfSpace) -> Unit,
    onToggleFavorite: (HfSpace) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var activeCodeTab by remember { mutableStateOf("python") }
    var showCopiedSnackbar by remember { mutableStateOf(false) }

    val hostUrl = spec?.host ?: "https://${space.subdomain ?: space.id.replace('/', '-').lowercase()}.hf.space"
    val primaryEndpoint = spec?.endpoints?.firstOrNull()?.name ?: "/predict"

    val pythonSnippet = """
# pip install gradio_client
from gradio_client import Client

client = Client("${space.id}")
result = client.predict(
    api_name="$primaryEndpoint"
)
print(result)
    """.trimIndent()

    val jsSnippet = """
// npm install @gradio/client
import { Client } from "@gradio/client";

const app = await Client.connect("${space.id}");
const result = await app.predict("$primaryEndpoint", []);
console.log(result.data);
    """.trimIndent()

    val curlSnippet = """
curl -X POST "$hostUrl/gradio_api/call$primaryEndpoint" \
  -H "Content-Type: application/json" \
  -d '{"data": []}'
    """.trimIndent()

    val activeSnippet = when (activeCodeTab) {
        "python" -> pythonSnippet
        "js" -> jsSnippet
        else -> curlSnippet
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = space.displayTitle,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onToggleFavorite(space) }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) RedError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/spaces/${space.id}"))
                        context.startActivity(intent)
                    }) {
                        Icon(imageVector = Icons.Outlined.OpenInBrowser, contentDescription = "Open in HF")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onRunSpace(space) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Run Model in App",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = space.displayEmoji, fontSize = 32.sp)
                        Column {
                            Text(
                                text = space.displayTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "by ${space.displayAuthor}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    val desc = space.shortDescription ?: space.ai_short_description ?: ""
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Stat chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(
                                text = "❤️ ${space.likes ?: 0} likes",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(
                                text = "⚡ ${space.hardwareType}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(
                                text = "📦 ${space.sdk ?: "gradio"}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Code Snippets Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "API Integration Code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(activeSnippet))
                            showCopiedSnackbar = true
                        }) {
                            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = "Copy")
                        }
                    }

                    // Tabs
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("python" to "Python", "js" to "JavaScript", "curl" to "cURL").forEach { (tabKey, label) ->
                            FilterChip(
                                selected = activeCodeTab == tabKey,
                                onClick = { activeCodeTab = tabKey },
                                label = { Text(label) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Code box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate950)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = activeSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Slate300
                            )
                        )
                    }
                }
            }

            // Endpoints Specification Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "API Endpoints Specification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isLoadingSpec) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Loading endpoints schema...", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (spec != null && spec.endpoints.isNotEmpty()) {
                        spec.endpoints.forEach { ep ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = ep.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (ep.parameters.isNotEmpty()) {
                                        Text(
                                            text = "Parameters: ${ep.parameters.joinToString { "${it.label} (${it.component ?: "input"})" }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (ep.returns.isNotEmpty()) {
                                        Text(
                                            text = "Returns: ${ep.returns.joinToString { it.label }}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EmeraldRunning
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Standard /predict endpoint available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
