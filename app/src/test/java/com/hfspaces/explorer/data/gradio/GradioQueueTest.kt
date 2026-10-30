package com.hfspaces.explorer.data.gradio

import com.hfspaces.explorer.data.remote.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradioQueueTest {
    private fun json(text: String) = AppJson.parseToJsonElement(text)
    private fun msg(text: String) = parseQueueMessage(json(text) as JsonObject)

    @Test
    fun `fn index comes from api name and respects queue false`() {
        // Shape captured from gradio/hello_world (Gradio 6, sse_v3).
        val config = AppJson.decodeFromString<GradioConfig>(
            """{"version":"6.31.0","protocol":"sse_v3","api_prefix":"/gradio_api","dependencies":[
                {"id":0,"api_name":"js_fn","queue":false},{"id":1,"api_name":false},{"id":2,"api_name":"predict","queue":true},
                {"id":5,"api_name":"lambda_1","queue":false}]}"""
        )
        assertEquals(2, config.queuedFnIndex("/predict"))
        assertNull(config.queuedFnIndex("lambda_1"))
        assertNull(config.queuedFnIndex("missing"))
        assertTrue(config.streamsDiffs)
    }

    @Test
    fun `older configs without ids use the list position`() {
        val config = AppJson.decodeFromString<GradioConfig>("""{"dependencies":[{"api_name":"a"},{"api_name":"b"}]}""")
        assertEquals(1, config.queuedFnIndex("b"))
    }

    @Test
    fun `queue messages captured from a real space parse`() {
        assertEquals(QueueMessage.Estimation(0, 1, 0.0026724763112525416), msg("""{"msg":"estimation","event_id":"x","rank":0,"queue_size":1,"rank_eta":0.0026724763112525416}"""))
        assertEquals(QueueMessage.Starts(null), msg("""{"msg":"process_starts","event_id":"x","eta":null}"""))
        assertEquals(
            QueueMessage.Completed(JsonArray(listOf(json("\"Hello Ada!\"")))),
            msg("""{"msg":"process_completed","event_id":"x","output":{"data":["Hello Ada!"],"is_generating":false},"success":true}""")
        )
        assertEquals(QueueMessage.Failed(null), msg("""{"msg":"process_completed","event_id":"x","output":{"error":null},"success":false,"title":"Error"}"""))
        assertEquals(QueueMessage.Closed, msg("""{"msg":"close_stream","event_id":null}"""))
    }

    @Test
    fun `progress uses index over length when no explicit fraction`() {
        val p = msg("""{"msg":"progress","progress_data":[{"index":3,"length":12,"unit":"steps","progress":null,"desc":"Denoising"}]}""") as QueueMessage.Progress
        assertEquals(0.25f, p.fraction!!, 0.0001f)
        assertEquals("Denoising", p.description)
    }

    @Test
    fun `diffs apply like the official clients`() {
        assertEquals(json("\"Hello\""), applyDiff(json("\"He\""), json("""[["append",[],"llo"]]""") as JsonArray))
        assertEquals(json("[1,9,3]"), applyDiff(json("[1,2,3]"), json("""[["replace",[1],9]]""") as JsonArray))
        assertEquals(
            json("""[{"role":"user","content":"hi"},{"role":"assistant","content":"Hello there"}]"""),
            applyDiff(
                json("""[{"role":"user","content":"hi"},{"role":"assistant","content":"Hello"}]"""),
                json("""[["append",[1,"content"]," there"]]""") as JsonArray
            )
        )
        assertEquals(json("[1,2,3]"), applyDiff(json("[1,3]"), json("""[["add",[1],2]]""") as JsonArray))
        assertEquals(json("""{"a":1}"""), applyDiff(json("""{"a":1,"b":2}"""), json("""[["delete",["b"],null]]""") as JsonArray))
        assertEquals(json("[1,2]"), applyDiff(json("[1]"), json("""[["append",[],[2]]]""") as JsonArray))
    }

    @Test
    fun `hidden state inputs are filled and state outputs dropped`() {
        // Shape of gradio/chatinterface_streaming_echo: slow_echo takes (textbox, state) and returns (json, state).
        val config = AppJson.decodeFromString<GradioConfig>(
            """{"protocol":"sse_v3","components":[{"id":16,"type":"textbox"},{"id":19,"type":"json"},{"id":20,"type":"state"}],
                "dependencies":[{"id":6,"api_name":"slow_echo","queue":true,"inputs":[16,20],"outputs":[19,20]}]}"""
        )
        val call = config.prepareQueuedCall("/slow_echo", json("""["hi"]""") as JsonArray)!!
        assertEquals(6, call.fnIndex)
        assertEquals(json("""["hi",null]"""), call.data)
        assertEquals(json("""["You typed: hi"]"""), call.visible(json("""["You typed: hi",{"history":[]}]""") as JsonArray))
        // A payload that doesn't fit the visible inputs is left to /call.
        assertNull(config.prepareQueuedCall("slow_echo", json("""["a","b"]""") as JsonArray))
    }
}
