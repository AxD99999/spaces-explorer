package com.hfspaces.explorer.ui.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.gradio.InputKind
import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.gradio.SpaceEndpoint
import com.hfspaces.explorer.data.gradio.inputKind
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.SpaceStage
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.CodeBlock
import com.hfspaces.explorer.ui.common.EmojiAvatar
import com.hfspaces.explorer.ui.common.ErrorState
import com.hfspaces.explorer.ui.common.FavoriteButton
import com.hfspaces.explorer.ui.common.FullScreenBox
import com.hfspaces.explorer.ui.common.SectionCard
import com.hfspaces.explorer.ui.common.SpaceBadges
import com.hfspaces.explorer.ui.common.openUrl
import com.hfspaces.explorer.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch

@Composable
fun SpaceDetailsScreen(viewModel: SpaceViewModel, onBack: () -> Unit, onRun: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.space?.displayTitle ?: state.spaceId, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (state.space != null) FavoriteButton(state.isFavorite, viewModel::toggleFavorite)
                    IconButton(onClick = { context.openUrl("https://huggingface.co/spaces/${state.spaceId}") }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Open on Hugging Face")
                    }
                }
            )
        },
        bottomBar = {
            val space = state.space
            if (space != null && space.isRunnable) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { onRun(space.id) },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run in app")
                    }
                }
            }
        }
    ) { padding ->
        val space = state.space
        when {
            space == null && state.spaceError != null -> FullScreenBox { ErrorState(state.spaceError!!, onRetry = viewModel::retry) }
            space == null -> FullScreenBox { CircularProgressIndicator() }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Header(space)
                if (space.isRunnable) {
                    ApiSection(state.spec, onRetry = viewModel::retry, onCopied = { scope.launch { snackbar.showSnackbar("Copied") } })
                } else {
                    SectionCard("Not runnable here") {
                        Text(
                            "This is a ${space.sdkLabel} Space. Only Gradio Spaces expose an API this app can call. Open it on Hugging Face to use it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { context.openUrl(space.hubUrl) }) { Text("Open on Hugging Face") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(space: HfSpace) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiAvatar(space.displayEmoji, size = 56)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(space.displayTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("by ${space.displayAuthor} · ♥ ${space.likes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        space.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        SpaceBadges(space)
        space.runtime?.errorMessage?.takeIf { space.stage == SpaceStage.ERROR }?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(12.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(error.take(400), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
        if (space.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                space.tags.filterNot { it.startsWith("region:") }.take(12).forEach { tag ->
                    Text("#$tag", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ApiSection(spec: SpecState, onRetry: () -> Unit, onCopied: () -> Unit) {
    when (spec) {
        SpecState.Loading -> SectionCard("API") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Reading the Space's API… sleeping Spaces can take a minute to wake.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is SpecState.Failed -> SectionCard("API") {
            ErrorState(spec.message, onRetry = onRetry)
        }
        is SpecState.Ready -> {
            var selected by rememberSaveable { mutableStateOf(spec.spec.endpoints.firstOrNull()?.name) }
            val endpoint = spec.spec.endpoints.firstOrNull { it.name == selected } ?: spec.spec.endpoints.firstOrNull()
            SectionCard("API endpoints (${spec.spec.endpoints.size})") {
                if (spec.spec.endpoints.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        spec.spec.endpoints.forEach { ep ->
                            FilterChip(selected = ep.name == endpoint?.name, onClick = { selected = ep.name }, label = { Text(ep.name, fontFamily = FontFamily.Monospace) })
                        }
                    }
                }
                if (endpoint == null) {
                    Text("This Space doesn't publish any API endpoints.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    EndpointDetails(endpoint)
                }
            }
            if (endpoint != null) SnippetSection(spec.spec, endpoint, onCopied)
        }
    }
}

@Composable
private fun EndpointDetails(endpoint: SpaceEndpoint) {
    val colors = LocalStatusColors.current
    endpoint.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    Text("Inputs", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    if (endpoint.parameters.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
    endpoint.parameters.forEach { param ->
        Row(verticalAlignment = Alignment.Top) {
            Text(
                param.parameterName ?: param.displayLabel,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.widthIn(min = 96.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull(param.component, param.pythonType?.type, if (param.hasDefault) "optional" else "required").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Text("Outputs", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    if (endpoint.returns.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
    endpoint.returns.forEach { ret ->
        Text(
            listOfNotNull(ret.label, ret.component).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.running
        )
    }
}

@Composable
private fun SnippetSection(spec: SpaceApiSpec, endpoint: SpaceEndpoint, onCopied: () -> Unit) {
    val snippets = remember(spec, endpoint) { codeSnippets(spec, endpoint) }
    var tab by rememberSaveable { mutableStateOf(snippets.keys.first()) }
    SectionCard("Use from code") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            snippets.keys.forEach { key -> FilterChip(selected = key == tab, onClick = { tab = key }, label = { Text(key) }) }
        }
        CodeBlock(snippets[tab] ?: snippets.values.first(), onCopied = onCopied)
    }
}

/** Gradio 5 publishes ready-made snippets; otherwise build equivalent ones from the schema. */
internal fun codeSnippets(spec: SpaceApiSpec, endpoint: SpaceEndpoint): Map<String, String> {
    val published = endpoint.codeSnippets
    val python = published["python"] ?: run {
        val args = endpoint.parameters.joinToString("") { p ->
            "\t${p.parameterName ?: "arg"}=${p.example?.toString() ?: "None"},\n"
        }
        "from gradio_client import Client\n\nclient = Client(\"${spec.spaceId}\")\nresult = client.predict(\n$args\tapi_name=\"${endpoint.name}\",\n)\nprint(result)"
    }
    val js = published["javascript"] ?: run {
        val args = endpoint.parameters.joinToString(",\n") { p -> "\t\t${p.parameterName ?: "arg"}: ${p.example?.toString() ?: "null"}" }
        "import { Client } from \"@gradio/client\";\n\nconst client = await Client.connect(\"${spec.spaceId}\");\n" +
            "const result = await client.predict(\"${endpoint.name}\", {\n$args\n});\n\nconsole.log(result.data);"
    }
    val base = spec.host.toString().trimEnd('/') + spec.apiPrefix
    val api = endpoint.name.trim('/')
    val data = endpoint.parameters.joinToString(", ", prefix = "[", postfix = "]") { p ->
        if (p.inputKind() is InputKind.File) "{\"path\": \"<uploaded path>\", \"meta\": {\"_type\": \"gradio.FileData\"}}"
        else p.example?.toString() ?: "null"
    }
    val curl = "EVENT_ID=$(curl -s -X POST $base/call/$api \\\n  -H \"Content-Type: application/json\" \\\n  -d '{\"data\": $data}' \\\n" +
        "  | sed -E 's/.*\"event_id\": ?\"([^\"]+)\".*/\\1/')\n\ncurl -N $base/call/$api/\$EVENT_ID"
    return linkedMapOf("Python" to python, "JavaScript" to js, "cURL" to curl)
}
