package com.hfspaces.explorer.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GradioClientTest {
    private val server = MockWebServer()
    private val client = GradioClient(OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build())
    private val host get() = server.url("/")

    @Before fun setUp() = server.start()
    // Tests that stall a response on purpose leave it open; don't fail on the shutdown wait.
    @After fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun route(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = handler(request)
        }
    }

    private fun sse(body: String) = MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body)

    @Test
    fun `predict submits then reads the event stream`() = runBlocking {
        route { req ->
            when ("${req.method} ${req.path}") {
                "POST /gradio_api/call/predict" -> MockResponse().setBody("""{"event_id":"abc"}""")
                "GET /gradio_api/call/predict/abc" -> sse("event: heartbeat\ndata: null\n\nevent: complete\ndata: [7]\n\n")
                else -> MockResponse().setResponseCode(500)
            }
        }
        val updates = mutableListOf<CallUpdate>()
        val result = client.predict(host, "/gradio_api", "/predict", JsonArray(listOf(JsonPrimitive(3))), null) { updates += it }
        assertEquals(JsonArray(listOf(JsonPrimitive(7))), result)
        assertEquals(listOf<CallUpdate>(CallUpdate.Queued), updates)
        val submit = server.takeRequest()
        assertEquals("""{"data":[3]}""", submit.body.readUtf8())
    }

    @Test
    fun `space errors surface verbatim and are not retried elsewhere`() = runBlocking {
        route { req ->
            when (req.path) {
                "/gradio_api/call/predict" -> MockResponse().setBody("""{"event_id":"e1"}""")
                "/gradio_api/call/predict/e1" -> sse("event: error\ndata: {\"error\": \"Cannot divide by zero!\"}\n\n")
                else -> MockResponse().setBody("""{"data":["fallback should never be used"]}""")
            }
        }
        try {
            client.predict(host, "/gradio_api", "predict", JsonArray(emptyList()), null)
            fail("expected an error")
        } catch (e: SpaceExecutionException) {
            assertEquals("Cannot divide by zero!", e.message)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `null error data gets a helpful message`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"e"}""") else sse("event: error\ndata: null\n\n")
        }
        val error = runCatching { client.predict(host, "", "predict", JsonArray(emptyList()), null) }.exceptionOrNull()
        assertTrue(error is SpaceExecutionException)
        assertTrue(error!!.message!!.contains("without details"))
    }

    @Test
    fun `generating events stream partial output`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"g"}""")
            else sse("event: generating\ndata: [\"He\"]\n\nevent: generating\ndata: [\"Hello\"]\n\nevent: complete\ndata: [\"Hello!\"]\n\n")
        }
        val partials = mutableListOf<String>()
        val result = client.predict(host, "/gradio_api", "chat", JsonArray(emptyList()), null) { update ->
            if (update is CallUpdate.Generating) partials += update.partial.toString()
        }
        assertEquals(listOf("[\"He\"]", "[\"Hello\"]"), partials)
        assertEquals("[\"Hello!\"]", result.toString())
    }

    @Test
    fun `a stream that closes early is an error, not a silent empty result`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"x"}""") else sse("event: heartbeat\ndata: null\n\n")
        }
        val error = runCatching { client.predict(host, "/gradio_api", "predict", JsonArray(emptyList()), null) }.exceptionOrNull()
        assertTrue(error.toString(), error is java.io.IOException && error.message!!.contains("closed the connection"))
    }

    @Test
    fun `old gradio without the queue api falls back to legacy predict only on 404`() = runBlocking {
        route { req ->
            when (req.path) {
                "/run/predict" -> MockResponse().setBody("""{"data":["legacy"],"duration":0.1}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val result = client.predict(host, "", "/predict", JsonArray(emptyList()), null)
        assertEquals("[\"legacy\"]", result.toString())
    }

    @Test
    fun `server errors on submit are reported, not masked by fallbacks`() = runBlocking {
        route { MockResponse().setResponseCode(503).setBody("""{"detail":"Space is building"}""") }
        val error = runCatching { client.predict(host, "/gradio_api", "predict", JsonArray(emptyList()), null) }.exceptionOrNull()
        assertTrue(error is HttpStatusException)
        assertTrue(error!!.message!!.contains("503"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `cancelling the coroutine aborts the open stream`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"slow"}""")
            else sse("event: heartbeat\ndata: null\n\n").setSocketPolicy(SocketPolicy.KEEP_OPEN).throttleBody(1, 1, TimeUnit.HOURS)
        }
        val job = async(kotlinx.coroutines.Dispatchers.Default) {
            client.predict(host, "/gradio_api", "predict", JsonArray(emptyList()), null)
        }
        delay(500)
        job.cancel()
        // Without call cancellation this would block until the 10s read timeout.
        withTimeout(3_000) { runCatching { job.await() } }
        assertTrue(job.isCancelled)
    }

    @Test
    fun `discover tries gradio 5 prefix then root`() = runBlocking {
        route { req ->
            when (req.path) {
                "/info" -> MockResponse().setBody("""{"named_endpoints":{"/predict":{"parameters":[]}}}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        val api = client.discover(host, null)
        assertEquals("", api.apiPrefix)
        assertEquals(setOf("/predict"), api.info.namedEndpoints.keys)
    }

    @Test
    fun `discover rejects non gradio apps`() = runBlocking {
        route { MockResponse().setBody("<html>not gradio</html>") }
        val error = runCatching { client.discover(host, null) }.exceptionOrNull()
        assertTrue(error.toString(), error!!.message!!.contains("Gradio API"))
    }

    @Test
    fun `upload sends multipart and returns server paths`() = runBlocking {
        route { MockResponse().setBody("""["/tmp/gradio/1/a.png","/tmp/gradio/2/b.png"]""") }
        val paths = client.upload(
            host,
            "/gradio_api",
            listOf(
                UploadSource("a.png", "image/png", 3) { "abc".byteInputStream() },
                UploadSource("b.png", "image/png", 3) { "def".byteInputStream() }
            ),
            token = null
        )
        assertEquals(listOf("/tmp/gradio/1/a.png", "/tmp/gradio/2/b.png"), paths)
        val request = server.takeRequest()
        assertEquals("/gradio_api/upload", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("filename=\"a.png\"") && body.contains("abc") && body.contains("def"))
    }

    @Test
    fun `tokens are never sent to hosts outside hugging face`() = runBlocking {
        route { req ->
            if (req.method == "POST") MockResponse().setBody("""{"event_id":"t"}""") else sse("event: complete\ndata: []\n\n")
        }
        client.predict(host, "/gradio_api", "predict", JsonArray(emptyList()), token = "hf_secret_token_value")
        assertNull(server.takeRequest().getHeader("Authorization"))
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `endpoint names cannot inject path segments`() = runBlocking {
        val error = runCatching { client.predict(host, "", "../../admin", JsonArray(emptyList()), null) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }
}
