package com.hfspaces.explorer.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.BuildConfig
import com.hfspaces.explorer.data.repository.AppSettings
import com.hfspaces.explorer.data.repository.BackupManager
import com.hfspaces.explorer.data.repository.ChatRepository
import com.hfspaces.explorer.data.repository.MediaRepository
import com.hfspaces.explorer.data.repository.RecentSearches
import com.hfspaces.explorer.data.repository.SettingsRepository
import com.hfspaces.explorer.data.repository.ThemeMode
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.SectionCard
import com.hfspaces.explorer.ui.common.openUrl
import com.hfspaces.explorer.ui.common.userMessage
import com.hfspaces.explorer.ui.space.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the app keeps on the device besides the cache. */
data class StorageInfo(val outputFiles: Int = 0, val outputBytes: Long = 0, val chatMessages: Int = 0)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val recentSearches: RecentSearches,
    private val cacheDir: File,
    private val media: MediaRepository,
    private val chats: ChatRepository,
    private val backups: BackupManager
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings

    private val _storage = MutableStateFlow(StorageInfo())
    val storage: StateFlow<StorageInfo> = _storage.asStateFlow()

    private val messages = Channel<String>(Channel.BUFFERED)

    /** One-off messages for a snackbar. */
    val events: Flow<String> = messages.receiveAsFlow()

    init {
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            val (files, bytes) = media.diskUsage()
            _storage.value = StorageInfo(files, bytes, chats.messageCount())
        }
    }

    fun clearChats() {
        viewModelScope.launch {
            chats.clearAll()
            refreshStorage()
            messages.trySend("Chat history deleted")
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            messages.trySend(
                try {
                    backups.exportTo(uri)
                    "Backup saved"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Couldn't save the backup: ${e.userMessage()}"
                }
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            messages.trySend(
                try {
                    val added = backups.importFrom(uri)
                    if (added.isEmpty) {
                        "Everything in that backup was already here"
                    } else {
                        "Added " + listOfNotNull(
                            added.favourites.takeIf { it > 0 }?.let { "$it favourite${if (it == 1) "" else "s"}" },
                            added.collections.takeIf { it > 0 }?.let { "$it collection${if (it == 1) "" else "s"}" },
                            added.presets.takeIf { it > 0 }?.let { "$it preset${if (it == 1) "" else "s"}" }
                        ).joinToString(", ")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Couldn't import: ${e.userMessage()}"
                }
            )
        }
    }

    fun setTheme(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setNotify(enabled: Boolean) = settings.setNotifyOnFinish(enabled)
    fun setShowAdult(enabled: Boolean) = settings.setShowAdultSpaces(enabled)
    fun clearSearchHistory() = recentSearches.clear()

    /** Deletes cached pages and staged uploads; saved outputs and favourites are untouched. */
    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val size = cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        size
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var confirmClearChats by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackup)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackup)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard("Appearance") {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = settings.themeMode == mode, role = Role.RadioButton) { viewModel.setTheme(mode) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = settings.themeMode == mode, onClick = null)
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Follow system"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp)
                        )
                    }
                }
                val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SwitchSetting(
                    title = "Dynamic colour",
                    subtitle = if (dynamicAvailable) "Use colours from your wallpaper" else "Needs Android 12 or newer",
                    checked = settings.dynamicColor && dynamicAvailable,
                    enabled = dynamicAvailable,
                    onChange = viewModel::setDynamicColor
                )
            }
            SectionCard("Notifications") {
                SwitchSetting(
                    title = "Notify when a run finishes",
                    subtitle = "Only while you're outside the app",
                    checked = settings.notifyOnFinish,
                    onChange = viewModel::setNotify
                )
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }) { Text("System notification settings") }
            }
            SectionCard("Content") {
                SwitchSetting(
                    title = "Show adult Spaces",
                    subtitle = "Spaces Hugging Face tags as not for all audiences. Favourites and your own Spaces are always shown.",
                    checked = settings.showAdultSpaces,
                    onChange = viewModel::setShowAdult
                )
            }
            SectionCard("Links") {
                Text(
                    "Links to Space pages (huggingface.co/spaces/…) can open here instead of the browser. " +
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Turn the links on under “Open by default”." else "Android asks which app to use the first time.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Either way, you can share a Space's page to this app, or paste its link into search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    val packageUri = Uri.fromParts("package", context.packageName, null)
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    }
                    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Link settings") }
            }
            SectionCard("Backup") {
                Text(
                    "Android backups are off for this app, because tokens are tied to this device. Save your favourites, " +
                        "collections, presets and settings to a file to move them to another phone.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Tokens, runs, chats and saved outputs aren't included. Importing only adds; nothing here is replaced.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { runCatching { exportBackup.launch(BackupManager.suggestedFileName()) } }) { Text("Export") }
                    // Some file managers don't label JSON files, so any file can be picked; the content is checked.
                    OutlinedButton(onClick = { runCatching { importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) } }) { Text("Import") }
                }
            }
            SectionCard("Storage") {
                Text(
                    "Saved outputs: ${storage.outputFiles} file${if (storage.outputFiles == 1) "" else "s"}, ${formatBytes(storage.outputBytes)}. " +
                        "Delete them one by one or all at once under Saved outputs.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Chat history: ${storage.chatMessages} message${if (storage.chatMessages == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (storage.chatMessages > 0) TextButton(onClick = { confirmClearChats = true }) { Text("Delete chats") }
                }
                Text("Cache: lists and files staged for upload. Clearing it doesn't touch saved outputs or favourites.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val freed = viewModel.clearCache()
                            snackbar.showSnackbar("Cleared ${"%.1f".format(freed / 1_048_576.0)} MB")
                        }
                    }) { Text("Clear cache") }
                    OutlinedButton(onClick = {
                        viewModel.clearSearchHistory()
                        scope.launch { snackbar.showSnackbar("Search history cleared") }
                    }) { Text("Clear search history") }
                }
            }
            SectionCard("About") {
                Text("Spaces Explorer for Hugging Face ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Unofficial app, not affiliated with or endorsed by Hugging Face. Open source under the Apache License 2.0.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = {
                    context.openUrl(SOURCE_URL)
                }) { Text("Source code") }
            }
        }
    }

    if (confirmClearChats) {
        ConfirmDialog(
            title = "Delete all chats?",
            body = "This removes every saved conversation from this device.",
            confirm = "Delete",
            onConfirm = {
                viewModel.clearChats()
                confirmClearChats = false
            },
            onDismiss = { confirmClearChats = false }
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private const val SOURCE_URL = "https://github.com/AxD99999/spaces-explorer"

@Composable
private fun SwitchSetting(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
