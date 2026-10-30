package com.hfspaces.explorer.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hfspaces.explorer.BuildConfig
import com.hfspaces.explorer.data.repository.AppSettings
import com.hfspaces.explorer.data.repository.RecentSearches
import com.hfspaces.explorer.data.repository.SettingsRepository
import com.hfspaces.explorer.data.repository.ThemeMode
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val recentSearches: RecentSearches,
    private val cacheDir: File
) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings

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
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

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
            SectionCard("Storage") {
                Text("Cached lists and files staged for upload. Saved outputs and favourites aren't affected.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
                }) { Text("Source code") }
            }
        }
    }
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
