package com.hfspaces.explorer.ui.screens.apikeys

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hfspaces.explorer.data.model.UserApiKey
import com.hfspaces.explorer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    apiKeys: List<UserApiKey>,
    onBack: () -> Unit,
    onAddKey: (name: String, token: String) -> Unit,
    onDeleteKey: (String) -> Unit,
    onSetDefaultKey: (String) -> Unit,
    onValidateKey: (String, (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var keyNameInput by remember { mutableStateOf("") }
    var keyTokenInput by remember { mutableStateOf("") }
    var validationResult by remember { mutableStateOf<String?>(null) }
    var isValidating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hugging Face API Tokens",
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
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Token")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Token")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Explanatory Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Why add a Hugging Face Token?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "A User Access Token grants higher rate limits, unlocks private Spaces, and allows running gated models without queuing delays.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/settings/tokens"))
                        context.startActivity(intent)
                    }) {
                        Text("Get token from huggingface.co/settings/tokens →")
                    }
                }
            }

            if (apiKeys.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "No API tokens added yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Add Your First Token")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = apiKeys, key = { it.id }) { key ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = CardDefaults.outlinedCardBorder(enabled = true)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = key.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (key.isDefault) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = EmeraldBg
                                            ) {
                                                Text(
                                                    text = "DEFAULT",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldRunning,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = "hf_••••••••" + key.token.takeLast(4),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!key.isDefault) {
                                        TextButton(onClick = { onSetDefaultKey(key.id) }) {
                                            Text("Set Default")
                                        }
                                    }
                                    IconButton(onClick = { onDeleteKey(key.id) }) {
                                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", tint = RedError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Key Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Hugging Face Token", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = keyNameInput,
                        onValueChange = { keyNameInput = it },
                        label = { Text("Token Label (e.g. My HF Token)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = keyTokenInput,
                        onValueChange = { keyTokenInput = it },
                        label = { Text("Token Value (hf_...)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    if (isValidating) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Verifying token with HF Hub...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (validationResult != null) {
                        Text(
                            text = validationResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (validationResult!!.startsWith("Valid")) EmeraldRunning else RedError
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyTokenInput.isNotBlank()) {
                            isValidating = true
                            onValidateKey(keyTokenInput) { valid, userName ->
                                isValidating = false
                                if (valid) {
                                    onAddKey(keyNameInput.ifBlank { userName ?: "HF Token" }, keyTokenInput)
                                    showAddDialog = false
                                    keyNameInput = ""
                                    keyTokenInput = ""
                                    validationResult = null
                                } else {
                                    validationResult = "Invalid token or network error. Please check your token."
                                }
                            }
                        }
                    },
                    enabled = keyTokenInput.isNotBlank() && !isValidating
                ) {
                    Text("Save & Validate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
