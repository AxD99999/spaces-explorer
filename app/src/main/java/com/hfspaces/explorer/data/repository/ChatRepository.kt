package com.hfspaces.explorer.data.repository

import com.hfspaces.explorer.data.gradio.ChatMessage
import com.hfspaces.explorer.data.gradio.ChatRole
import com.hfspaces.explorer.data.local.dao.ChatDao
import com.hfspaces.explorer.data.local.entity.ChatMessageEntity
import com.hfspaces.explorer.data.remote.GradioClient
import java.util.concurrent.ConcurrentHashMap

/**
 * The Gradio session a conversation runs in. [firstMessage] is how many stored messages came
 * before it; the Space never saw those.
 */
data class ChatSession(val hash: String, val firstMessage: Int)

/** Chat transcripts per Space, kept across restarts, and the live session of each. */
class ChatRepository(private val dao: ChatDao) {
    /**
     * Sessions last for the process. Chat Spaces keep the conversation in Gradio session state on
     * their side, which is gone (or soon will be) once the app has been closed, so a restored
     * transcript starts a new session rather than pretending the Space still remembers.
     */
    private val sessions = ConcurrentHashMap<String, ChatSession>()

    suspend fun load(spaceId: String): List<ChatMessage> = dao.forSpace(spaceId).map { row ->
        ChatMessage(
            role = if (row.role == ChatRole.USER.name) ChatRole.USER else ChatRole.ASSISTANT,
            text = row.text,
            isError = row.isError
        )
    }

    suspend fun append(spaceId: String, message: ChatMessage) {
        dao.insert(
            ChatMessageEntity(
                spaceId = spaceId,
                role = message.role.name,
                text = message.text.take(MAX_MESSAGE_CHARS),
                isError = message.isError,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.trim(spaceId, MAX_MESSAGES)
    }

    suspend fun removeLast(spaceId: String, count: Int) = dao.deleteLast(spaceId, count)

    suspend fun clear(spaceId: String) {
        sessions.remove(spaceId)
        dao.deleteForSpace(spaceId)
    }

    suspend fun clearAll() {
        sessions.clear()
        dao.clear()
    }

    suspend fun messageCount(): Int = dao.count()

    fun session(spaceId: String): ChatSession? = sessions[spaceId]

    /** Starts a session for a conversation that already holds [messagesSoFar] messages. */
    fun startSession(spaceId: String, messagesSoFar: Int): ChatSession =
        ChatSession(GradioClient.newSessionHash(), messagesSoFar).also { sessions[spaceId] = it }

    private companion object {
        const val MAX_MESSAGES = 400
        const val MAX_MESSAGE_CHARS = 100_000
    }
}
