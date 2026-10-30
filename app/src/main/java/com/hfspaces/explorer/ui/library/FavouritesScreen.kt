package com.hfspaces.explorer.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.CollectionsRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.SpaceCollection
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.ui.LocalSnackbarHostState
import com.hfspaces.explorer.ui.common.MessageState
import com.hfspaces.explorer.ui.common.SpaceCard
import com.hfspaces.explorer.ui.common.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class FavouriteSort(val label: String) {
    RECENT("Recently saved"),
    NAME("Name"),
    LIKES("Most likes")
}

data class FavouritesUiState(
    val all: List<HfSpace> = emptyList(),
    val shown: List<HfSpace> = emptyList(),
    val collections: List<SpaceCollection> = emptyList(),
    val members: Map<String, Set<String>> = emptyMap(),
    val selected: String? = null,
    val sort: FavouriteSort = FavouriteSort.RECENT
) {
    fun collectionsOf(spaceId: String): Set<String> = members.filterValues { spaceId in it }.keys
}

/** Sorts and filters favourites; pure so it can be tested. */
fun arrangeFavourites(all: List<HfSpace>, members: Map<String, Set<String>>, selected: String?, sort: FavouriteSort): List<HfSpace> {
    val filtered = if (selected == null) all else all.filter { it.id in members[selected].orEmpty() }
    return when (sort) {
        FavouriteSort.RECENT -> filtered
        FavouriteSort.NAME -> filtered.sortedBy { it.displayTitle.lowercase() }
        FavouriteSort.LIKES -> filtered.sortedByDescending { it.likes }
    }
}

