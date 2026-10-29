package com.hfspaces.explorer.testing

import com.hfspaces.explorer.AppContainer
import com.hfspaces.explorer.HfSpacesApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import com.hfspaces.explorer.data.remote.AppJson

/** Application wired to [FakeHub] instead of Hugging Face. */
class TestApp : HfSpacesApplication() {
    override fun createContainer() = AppContainer(
        context = this,
        hubBaseUrl = FakeHub.server.url("/"),
        hostResolver = { FakeHub.server.url("/") },
        tokenCipher = FakeTokenCipher(),
        databaseName = null
    )
}

/** A tiny stand-in for the Hub and for the `gradio/hello_world` Space. */
object FakeHub {
    val server: MockWebServer by lazy {
        MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = route(request)
            }
            start()
        }
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
                json("""{"id":"$id","sdk":"gradio","host":"https://gradio-hello-world.hf.space","runtime":{"stage":"RUNNING","hardware":{"current":"cpu-basic"}},"title":"Hello World","emoji":"👋","likes":543}""")
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
