package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.data.model.UserApiKey
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.Pill
import com.hfspaces.explorer.ui.common.openUrl
import com.hfspaces.explorer.ui.theme.LocalStatusColors

@Composable
fun ApiKeysScreen(viewModel: ApiKeysViewModel, onBack: () -> Unit) {
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val check by viewModel.check.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = { Text("Access tokens") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add token") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Why add a token?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Tokens let you run private and gated Spaces and use your own ZeroGPU quota. They're encrypted with " +
                                "a key in this device's Keystore and only sent to huggingface.co and *.hf.space. A read-only token is enough.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { context.openUrl("https://huggingface.co/settings/tokens") }, contentPadding = PaddingValues(0.dp)) {
                            Text("Create a token on Hugging Face")
                        }
                    }
                }
            }
            if (keys.isEmpty()) {
                item {
                    Text(
                        "No tokens yet. Public Spaces work without one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            items(keys, key = { it.id }) { key ->
                KeyCard(key, onSetDefault = { viewModel.setDefault(key.id) }, onDelete = { pendingDelete = key.id })
            }
        }
    }

    if (showAdd) {
        AddKeyDialog(
            check = check,
            onValidate = viewModel::validate,
            onTokenEdited = viewModel::resetCheck,
            onDismiss = {
                showAdd = false
                viewModel.resetCheck()
            },
            onSave = { name, token ->
                viewModel.add(name, token)
                showAdd = false
            }
        )
    }

    keys.firstOrNull { it.id == pendingDelete }?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove “${key.name}”?") },
            text = { Text("Runs that rely on this token will stop working. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(key.id)
                    pendingDelete = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun KeyCard(key: UserApiKey, onSetDefault: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalStatusColors.current
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Key, contentDescription = null, tint = if (key.isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(key.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (key.isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Pill("Default", colors.running)
                    }
                }
                if (key.token.isBlank()) {
                    Text("Can't be decrypted on this device. Remove it and add it again.", style = MaterialTheme.typography.bodySmall, color = colors.error)
                } else {
                    Text(key.maskedToken, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Added ${key.createdAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                if (!key.isDefault && key.token.isNotBlank()) {
                    TextButton(onClick = onSetDefault, contentPadding = PaddingValues(0.dp)) { Text("Make default") }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Remove ${key.name}") }
        }
    }
}

@Composable
private fun AddKeyDialog(
    check: TokenCheck,
    onValidate: (String) -> Unit,
    onTokenEdited: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var reveal by rememberSaveable { mutableStateOf(false) }
    val colors = LocalStatusColors.current
    val looksValid = token.trim().startsWith("hf_") && token.trim().length >= 20

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add access token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        onTokenEdited()
                    },
                    label = { Text("Token") },
                    placeholder = { Text("hf_…") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (reveal) "Hide token" else "Show token")
                        }
                    },
                    supportingText = {
                        when {
                            token.isNotBlank() && !looksValid -> Text("Hugging Face tokens start with hf_")
                            check is TokenCheck.Valid -> Text("Valid · ${check.account}", color = colors.running)
                            check is TokenCheck.Invalid -> Text(check.message, color = colors.error)
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onValidate(token) }, enabled = looksValid && check !is TokenCheck.Checking) { Text("Check token") }
                    if (check is TokenCheck.Checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, token) }, enabled = looksValid && check !is TokenCheck.Invalid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
