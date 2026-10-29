package com.hfspaces.explorer.data.gradio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    private fun parse(stream: String): List<SseEvent> {
        val parser = SseParser()
        val events = stream.split("\n").mapNotNull(parser::feed).toMutableList()
        parser.finish()?.let(events::add)
        return events
    }

    @Test
    fun `parses a gradio complete event`() {
        assertEquals(listOf(SseEvent("complete", "[7]")), parse("event: complete\ndata: [7]\n\n"))
    }

    @Test
    fun `parses heartbeat generating and error sequences`() {
        val events = parse(
            "event: heartbeat\ndata: null\n\n" +
                "event: generating\ndata: [\"partial\"]\n\n" +
                "event: error\ndata: {\"error\": \"Cannot divide by zero!\"}\n\n"
        )
        assertEquals(listOf("heartbeat", "generating", "error"), events.map { it.event })
        assertEquals("{\"error\": \"Cannot divide by zero!\"}", events[2].data)
    }

    @Test
    fun `joins multi-line data and handles CRLF and missing space`() {
        assertEquals(listOf(SseEvent("complete", "[1,\n2]")), parse("event:complete\r\ndata:[1,\r\ndata: 2]\r\n\r\n"))
    }

    @Test
    fun `ignores comments and unknown fields`() {
        assertEquals(listOf(SseEvent("complete", "[]")), parse(": keepalive\nid: 5\nretry: 1000\nevent: complete\ndata: []\n\n"))
    }

    @Test
    fun `flushes a final event without trailing blank line`() {
        assertEquals(listOf(SseEvent("complete", "[\"x\"]")), parse("event: complete\ndata: [\"x\"]"))
    }

    @Test
    fun `blank lines alone dispatch nothing`() {
        val parser = SseParser()
        assertNull(parser.feed(""))
        assertNull(parser.feed(""))
        assertNull(parser.finish())
    }

    @Test
    fun `data without event name is a message`() {
        assertEquals(listOf(SseEvent("message", "hi")), parse("data: hi\n\n"))
    }
}
