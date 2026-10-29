package com.hfspaces.explorer

import android.content.Context
import android.net.Uri
import com.hfspaces.explorer.data.local.AppDatabase
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.data.security.TokenCipher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.remote.defaultOkHttpClient
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.ContentOpener
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.data.repository.FormDraftStore
import com.hfspaces.explorer.data.repository.MediaRepository
import com.hfspaces.explorer.data.repository.SpacesRepository
import com.hfspaces.explorer.data.repository.TaskManager
import com.hfspaces.explorer.data.repository.UploadStaging
import com.hfspaces.explorer.data.security.KeystoreTokenCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.io.FileNotFoundException

/**
 * Manual dependency graph, created once per process by [HfSpacesApplication].
 * The optional parameters are seams for tests.
 */
class AppContainer(
    context: Context,
    hubBaseUrl: HttpUrl = "https://huggingface.co".toHttpUrl(),
    hostResolver: (HfSpace) -> HttpUrl? = SpaceIds::resolveHost,
    private val tokenCipher: TokenCipher = KeystoreTokenCipher(),
    databaseName: String? = AppDatabase.NAME
) {
    private val appContext = context.applicationContext

    /** Outlives screens, so Space calls keep running while the user navigates. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient = defaultOkHttpClient()
    private val database = AppDatabase.build(appContext, tokenCipher, databaseName)
    private val gradioClient = GradioClient(httpClient)
    val hubClient = HfHubClient(httpClient, hubBaseUrl)

    val spacesRepository = SpacesRepository(
        hub = hubClient,
        gradio = gradioClient,
        cacheDir = appContext.cacheDir,
        hostResolver = hostResolver
    )
    val favoritesRepository = FavoritesRepository(database.favoritesDao())
    val apiKeysRepository = ApiKeysRepository(database.apiKeysDao(), tokenCipher)
    val mediaRepository = MediaRepository(
        dao = database.generatedMediaDao(),
        client = httpClient,
        mediaDir = File(appContext.filesDir, "media")
    )
    val formDrafts = FormDraftStore()
    val uploadStaging = UploadStaging(appContext.contentResolver, File(appContext.cacheDir, "uploads"))

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
}