class FavouritesLibraryViewModel(
    private val favorites: FavoritesRepository,
    private val collections: CollectionsRepository,
    private val spaces: SpacesRepository,
    private val hub: HfHubClient,
    private val apiKeys: ApiKeysRepository
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(FavouriteSort.RECENT)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private var refreshed = false

    val state: StateFlow<FavouritesUiState> = combine(
        favorites.observe(),
        collections.observe(),
        collections.observeMembers(),
        selected,
        sort
    ) { all, cols, members, sel, order ->
        val validSel = sel?.takeIf { id -> cols.any { it.id == id } }
        FavouritesUiState(all, arrangeFavourites(all, members, validSel, order), cols, members, validSel, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouritesUiState())

    fun select(collectionId: String?) {
        selected.value = collectionId
    }

    fun setSort(order: FavouriteSort) {
        sort.value = order
    }

    fun toggleFavourite(space: HfSpace) {
        viewModelScope.launch { favorites.toggle(space) }
    }

    fun createCollection(name: String, withSpace: String? = null) {
        viewModelScope.launch {
            val id = collections.create(name)
            withSpace?.let { collections.setMember(id, it, true) }
        }
    }

    fun renameCollection(id: String, name: String) {
        viewModelScope.launch { collections.rename(id, name) }
    }

    fun deleteCollection(id: String) {
        viewModelScope.launch {
            collections.delete(id)
            if (selected.value == id) selected.value = null
        }
    }

    fun setMember(collectionId: String, spaceId: String, member: Boolean) {
        viewModelScope.launch { collections.setMember(collectionId, spaceId, member) }
    }

    /** Pulls current status for saved Spaces once per visit (or on pull-to-refresh). */
    fun refresh(force: Boolean = false) {
        if ((refreshed && !force) || _refreshing.value) return
        refreshed = true
        viewModelScope.launch {
            _refreshing.value = true
            val limit = Semaphore(4)
            favorites.getAll().map { space ->
                async { limit.withPermit { runCatching { favorites.refreshIfFavorite(spaces.fetchSpace(space.id, null)) } } }
            }.awaitAll()
            _refreshing.value = false
        }
    }

    /** The default token's username, to prefill the import dialog. */
    suspend fun suggestedUsername(): String? {
        val token = apiKeys.observe().first().let { keys -> (keys.firstOrNull { it.isDefault } ?: keys.firstOrNull())?.token }
        return token?.takeIf { it.isNotBlank() }?.let { runCatching { hub.whoAmI(it)?.name }.getOrNull() }
    }

    /** Adds every Space [username] has liked on the Hub to favourites. */
    fun importLikes(username: String) {
        val user = username.trim().removePrefix("@")
        if (user.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            try {
                val ids = hub.likedSpaceIds(user)
                val limit = Semaphore(4)
                val added = ids.map { id ->
                    async {
                        limit.withPermit {
                            runCatching { favorites.addIfMissing(spaces.knownSpace(id) ?: spaces.fetchSpace(id, null)) }.getOrDefault(false)
                        }
                    }
                }.awaitAll().count { it }
                messages.trySend(
                    when {
                        ids.isEmpty() -> "$user hasn't liked any Spaces"
                        added == 0 -> "All ${ids.size} liked Spaces were already favourites"
                        else -> "Added $added of ${ids.size} liked Spaces"
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.trySend(e.userMessage())
            } finally {
                _importing.value = false
            }
        }
    }
}

@Composable
fun FavouritesScreen(
    viewModel: FavouritesLibraryViewModel,
    onBack: () -> Unit,
    onOpenSpace: (HfSpace) -> Unit,
    onRunSpace: (HfSpace) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    var overflowMenu by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    var newCollection by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var editingSpace by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Favourites") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (importing) CircularProgressIndicator(Modifier.padding(12.dp).heightIn(max = 20.dp), strokeWidth = 2.dp)
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            FavouriteSort.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = { RadioButton(selected = state.sort == order, onClick = null) },
                                    onClick = {
                                        viewModel.setSort(order)
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = overflowMenu, onDismissRequest = { overflowMenu = false }) {
                            DropdownMenuItem(text = { Text("Import liked Spaces…") }, onClick = {
                                overflowMenu = false
                                showImport = true
                            })
                            DropdownMenuItem(text = { Text("New collection…") }, onClick = {
                                overflowMenu = false
                                newCollection = true
                            })
                            if (state.selected != null) {
                                DropdownMenuItem(text = { Text("Rename collection…") }, onClick = {
                                    overflowMenu = false
                                    renaming = true
                                })
                                DropdownMenuItem(text = { Text("Delete collection") }, onClick = {
                                    overflowMenu = false
                                    state.selected?.let(viewModel::deleteCollection)
                                })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.all.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = state.selected == null, onClick = { viewModel.select(null) }, label = { Text("All (${state.all.size})") })
                    state.collections.forEach { col ->
                        FilterChip(
                            selected = state.selected == col.id,
                            onClick = { viewModel.select(col.id) },
                            label = { Text("${col.name} (${col.count})") },
                            leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) }
                        )
                    }
                    AssistChip(onClick = { newCollection = true }, label = { Text("New") }, leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) })
                }
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh(force = true) }, modifier = Modifier.fillMaxSize()) {
                when {
                    state.all.isEmpty() -> MessageState(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = "No favourites yet",
                        message = "Tap the heart on any Space, or import the Spaces you've liked on Hugging Face.",
                        actionLabel = "Import liked Spaces",
                        onAction = { showImport = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.shown.isEmpty() -> MessageState(
                        icon = Icons.Outlined.Folder,
                        title = "This collection is empty",
                        message = "Use the folder button on a favourite to add it here.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.shown, key = { it.id }) { space ->
                            SpaceCard(
                                space = space,
                                isFavorite = true,
                                onOpen = { onOpenSpace(space) },
                                onRun = { onRunSpace(space) },
                                onToggleFavorite = { viewModel.toggleFavourite(space) },
                                menu = {
                                    IconButton(onClick = { editingSpace = space.id }) {
                                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Add ${space.displayTitle} to a collection")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showImport) {
        var username by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(Unit) { if (username.isEmpty()) viewModel.suggestedUsername()?.let { username = it } }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import liked Spaces") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Likes on Hugging Face are public, so any username works.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importLikes(username)
                    showImport = false
                }, enabled = username.isNotBlank()) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } }
        )
    }
    if (newCollection || renaming) {
        val current = state.collections.firstOrNull { it.id == state.selected }
        NameDialog(
            title = if (renaming) "Rename collection" else "New collection",
            initial = if (renaming) current?.name.orEmpty() else "",
            onDismiss = {
                newCollection = false
                renaming = false
            },
            onConfirm = { name ->
                if (renaming && current != null) viewModel.renameCollection(current.id, name) else viewModel.createCollection(name)
                newCollection = false
                renaming = false
            }
        )
    }
    editingSpace?.let { spaceId ->
        CollectionPickerDialog(
            collections = state.collections,
            memberOf = state.collectionsOf(spaceId),
            onToggle = { colId, member -> viewModel.setMember(colId, spaceId, member) },
            onCreate = { name -> viewModel.createCollection(name, withSpace = spaceId) },
            onDismiss = { editingSpace = null }
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CollectionPickerDialog(
    collections: List<SpaceCollection>,
    memberOf: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collections") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(collections, key = { it.id }) { col ->
                        val checked = col.id in memberOf
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox) { onToggle(col.id, it) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(col.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    label = { Text("New collection") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            onCreate(newName)
                            newName = ""
                        }, enabled = newName.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Create collection") }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
internal fun CenteredBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
