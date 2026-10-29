package com.hfspaces.explorer.data.gradio

/** One server-sent event. */
data class SseEvent(val event: String, val data: String)

/**
 * Incremental parser for `text/event-stream`, fed one line at a time.
 *
 * Follows the WHATWG rules that matter here: `data:` lines accumulate (joined by `\n`), a blank
 * line dispatches, `:` lines are comments, and an optional single space after the colon is dropped.
 */
class SseParser {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Returns the event completed by [line], if any. */
    fun feed(line: String): SseEvent? {
        val clean = line.removeSuffix("\r")
        if (clean.isEmpty()) return dispatch()
        if (clean.startsWith(":")) return null

        val colon = clean.indexOf(':')
        val field = if (colon == -1) clean else clean.substring(0, colon)
        var value = if (colon == -1) "" else clean.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    /** Flushes a final event that wasn't followed by a blank line before the stream closed. */
    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        val result = if (hasData || event != null) SseEvent(event ?: "message", data.toString()) else null
        event = null
        data.setLength(0)
        hasData = false
        return result
    }
}
