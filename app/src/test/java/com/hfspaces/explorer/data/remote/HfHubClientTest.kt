package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.data.model.SpaceStage
import com.hfspaces.explorer.testing.Fixtures
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HfHubClientTest {
    private val server = MockWebServer()
    private lateinit var hub: HfHubClient

    @Before
    fun setUp() {
        server.start()
        hub = HfHubClient(OkHttpClient(), server.url("/"))
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `parses a page and drops invalid ids`() = runBlocking {
        server.enqueue(MockResponse().setBody(Fixtures.read("spaces_page.json")))
        val page = hub.fetchSpacesPage(0, search = "  hello ")
        assertEquals(listOf("gradio/hello_world", "example/renamed-space", "example/static-site"), page.spaces.map { it.id })
        assertFalse("4 results < 24 per page", page.hasMore)

        val hello = page.spaces[0]
        assertEquals(SpaceStage.RUNNING, hello.stage)
        assertEquals("https://huggingface.co/avatars/gradio.svg", hello.avatarUrl)
        assertEquals("Text Generation", hello.category)

        // Nulls in the feed fall back to defaults instead of crashing.
        val renamed = page.spaces[1]
        assertEquals("renamed-space", renamed.displayTitle)
        assertEquals("zero-a10g", renamed.hardwareLabel)
        assertEquals("https://example-other-name.hf.space/", SpaceIds.resolveHost(renamed).toString())
        assertEquals(0, page.spaces[2].likes)

        val request = server.takeRequest()
        assertEquals("/spaces-json?p=0&includeNonRunning=true&withCount=false&search=hello", request.path)
    }

    @Test
    fun `pages past the end report no more results`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("<!doctype html><html></html>"))
        val page = hub.fetchSpacesPage(9999, null)
        assertTrue(page.spaces.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `first page errors are thrown`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val error = runCatching { hub.fetchSpacesPage(0, null) }.exceptionOrNull()
        assertTrue(error is HttpStatusException)
    }

    @Test
    fun `semantic search accepts bare arrays`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"a/b","likes":3},{"id":"bad id"}]"""))
        val results = hub.semanticSearch("image-generation", "cats")
        assertEquals(listOf("a/b"), results.map { it.id })
        assertEquals("/api/spaces/semantic-search?includeNonRunning=true&category=image-generation&q=cats", server.takeRequest().path)
    }

    @Test
    fun `fetch space builds a safe path and sends the token to the hub`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"gradio/hello_world","subdomain":"gradio-hello-world","host":"https://gradio-hello-world.hf.space","sdk":"gradio"}"""))
        val space = hub.fetchSpace("gradio/hello_world", "hf_token")
        assertEquals("https://gradio-hello-world.hf.space", space.host)
        val request = server.takeRequest()
        assertEquals("/api/spaces/gradio/hello_world", request.path)
        assertEquals("Bearer hf_token", request.getHeader("Authorization"))
    }

    @Test
    fun `fetch space refuses invalid ids without a request`() = runBlocking {
        val error = runCatching { hub.fetchSpace("../admin/x", null) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `who am i returns null for rejected tokens`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertNull(hub.whoAmI("hf_bad"))
        server.enqueue(MockResponse().setBody("""{"name":"ada","fullname":"Ada L","type":"user"}"""))
        assertEquals("Ada L", hub.whoAmI("hf_good")?.fullname)
    }

    @Test
    fun `server side sort and sdk are sent to the hub`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"spaces":[],"numItemsPerPage":24}"""))
        hub.fetchSpacesPage(1, null, HubSort.LIKES, "gradio")
        assertEquals("/spaces-json?p=1&includeNonRunning=true&withCount=false&sort=likes&sdk=gradio", server.takeRequest().path)
        server.enqueue(MockResponse().setBody("""{"spaces":[]}"""))
        hub.fetchSpacesPage(0, null, HubSort.TRENDING, "all")
        assertEquals("/spaces-json?p=0&includeNonRunning=true&withCount=false", server.takeRequest().path)
    }

    @Test
    fun `likes keep only valid space ids`() = runBlocking {
        // Shape captured from /api/users/{name}/likes.
        server.enqueue(
            MockResponse().setBody(
                """[{"createdAt":"2026-10-29T17:06:50.000Z","repo":{"name":"someone/dataset","type":"dataset"}},
                    {"repo":{"name":"gradio/hello_world","type":"space"}},{"repo":{"name":"../evil","type":"space"}}]"""
            )
        )
        assertEquals(listOf("gradio/hello_world"), hub.likedSpaceIds("ada"))
        assertEquals("/api/users/ada/likes", server.takeRequest().path)
        assertTrue(runCatching { hub.likedSpaceIds("../x") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `author listing and runtime`() = runBlocking {
        server.enqueue(MockResponse().setBody("""[{"id":"ada/one","likes":3,"sdk":"gradio"},{"id":"ada/two","private":true}]"""))
        assertEquals(listOf("ada/one", "ada/two"), hub.spacesByAuthor("ada", "hf_t").map { it.id })
        val list = server.takeRequest()
        assertEquals("/api/spaces?author=ada&limit=100&full=true", list.path)
        assertEquals("Bearer hf_t", list.getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("""{"stage":"SLEEPING","hardware":{"current":null,"requested":"cpu-basic"}}"""))
        assertEquals("SLEEPING", hub.fetchRuntime("ada/one", null).stage)
        assertEquals("/api/spaces/ada/one/runtime", server.takeRequest().path)
    }

    @Test
    fun `owner actions post with the token and explain permission errors`() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        hub.restartSpace("ada/one", "hf_w", factory = true)
        val restart = server.takeRequest()
        assertEquals("POST /api/spaces/ada/one/restart?factory=true", "${restart.method} ${restart.path}")
        assertEquals("Bearer hf_w", restart.getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("{}"))
        hub.requestHardware("ada/one", "hf_w", "t4-small")
        assertEquals("""{"flavor":"t4-small"}""", server.takeRequest().body.readUtf8())

        server.enqueue(MockResponse().setResponseCode(403))
        val error = runCatching { hub.pauseSpace("ada/one", "hf_r") }.exceptionOrNull()
        assertTrue(error is HttpStatusException && error.message!!.contains("write access"))
        assertTrue(runCatching { hub.requestHardware("ada/one", "hf_w", "a10g\"; rm") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `logs stream line by line`() = runBlocking {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "data: {\"data\":\"Running on local URL\\n\",\"timestamp\":\"t1\"}\n\n" +
                    ": keepalive\n\n" +
                    "data: {\"data\":\"Model loaded\",\"timestamp\":\"t2\"}\n\n"
            )
        )
        val lines = mutableListOf<String>()
        hub.streamLogs("ada/one", "hf_w", LogKind.RUN) { lines += it }
        assertEquals(listOf("Running on local URL", "Model loaded"), lines)
        assertEquals("/api/spaces/ada/one/logs/run", server.takeRequest().path)
    }
}
