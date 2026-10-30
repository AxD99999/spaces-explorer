package com.hfspaces.explorer.data.model

import com.hfspaces.explorer.data.gradio.FormValue
import kotlinx.serialization.json.JsonElement

data class UserApiKey(
    val id: String,
    val name: String,
    val token: String,
    val createdAt: String,
    val isDefault: Boolean = false
) {
    /** Never show a full token on screen. */
    val maskedToken: String
        get() = if (token.length <= 10) "••••••" else "${token.take(5)}••••••${token.takeLast(4)}"
}

enum class GeneratedMediaType {
    IMAGE, VIDEO, AUDIO, FILE, TEXT, OTHER
}

data class GeneratedMediaItem(
    val id: String,
    val type: GeneratedMediaType,
    /** Original URL on the Space; Gradio deletes these after a while. */
    val url: String? = null,
    /** Copy saved in app storage, preferred for display when present. */
    val localPath: String? = null,
    val mimeType: String? = null,
    val textContent: String? = null,
    val title: String,
    val prompt: String? = null,
    val spaceId: String,
    val spaceTitle: String,
    val spaceEmoji: String? = null,
    val createdAt: String,
    val endpoint: String? = null,
    val elapsedSeconds: Double? = null
) {
    /** What to hand to an image loader or player. */
    val displaySource: String?
        get() = localPath?.let { "file://$it" } ?: url
}

enum class TaskStatus {
    RUNNING, COMPLETED, ERROR, CANCELLED
}

data class SpaceTask(
    val id: String,
    val space: HfSpace,
    val endpoint: String,
    val status: TaskStatus = TaskStatus.RUNNING,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    /** Latest progress message from the Space's queue, e.g. "Generating…". */
    val progressMessage: String? = null,
    /** Position in the Space's queue (0 = next), while waiting. */
    val queueRank: Int? = null,
    val queueSize: Int? = null,
    /** Seconds the Space expects until this run finishes or starts. */
    val etaSeconds: Double? = null,
    /** 0..1 when the Space reports step progress. */
    val progressFraction: Float? = null,
    /** Inputs the run was started with, so it can be repeated. */
    val inputs: Map<Int, FormValue>? = null,
    /** Final (or, while generating, partial) output data array. */
    val result: JsonElement? = null,
    val error: String? = null,
    val savedMediaIds: List<String> = emptyList()
) {
    val isActive: Boolean
        get() = status == TaskStatus.RUNNING

    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Double =
        ((endTime ?: now) - startTime).coerceAtLeast(0) / 1000.0
}
