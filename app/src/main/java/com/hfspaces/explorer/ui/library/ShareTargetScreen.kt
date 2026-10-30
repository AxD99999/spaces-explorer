package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.hidingAdult
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.SettingsRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.data.repository.TaskManager
import com.hfspaces.explorer.system.DeepLinks
import com.hfspaces.explorer.system.SharedContent
import com.hfspaces.explorer.ui.common.EmojiAvatar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpacePickerState(
    val query: String = "",
    val recent: List<HfSpace> = emptyList(),
    val favourites: List<HfSpace> = emptyList(),
    val results: List<HfSpace> = emptyList(),
    val searching: Boolean = false
)

/** Lets the user choose a Space by recent use, favourite or search. Used for sharing and comparing. */
class SpacePickerViewModel(
    private val spaces: SpacesRepository,
    favorites: FavoritesRepository,
    tasks: TaskManager,
    private val deepLinks: DeepLinks,
    private val settings: SettingsRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<HfSpace>>(emptyList())
    private val searching = MutableStateFlow(false)
    private var searchJob: Job? = null

    val shared: StateFlow<SharedContent?> = deepLinks.shared
    val sharing: StateFlow<Boolean> = deepLinks.sharing

    val state: StateFlow<SpacePickerState> = combine(
        query,
        tasks.tasks.map { list -> list.map { it.space }.distinctBy { it.id }.take(6) },
        favorites.observe(),
        results,
        searching
    ) { q, recent, favs, found, busy ->
        SpacePickerState(q, recent.filter { it.isRunnable || it.sdk == null }, favs.filter { it.isRunnable || it.sdk == null }, found, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpacePickerState())

    fun onQueryChange(text: String) {
        query.value = text
        searchJob?.cancel()
        if (text.isBlank()) {
            results.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            searching.value = true
            results.value = runCatching { spaces.fetchPage(0, text, sdk = "gradio").spaces.filter { it.isRunnable }.hidingAdult(settings.settings.value.showAdultSpaces) }.getOrDefault(emptyList())
            searching.value = false
        }
    }

    fun cancelShare() = deepLinks.clearShared()
}

@Composable
fun ShareTargetScreen(viewModel: SpacePickerViewModel, onBack: () -> Unit, onPick: (String) -> Unit) {
    val shared by viewModel.shared.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send to a Space") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelShare()
                        onBack()
                    }) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SharedPreview(shared, sharing)
            SpacePicker(viewModel, onPick = onPick)
        }
    }
}

@Composable
private fun SharedPreview(shared: SharedContent?, sharing: Boolean) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                sharing -> {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Preparing what you shared…")
                }
                shared == null || shared.isEmpty -> Text("Nothing to send. Pick a Space to open it.")
                else -> {
                    shared.files.forEach { file ->
                        if (file.mimeType?.startsWith("image") == true) {
                            AsyncImage(
                                model = file.uri,
                                contentDescription = file.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(Icons.Outlined.AttachFile, contentDescription = file.name)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    shared.text?.let { Text("“${it.take(80)}”", maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Search box plus recent, favourite and search-result sections; [onPick] gets a Space id. */
@Composable
fun SpacePicker(viewModel: SpacePickerViewModel, onPick: (String) -> Unit, exclude: Set<String> = emptySet()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Search Gradio Spaces") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { if (state.searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            fun section(title: String, list: List<HfSpace>) {
                val shown = list.filterNot { it.id in exclude }
                if (shown.isEmpty()) return
                item("h-$title") {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                items(shown, key = { "$title-${it.id}" }) { space ->
                    ListItem(
                        leadingContent = { EmojiAvatar(space.displayEmoji, size = 36) },
                        headlineContent = { Text(space.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(space.id, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.clickable { onPick(space.id) }
                    )
                }
            }
            if (state.query.isNotBlank()) {
                section("Results", state.results)
                if (!state.searching && state.results.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("No Gradio Spaces match") } }
                }
            } else {
                section("Recently run", state.recent)
                section("Favourites", state.favourites)
                if (state.recent.isEmpty() && state.favourites.isEmpty()) {
                    item { Text("Search for a Space above.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
