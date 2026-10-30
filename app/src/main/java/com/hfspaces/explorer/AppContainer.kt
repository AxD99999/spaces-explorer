package com.hfspaces.explorer

import android.content.Context
import android.net.Uri
import com.hfspaces.explorer.data.gradio.ChatMessage
import com.hfspaces.explorer.data.local.AppDatabase
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.data.remote.defaultOkHttpClient
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.CollectionsRepository
import com.hfspaces.explorer.data.repository.ContentOpener
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.FormDraftStore
import com.hfspaces.explorer.data.repository.MediaExporter
import com.hfspaces.explorer.data.repository.MediaRepository
import com.hfspaces.explorer.data.repository.PresetsRepository
import com.hfspaces.explorer.data.repository.RecentSearches
import com.hfspaces.explorer.data.repository.SettingsRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.data.repository.TaskManager
import com.hfspaces.explorer.data.repository.UploadStaging
import com.hfspaces.explorer.data.security.KeystoreTokenCipher
import com.hfspaces.explorer.data.security.TokenCipher
import com.hfspaces.explorer.system.AppVisibility
import com.hfspaces.explorer.system.DeepLinks
import com.hfspaces.explorer.system.RunNotifications
import com.hfspaces.explorer.system.RunNotifier
import com.hfspaces.explorer.system.ShortcutsUpdater
import com.hfspaces.explorer.system.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Manual dependency graph, created once per process by [HfSpacesApplication].
 * The optional parameters are seams for tests.
 */
class AppContainer(
    context: Context,
    hubBaseUrl: HttpUrl = "https://huggingface.co".toHttpUrl(),
    hostResolver: (HfSpace) -> HttpUrl? = SpaceIds::resolveHost,
    private val tokenCipher: TokenCipher = KeystoreTokenCipher(),
    databaseName: String? = AppDatabase.NAME,
    val visibility: AppVisibility = AppVisibility()
) {
    private val appContext = context.applicationContext

    /** Outlives screens, so Space calls keep running while the user navigates. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient = defaultOkHttpClient()
    val cacheDir: File = appContext.cacheDir
    private val database = AppDatabase.build(appContext, tokenCipher, databaseName)
    private val prefs = appContext.getSharedPreferences("hf_spaces_prefs", Context.MODE_PRIVATE)
    val gradioClient = GradioClient(httpClient)
    val hubClient = HfHubClient(httpClient, hubBaseUrl)

    val spacesRepository = SpacesRepository(
        hub = hubClient,
        gradio = gradioClient,
        cacheDir = appContext.cacheDir,
        hostResolver = hostResolver
    )
    val favoritesRepository = FavoritesRepository(database.favoritesDao(), database.collectionDao())
    val collectionsRepository = CollectionsRepository(database.collectionDao())
    val presetsRepository = PresetsRepository(database.presetDao())
    val apiKeysRepository = ApiKeysRepository(database.apiKeysDao(), tokenCipher)
    val settingsRepository = SettingsRepository(prefs)
    val recentSearches = RecentSearches(prefs)
    val mediaRepository = MediaRepository(
        dao = database.generatedMediaDao(),
        client = httpClient,
        mediaDir = File(appContext.filesDir, "media")
    )
    val mediaExporter = MediaExporter(appContext, httpClient)
    val formDrafts = FormDraftStore()
    val uploadStaging = UploadStaging(appContext.contentResolver, File(appContext.cacheDir, "uploads"))
    val deepLinks = DeepLinks(applicationScope, uploadStaging)

    /** Chat transcripts per Space, kept for the life of the process. */
    val chatTranscripts = ConcurrentHashMap<String, List<ChatMessage>>()

    val taskManager = TaskManager(
        scope = applicationScope,
        gradio = gradioClient,
        mediaRepository = mediaRepository,
        taskDao = database.taskDao(),
        contentOpener = ContentOpener { uri ->
            appContext.contentResolver.openInputStream(Uri.parse(uri))
                ?: throw FileNotFoundException("Couldn't open $uri")
        }
    )

    val notifications = RunNotifications(appContext)
    val widgetUpdater = WidgetUpdater(appContext, mediaRepository)
    private val runNotifier = RunNotifier(appContext, applicationScope, taskManager, mediaRepository, notifications, visibility) {
        settingsRepository.settings.value.notifyOnFinish
    }
    private val shortcuts = ShortcutsUpdater(appContext, applicationScope, favoritesRepository, taskManager)

    /** Starts the background pieces: run service hookup, notifications, shortcuts and widget. */
    @OptIn(FlowPreview::class)
    fun startSystemIntegrations() {
        runNotifier.start()
        shortcuts.start()
        applicationScope.launch {
            mediaRepository.observe().drop(1).debounce(500).collect { runCatching { widgetUpdater.update() } }
        }
    }
}
