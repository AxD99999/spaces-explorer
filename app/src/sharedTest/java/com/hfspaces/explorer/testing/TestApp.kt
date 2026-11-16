package com.hfspaces.explorer.testing

import com.hfspaces.explorer.AppContainer
import com.hfspaces.explorer.HfSpacesApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import com.hfspaces.explorer.data.remote.AppJson
import java.net.InetAddress
import kotlin.concurrent.thread

/** Application wired to [FakeHub] instead of Hugging Face. */
class TestApp : HfSpacesApplication() {
    override fun createContainer() = AppContainer(
        context = this,
        hubBaseUrl = FakeHub.baseUrl,
        hostResolver = { FakeHub.baseUrl },
        tokenCipher = FakeTokenCipher(),
        databaseName = null
    )
}

/** A tiny stand-in for the Hub and for the `gradio/hello_world` Space. */
object FakeHub {
    val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = route(request)
        }
    }

    /** Started off the main thread: on a device, binding and resolving the host are network calls. */
    val baseUrl: HttpUrl by lazy {
        var url: HttpUrl? = null
        thread {
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            url = server.url("/")
        }.join()
        checkNotNull(url)
    }

    @Volatile private var lastName = ""

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        return when {
            path.startsWith("/spaces-json") ->
                if (path.contains("p=0")) json(Fixtures.read("spaces_page.json")) else MockResponse().setResponseCode(400)
            path.startsWith("/api/spaces/semantic-search") -> json("[]")
            path.startsWith("/api/spaces/") -> {
                val id = path.removePrefix("/api/spaces/")
                // Like the real Hub: the title and emoji only appear inside cardData.
                json(
                    """{"id":"$id","sdk":"gradio","likes":543,"private":false,"gated":false,"author":"${id.substringBefore('/')}",""" +
                        """"host":"https://gradio-hello-world.hf.space","subdomain":"gradio-hello-world",""" +
                        """"lastModified":"2026-11-12T20:42:02.000Z","createdAt":"2022-08-30T06:21:57.000Z",""" +
                        """"cardData":{"title":"Hello World","emoji":"👋","sdk":"gradio","sdk_version":"6.32.0","license":"mit","pinned":false},""" +
                        """"models":["openai/whisper-large-v3"],""" +
                        """"runtime":{"stage":"RUNNING","hardware":{"current":"cpu-basic","requested":"cpu-basic"}}}"""
                )
            }
            path == "/gradio_api/info" -> json(Fixtures.read("info_hello-world.json"))
            request.method == "POST" && path == "/gradio_api/call/predict" -> {
                val data = AppJson.parseToJsonElement(request.body.readUtf8()) as JsonObject
                lastName = (data["data"] as JsonArray).first().jsonPrimitive.content
                json("""{"event_id":"evt"}""")
            }
            path == "/gradio_api/call/predict/evt" ->
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: complete\ndata: [\"Hello $lastName!!\"]\n\n")
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
