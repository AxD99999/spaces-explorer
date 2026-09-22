package com.hfspaces.explorer.ui.screens.runner

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hfspaces.explorer.data.model.*
import com.hfspaces.explorer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceRunnerScreen(
    space: HfSpace,
    spec: SpaceApiSpec?,
    isLoadingSpec: Boolean,
    apiKeys: List<UserApiKey>,
    activeTask: ActiveSpaceTask?,
    onBack: () -> Unit,
    onStartInference: (endpoint: String, formValues: Map<String, Any>, payload: List<Any?>, token: String?) -> Unit,
    onCancelInference: (taskId: String) -> Unit,
    onOpenApiKeys: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val endpoints = spec?.endpoints?.ifEmpty {
        listOf(
            SpaceEndpointSpec(
                name = "/predict",
                parameters = listOf(
                    SpaceEndpointParam(label = "Prompt", parameter_name = "prompt", component = "textbox", example_input = "")
                )
            )
        )
    } ?: listOf(
        SpaceEndpointSpec(
            name = "/predict",
            parameters = listOf(
                SpaceEndpointParam(label = "Prompt", parameter_name = "prompt", component = "textbox", example_input = "")
            )
        )
    )

    var selectedEndpointName by remember { mutableStateOf(endpoints.firstOrNull()?.name ?: "/predict") }
    var selectedKeyId by remember { mutableStateOf<String?>(apiKeys.find { it.isDefault }?.id ?: apiKeys.firstOrNull()?.id) }

    val currentEndpoint = endpoints.find { it.name == selectedEndpointName } ?: endpoints.first()
    val formValues = remember { mutableStateMapOf<String, Any>() }

    // Initialize form defaults for selected endpoint
    LaunchedEffect(currentEndpoint) {
        currentEndpoint.parameters.forEachIndexed { index, param ->
            val key = "param_$index"
            if (!formValues.containsKey(key)) {
                val comp = (param.component ?: "").lowercase()
                when {
                    comp.contains("checkbox") -> formValues[key] = false
                    comp.contains("slider") || comp.contains("number") -> formValues[key] = 0.0
                    else -> formValues[key] = param.example_input ?: ""
                }
            }
        }
    }

    val isRunning = activeTask?.status == TaskStatus.RUNNING
    val activeToken = apiKeys.find { it.id == selectedKeyId }?.token

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Run: ${space.displayTitle}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = space.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenApiKeys) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "API Keys",
                            tint = if (selectedKeyId != null) AmberPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = { activeTask?.id?.let { onCancelInference(it) } },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Execution", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                val payload = currentEndpoint.parameters.mapIndexed { index, _ ->
                                    formValues["param_$index"] ?: ""
                                }
                                onStartInference(currentEndpoint.name, formValues.toMap(), payload, activeToken)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Execute Inference",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
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
            // Endpoints dropdown if multiple exist
            if (endpoints.size > 1) {
                Text(
                    text = "Select Endpoint",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    endpoints.forEach { ep ->
                        FilterChip(
                            selected = selectedEndpointName == ep.name,
                            onClick = { selectedEndpointName = ep.name },
                            label = { Text(ep.name) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // API Token Selector Card
            if (apiKeys.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Outlined.Key, contentDescription = null, tint = AmberPrimary)
                            Text(
                                text = "Using HF Token:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = apiKeys.find { it.id == selectedKeyId }?.name ?: "None",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Input Parameters Form
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Input Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    currentEndpoint.parameters.forEachIndexed { index, param ->
                        val key = "param_$index"
                        val label = param.label.ifBlank { "Parameter ${index + 1}" }
                        val comp = (param.component ?: "").lowercase()

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            when {
                                comp.contains("checkbox") -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = (formValues[key] as? Boolean) ?: false,
                                            onCheckedChange = { formValues[key] = it }
                                        )
                                        Text(text = "Enable $label", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                comp.contains("slider") -> {
                                    val currentVal = (formValues[key] as? Double) ?: 0.0
                                    Column {
                                        Text(text = "Value: $currentVal", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = currentVal.toFloat(),
                                            onValueChange = { formValues[key] = it.toDouble() },
                                            valueRange = 0f..100f
                                        )
                                    }
                                }
                                else -> {
                                    OutlinedTextField(
                                        value = (formValues[key] as? String) ?: "",
                                        onValueChange = { formValues[key] = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Enter $label...") },
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = if (comp.contains("textbox") || comp.contains("prompt")) 2 else 1,
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Live Execution Status Banner
            if (activeTask != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (activeTask.status) {
                            TaskStatus.RUNNING -> AmberSleepBg.copy(alpha = 0.4f)
                            TaskStatus.COMPLETED -> EmeraldBg.copy(alpha = 0.4f)
                            TaskStatus.ERROR -> RedErrorBg.copy(alpha = 0.4f)
                        }
                    ),
                    border = CardDefaults.outlinedCardBorder(enabled = true)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (activeTask.status) {
                                                TaskStatus.RUNNING -> AmberPrimary
                                                TaskStatus.COMPLETED -> EmeraldRunning
                                                TaskStatus.ERROR -> RedError
                                            }
                                        )
                                )
                                Text(
                                    text = when (activeTask.status) {
                                        TaskStatus.RUNNING -> "Processing Model Inference..."
                                        TaskStatus.COMPLETED -> "Inference Completed!"
                                        TaskStatus.ERROR -> "Execution Error"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "${activeTask.elapsedSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (activeTask.status == TaskStatus.RUNNING) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AmberPrimary
                            )
                        }

                        if (activeTask.status == TaskStatus.ERROR) {
                            Text(
                                text = activeTask.error ?: "Unknown error occurred during inference",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RedError
                            )
                        }
                    }
                }
            }

            // Results Viewer Section
            if (activeTask?.result != null) {
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
                                text = "Model Output",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(activeTask.result.toString()))
                            }) {
                                Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = "Copy Output")
                            }
                        }

                        val resultStr = activeTask.result.toString()

                        // Check if result contains an image URL
                        if (resultStr.contains(".png") || resultStr.contains(".jpg") || resultStr.contains(".jpeg") || resultStr.contains("data:image/")) {
                            val imgUrl = Regex("(https?://[^\\s\"']+?\\.(?:png|jpg|jpeg|webp))").find(resultStr)?.value ?: resultStr

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Slate900),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = "Generated Output",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Text / JSON Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate950)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = resultStr,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = Slate300
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
