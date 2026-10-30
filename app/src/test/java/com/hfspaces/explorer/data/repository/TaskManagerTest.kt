package com.hfspaces.explorer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.PickedFile
import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.local.AppDatabase
import com.hfspaces.explorer.data.local.entity.TaskEntity
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.TaskStatus
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.testing.FakeTokenCipher
import com.hfspaces.explorer.testing.Fixtures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<String>()
    private val http = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
    private lateinit var database: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var mediaDir: File
    private val space = HfSpace(id = "gradio/hello_world", title = "Hello")

    @Before
    fun setUp() {
        server.start()
        database = AppDatabase.build(context, FakeTokenCipher(), name = null)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        mediaDir = File(context.filesDir, "media-test").apply { deleteRecursively() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        // The cancellation test stalls a response on purpose; don't fail on the shutdown wait.
        runCatching { server.shutdown() }
    }

    private fun manager() = TaskManager(
        scope = scope,
        gradio = GradioClient(http),
        mediaRepository = MediaRepository(database.generatedMediaDao(), http, mediaDir),
        taskDao = database.taskDao(),
        contentOpener = ContentOpener { "PNGDATA".byteInputStream() }
    )

    private fun route(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // No /config: these tests exercise the /call route.
                if (request.path == "/config") return MockResponse().setResponseCode(404)
                requests += "${request.method} ${request.path} ${request.body.readUtf8()}"
                return handler(request)
            }
        }
    }

    private fun spec(info: String) = SpaceApiSpec(space.id, server.url("/"), "/gradio_api", Fixtures.endpoints(info))

    @Test
    fun `a run completes and its output is saved`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"e"}""")
            else MockResponse().setBody("event: complete\ndata: [\"Hello Ada!!\"]\n\n")
        }
        val tasks = manager()
        val spec = spec("info_hello-world.json")
        val id = tasks.start(space, spec, spec.endpoints.single(), mapOf(0 to FormValue.Text("Ada")), token = null)

        val done = withTimeout(10_000) { tasks.tasks.first { list -> list.any { it.id == id && !it.isActive } } }.first { it.id == id }
        assertEquals(TaskStatus.COMPLETED, done.status)
        assertEquals(1, done.savedMediaIds.size)

        val media = database.generatedMediaDao().getAll().single()
        assertEquals(GeneratedMediaType.TEXT.name, media.type)
        assertEquals("Hello Ada!!", media.textContent)
        assertEquals("Ada", media.prompt)
        assertEquals(TaskStatus.COMPLETED.name, database.taskDao().getAll().single().status)
        assertTrue(requests.any { it.startsWith("POST /gradio_api/call/predict {\"data\":[\"Ada\"]}") })
    }

    @Test
    fun `files are uploaded before the call and sent as file data`() = runBlocking {
        route { req ->
            when {
                req.path == "/gradio_api/upload" -> MockResponse().setBody("""["/tmp/gradio/1/cat.png"]""")
                req.method == "POST" -> MockResponse().setBody("""{"event_id":"e"}""")
                else -> MockResponse().setBody("event: complete\ndata: [{\"path\": \"/tmp/out.webp\", \"meta\": {\"_type\": \"gradio.FileData\"}}]\n\n")
            }
        }
        val tasks = manager()
        val spec = spec("info_image-mod.json")
        val picked = PickedFile("file:///cache/cat.png", "cat.png", "image/png", 7)
        val id = tasks.start(space, spec, spec.endpoints.first(), mapOf(0 to FormValue.Files(listOf(picked))), null)

        val done = withTimeout(10_000) { tasks.tasks.first { list -> list.any { it.id == id && !it.isActive } } }.first { it.id == id }
        assertEquals(done.error, TaskStatus.COMPLETED, done.status)
        assertTrue(requests[0], requests[0].startsWith("POST /gradio_api/upload") && requests[0].contains("PNGDATA"))
        assertTrue(requests[1], requests[1].contains("\"path\":\"/tmp/gradio/1/cat.png\"") && !requests[1].contains("base64"))
        val media = database.generatedMediaDao().getAll().single()
        assertEquals(GeneratedMediaType.IMAGE.name, media.type)
        assertEquals(server.url("/gradio_api/file=/tmp/out.webp").toString(), media.url)
    }

    @Test
    fun `stop cancels the call and records it`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"slow"}""")
            else MockResponse().setBody("event: heartbeat\ndata: null\n\n").setSocketPolicy(SocketPolicy.KEEP_OPEN).throttleBody(1, 1, TimeUnit.HOURS)
        }
        val tasks = manager()
        val spec = spec("info_hello-world.json")
        val id = tasks.start(space, spec, spec.endpoints.single(), emptyMap(), null)
        withTimeout(5_000) { tasks.tasks.first { list -> list.any { it.id == id && it.progressMessage != null } } }

        tasks.cancel(id)
        val done = withTimeout(5_000) { tasks.tasks.first { list -> list.any { it.id == id && !it.isActive } } }.first { it.id == id }
        assertEquals(TaskStatus.CANCELLED, done.status)
        withTimeout(5_000) {
            while (database.taskDao().getAll().singleOrNull()?.status != TaskStatus.CANCELLED.name) kotlinx.coroutines.delay(50)
        }
    }

    @Test
    fun `space errors are shown as task errors`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"e"}""")
            else MockResponse().setBody("event: error\ndata: {\"error\": \"GPU quota exceeded\"}\n\n")
        }
        val tasks = manager()
        val spec = spec("info_hello-world.json")
        val id = tasks.start(space, spec, spec.endpoints.single(), mapOf(0 to FormValue.Text("x")), null)
        val done = withTimeout(10_000) { tasks.tasks.first { list -> list.any { it.id == id && !it.isActive } } }.first { it.id == id }
        assertEquals(TaskStatus.ERROR, done.status)
        assertEquals("GPU quota exceeded", done.error)
        assertTrue(database.generatedMediaDao().getAll().isEmpty())
    }

    @Test
    fun `runs interrupted by process death are marked as such on start`() = runBlocking {
        database.taskDao().upsert(
            TaskEntity("old", space.id, """{"id":"gradio/hello_world"}""", "/predict", "RUNNING", 1L, null, null, null)
        )
        val tasks = manager()
        val restored = withTimeout(5_000) { tasks.tasks.first { it.isNotEmpty() } }.single()
        assertEquals(TaskStatus.ERROR, restored.status)
        assertTrue(restored.error!!.startsWith("Interrupted"))
    }

    @Test
    fun `run inputs are stored so a run can be repeated after a restart`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"e"}""")
            else MockResponse().setBody("event: complete\ndata: [\"ok\"]\n\n")
        }
        val spec = spec("info_hello-world.json")
        val first = manager()
        val id = first.start(space, spec, spec.endpoints.single(), mapOf(0 to FormValue.Text("Grace")), null)
        withTimeout(10_000) { first.tasks.first { list -> list.any { it.id == id && !it.isActive } } }
        withTimeout(5_000) { while (database.taskDao().get(id)?.status != TaskStatus.COMPLETED.name) kotlinx.coroutines.delay(50) }

        // A fresh manager (as after process death) loads the history with its inputs.
        val second = manager()
        val restored = withTimeout(5_000) { second.tasks.first { list -> list.any { it.id == id } } }.first { it.id == id }
        assertEquals(mapOf(0 to FormValue.Text("Grace")), restored.inputs)
        assertEquals(restored, second.find(id))
    }
}
