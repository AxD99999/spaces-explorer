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
}
