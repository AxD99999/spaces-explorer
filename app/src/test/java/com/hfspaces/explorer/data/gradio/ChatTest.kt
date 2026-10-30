package com.hfspaces.explorer.data.gradio

import com.hfspaces.explorer.data.remote.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTest {
    private fun endpoint(info: String) = AppJson.decodeFromString<GradioApiInfo>(info).toEndpoints()

    // Captured from gradio/chatinterface_streaming_echo (a ChatInterface: stateless /slow_echo).
    private val chatInterface = endpoint(
        """{"named_endpoints":{"/slow_echo":{"parameters":[{"label":"message","parameter_name":"message","parameter_has_default":false,
           "type":{"type":"string"},"python_type":{"type":"str","description":""},"component":"Textbox"}],
           "returns":[{"label":"Response","component":"Json"}]}}}"""
    ).single()

    // Shape of gradio/chatbot_streaming's /user: message + Chatbot history in "messages" format.
    private val withHistory = endpoint(
        """{"named_endpoints":{"/user":{"parameters":[
            {"label":"user_message","parameter_name":"user_message","parameter_has_default":false,"type":{"type":"string"},"component":"Textbox"},
            {"label":"history","parameter_name":"history","parameter_has_default":true,"parameter_default":[],
             "type":{"${'$'}defs":{"ComponentMessage":{"properties":{"role":{"type":"string"}}}},"items":{}},"component":"Chatbot"}],
           "returns":[{"label":"value_2","component":"Textbox"},{"label":"value_1","component":"Chatbot"}]}}}"""
    ).single()

    @Test
    fun `recognises chat interface and chatbot endpoints`() {
        assertEquals(ChatShape(messageIndex = 0, historyIndex = null, historyFormat = HistoryFormat.MESSAGES, responseIndex = 0), chatInterface.chatShape())
        assertEquals(ChatShape(messageIndex = 0, historyIndex = 1, historyFormat = HistoryFormat.MESSAGES, responseIndex = 1), withHistory.chatShape())
    }

    @Test
    fun `plain endpoints and required extra inputs are not chats`() {
        assertNull(endpoint("""{"named_endpoints":{"/predict":{"parameters":[{"parameter_name":"name","component":"Textbox","type":{"type":"string"}}],"returns":[{"label":"output","component":"Textbox"}]}}}""").single().chatShape())
        assertNull(
            endpoint(
                """{"named_endpoints":{"/chat":{"parameters":[
                    {"parameter_name":"message","component":"Textbox","type":{"type":"string"}},
                    {"parameter_name":"image","component":"Image","parameter_has_default":false}],"returns":[{"label":"Response"}]}}}"""
            ).single().chatShape()
        )
    }

    @Test
    fun `payload includes history in messages format`() {
        val shape = withHistory.chatShape()!!
        val payload = buildChatPayload(
            withHistory,
            shape,
            "and you?",
            listOf(ChatMessage(ChatRole.USER, "hi"), ChatMessage(ChatRole.ASSISTANT, "hello"), ChatMessage(ChatRole.ASSISTANT, "oops", isError = true))
        )
        assertEquals("and you?", payload[0].jsonPrimitive.content)
        val history = payload[1].jsonArray
        assertEquals(2, history.size)
        assertEquals("assistant", history[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tuple history pairs user and bot turns`() {
        val tuples = withHistory.copy(parameters = withHistory.parameters.mapIndexed { i, p -> if (i == 1) p.copy(type = AppJson.parseToJsonElement("{\"type\":\"array\"}")) else p })
        val shape = tuples.chatShape()!!
        assertEquals(HistoryFormat.TUPLES, shape.historyFormat)
        val payload = buildChatPayload(tuples, shape, "q2", listOf(ChatMessage(ChatRole.USER, "q1"), ChatMessage(ChatRole.ASSISTANT, "a1")))
        assertEquals(AppJson.parseToJsonElement("""[["q1","a1"]]"""), payload[1])
    }

    @Test
    fun `reply extraction handles strings and chatbot histories`() {
        val echo = chatInterface.chatShape()!!
        // Real streamed output: ["You typed: hi", null]
        assertEquals("You typed: hi", extractChatReply(AppJson.parseToJsonElement("""["You typed: hi", null]"""), echo))
        val bot = withHistory.chatShape()!!
        assertEquals(
            "Hello!",
            extractChatReply(AppJson.parseToJsonElement("""["", [{"role":"user","content":"hi"},{"role":"assistant","content":"Hello!"}]]"""), bot)
        )
        assertEquals(
            "a1",
            extractChatReply(AppJson.parseToJsonElement("""["", [["q1","a1"]]]"""), bot)
        )
        assertEquals(
            "part one, part two",
            extractChatReply(
                AppJson.parseToJsonElement("""["", [{"role":"assistant","content":[{"type":"text","text":"part one, "},{"type":"text","text":"part two"}]}]]"""),
                bot
            )
        )
        assertNull(extractChatReply(JsonArray(emptyList()), echo))
    }

    @Test
    fun `form values round trip through json`() {
        val values = mapOf(
            0 to FormValue.Text("a cat"),
            1 to FormValue.Bool(true),
            2 to FormValue.Multi(listOf("x", "y")),
            3 to FormValue.Files(listOf(PickedFile("file:///c/a.png", "a.png", "image/png", 10)))
        )
        assertEquals(values, decodeFormValues(encodeFormValues(values)))
        assertNull(decodeFormValues("not json"))
        val pruned = values.withoutMissingFiles { false }
        assertEquals(FormValue.Files(emptyList()), pruned[3])
    }
}
