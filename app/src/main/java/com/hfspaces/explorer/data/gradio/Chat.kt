package com.hfspaces.explorer.data.gradio

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** How a chat endpoint wants previous turns: Gradio 4+ "messages" or legacy `[user, bot]` pairs. */
enum class HistoryFormat { MESSAGES, TUPLES }

/** Where the message, history and reply live in a chat-style endpoint. */
data class ChatShape(
    val messageIndex: Int,
    val historyIndex: Int?,
    val historyFormat: HistoryFormat,
    val responseIndex: Int
)

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(val role: ChatRole, val text: String, val isError: Boolean = false)

private val MESSAGE_NAMES = Regex("message|msg|text|prompt|query|input|question|user", RegexOption.IGNORE_CASE)
private val CHATTY_NAMES = Regex("chat|respond|reply|message|converse|echo|bot", RegexOption.IGNORE_CASE)

/**
 * Recognises endpoints that work as a chat: a text message, optionally a Chatbot history, and
 * every other input optional. Returns null for anything else.
 */
fun SpaceEndpoint.chatShape(): ChatShape? {
    val kinds = parameters.map { it.inputKind() }
    val textIndices = kinds.indices.filter { kinds[it] is InputKind.Text }
    if (textIndices.isEmpty()) return null
    val historyIndex = parameters.indexOfFirst { it.component.equals("chatbot", ignoreCase = true) }.takeIf { it >= 0 }
    val chatbotReturn = returns.indexOfFirst { it.component.equals("chatbot", ignoreCase = true) }.takeIf { it >= 0 }
    val chatInterfaceReturn = returns.size == 1 && returns[0].label.equals("Response", ignoreCase = true)
    val looksChatty = historyIndex != null || chatbotReturn != null || chatInterfaceReturn || CHATTY_NAMES.containsMatchIn(name)
    if (!looksChatty || returns.isEmpty()) return null

    val messageIndex = textIndices.firstOrNull { i ->
        MESSAGE_NAMES.containsMatchIn(parameters[i].parameterName.orEmpty()) || MESSAGE_NAMES.containsMatchIn(parameters[i].label.orEmpty())
    } ?: textIndices.first()
    // Anything else must be optional, since the chat screen only asks for a message.
    val othersOptional = parameters.indices.all { it == messageIndex || it == historyIndex || parameters[it].hasDefault }
    if (!othersOptional) return null

    val historyFormat = historyIndex
        ?.let { parameters[it].type?.toString().orEmpty() }
        ?.let { if (it.contains("\"role\"") || it.contains("MessageDict") || it.contains("ComponentMessage")) HistoryFormat.MESSAGES else HistoryFormat.TUPLES }
        ?: HistoryFormat.MESSAGES
    return ChatShape(messageIndex, historyIndex, historyFormat, chatbotReturn ?: 0)
}

/** Builds the call payload: defaults everywhere, plus the message and (if supported) the history. */
fun buildChatPayload(
    endpoint: SpaceEndpoint,
    shape: ChatShape,
    message: String,
    history: List<ChatMessage>
): JsonArray {
    val values = endpoint.parameters.mapIndexed { i, p -> i to initialFormValue(p, p.inputKind()) }.toMap() +
        (shape.messageIndex to FormValue.Text(message))
    val base = buildPayload(endpoint, values.filterKeys { it != shape.historyIndex }, emptyMap())
    val historyIndex = shape.historyIndex ?: return base
    return JsonArray(base.toMutableList().also { it[historyIndex] = historyJson(history.filterNot { m -> m.isError }, shape.historyFormat) })
}

private fun historyJson(history: List<ChatMessage>, format: HistoryFormat): JsonElement = when (format) {
    HistoryFormat.MESSAGES -> buildJsonArray {
        history.forEach { msg ->
            add(buildJsonObject {
                put("role", if (msg.role == ChatRole.USER) "user" else "assistant")
                put("content", msg.text)
            })
        }
    }
    HistoryFormat.TUPLES -> buildJsonArray {
        var pendingUser: String? = null
        history.forEach { msg ->
            if (msg.role == ChatRole.USER) {
                pendingUser?.let { add(JsonArray(listOf(JsonPrimitive(it), JsonNull))) }
                pendingUser = msg.text
            } else {
                add(JsonArray(listOf(pendingUser?.let(::JsonPrimitive) ?: JsonNull, JsonPrimitive(msg.text))))
                pendingUser = null
            }
        }
        pendingUser?.let { add(JsonArray(listOf(JsonPrimitive(it), JsonNull))) }
    }
}

/** The assistant's reply from a (possibly partial) output array. */
fun extractChatReply(data: JsonElement?, shape: ChatShape): String? {
    val outputs = data as? JsonArray ?: return (data as? JsonPrimitive)?.contentOrNull
    val value = outputs.getOrNull(shape.responseIndex) ?: return null
    return when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonArray -> lastAssistantMessage(value)
        is JsonObject -> contentText(value)
        else -> null
    }
}

private fun lastAssistantMessage(history: JsonArray): String? {
    for (entry in history.reversed()) {
        when (entry) {
            is JsonObject -> if ((entry["role"] as? JsonPrimitive)?.contentOrNull == "assistant") return contentText(entry)
            is JsonArray -> (entry.getOrNull(1) as? JsonPrimitive)?.contentOrNull?.let { return it }
            else -> Unit
        }
    }
    return null
}

private fun contentText(obj: JsonObject): String? = when (val content = obj["content"] ?: obj["text"] ?: obj["value"]) {
    is JsonPrimitive -> content.contentOrNull
    is JsonArray -> content.joinToString("") { part ->
        when (part) {
            is JsonPrimitive -> part.contentOrNull.orEmpty()
            is JsonObject -> ((part["text"] ?: part["content"]) as? JsonPrimitive)?.contentOrNull.orEmpty()
            else -> ""
        }
    }.takeIf { it.isNotEmpty() }
    null -> null
    else -> content.toString()
}
