package com.hfspaces.explorer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HfAuthorData(
    val name: String? = null,
    val fullname: String? = null,
    val avatarUrl: String? = null,
    val isPro: Boolean = false
)

@Serializable
data class HfSpaceHardware(
    val current: String? = null,
    val requested: String? = null
)

@Serializable
data class HfSpaceDomain(
    val domain: String = "",
    val stage: String? = null
)

@Serializable
data class HfSpaceRuntime(
    val stage: String? = null,
    val hardware: HfSpaceHardware? = null,
    val domains: List<HfSpaceDomain> = emptyList(),
    val errorMessage: String? = null
)

/** Lifecycle stage of a Space, normalised from the Hub's free-form `runtime.stage`. */
enum class SpaceStage(val label: String) {
    RUNNING("Running"),
    SLEEPING("Sleeping"),
    BUILDING("Building"),
    PAUSED("Paused"),
    ERROR("Error"),
    UNKNOWN("Unknown");

    companion object {
        fun from(raw: String?): SpaceStage = when (raw?.uppercase()) {
            null -> UNKNOWN
            "RUNNING", "RUNNING_BUILDING", "RUNNING_APP_STARTING" -> RUNNING
            "SLEEPING" -> SLEEPING
            "BUILDING", "APP_STARTING", "STOPPED_BUILDING" -> BUILDING
            "PAUSED", "STOPPED" -> PAUSED
            "RUNTIME_ERROR", "BUILD_ERROR", "CONFIG_ERROR", "NO_APP_FILE" -> ERROR
            else -> UNKNOWN
        }
    }
}

@Serializable
data class HfSpace(
    val id: String,
    val title: String? = null,
    val author: String? = null,
    val authorData: HfAuthorData? = null,
    val emoji: String? = null,
    val colorFrom: String? = null,
    val colorTo: String? = null,
    val sdk: String? = null,
    val runtime: HfSpaceRuntime? = null,
    val shortDescription: String? = null,
    @SerialName("ai_short_description")
    val aiShortDescription: String? = null,
    @SerialName("ai_category")
    val aiCategory: String? = null,
    val likes: Int = 0,
    val trendingScore: Double = 0.0,
    val createdAt: String? = null,
    val lastModified: String? = null,
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val featured: Boolean = false,
    val subdomain: String? = null,
    val host: String? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: id.substringAfter('/')

    val displayAuthor: String
        get() = author?.takeIf { it.isNotBlank() } ?: id.substringBefore('/')

    val displayEmoji: String
        get() = emoji?.takeIf { it.isNotBlank() } ?: "🤗"

    val description: String?
        get() = shortDescription?.takeIf { it.isNotBlank() } ?: aiShortDescription?.takeIf { it.isNotBlank() }

    val stage: SpaceStage
        get() = SpaceStage.from(runtime?.stage)

    val hardwareLabel: String?
        get() = runtime?.hardware?.current ?: runtime?.hardware?.requested

    val sdkLabel: String
        get() = sdk?.takeIf { it.isNotBlank() } ?: "unknown"

    /** Only Gradio Spaces expose the queue API the runner talks to. */
    val isRunnable: Boolean
        get() = sdk.equals("gradio", ignoreCase = true)

    /** Tagged by Hugging Face as not suitable for all audiences. */
    val isAdult: Boolean
        get() = tags.any { it.equals(ADULT_TAG, ignoreCase = true) }

    val avatarUrl: String?
        get() = authorData?.avatarUrl?.let { if (it.startsWith("/")) "https://huggingface.co$it" else it }

    val hubUrl: String
        get() = "https://huggingface.co/spaces/$id"

    val category: String
        get() = aiCategory?.takeIf { it.isNotBlank() } ?: inferCategory()

    private fun inferCategory(): String {
        val text = listOfNotNull(title, id, shortDescription, aiShortDescription, tags.joinToString(" "))
            .joinToString(" ")
            .lowercase()
        return when {
            listOf("image edit", "inpaint", "remove background", "upscal").any(text::contains) -> "Image Editing"
            listOf("text-to-image", "image-generation", "diffusion", "flux").any(text::contains) -> "Image Generation"
            listOf("video", "animate").any(text::contains) -> "Video Generation"
            listOf("chat", "assistant").any(text::contains) -> "Chatbots"
            listOf("music", "audio", "speech", "tts", "voice").any(text::contains) -> "Audio & Music"
            listOf("llm", "text generation", "summariz", "translat").any(text::contains) -> "Text Generation"
            listOf("ocr", "pdf", "document").any(text::contains) -> "OCR & Documents"
            listOf("3d", "mesh", "splat").any(text::contains) -> "3D Modeling"
            listOf("vision", "yolo", "detect", "segment").any(text::contains) -> "Computer Vision"
            else -> "AI Space"
        }
    }
}

@Serializable
data class HfSpacesPage(
    val spaces: List<HfSpace> = emptyList(),
    val numItemsPerPage: Int? = null,
    val numTotalItems: Int? = null,
    val pageIndex: Int? = null
)

data class SemanticCategory(
    val id: String,
    val name: String,
    val emoji: String
)

const val ALL = "all"

val HF_CATEGORIES = listOf(
    SemanticCategory(ALL, "All", "✨"),
    SemanticCategory("image-generation", "Image Generation", "🎨"),
    SemanticCategory("text-generation", "Text Generation", "✍️"),
    SemanticCategory("video-generation", "Video Generation", "🎥"),
    SemanticCategory("chatbots", "Chatbots", "💬"),
    SemanticCategory("image-editing", "Image Editing", "🪄"),
    SemanticCategory("music-generation", "Music", "🎵"),
    SemanticCategory("speech-synthesis", "Speech", "🎙️"),
    SemanticCategory("text-analysis", "Text Analysis", "📊"),
    SemanticCategory("data-visualization", "Data Viz", "📈"),
    SemanticCategory("ocr", "OCR & Docs", "📄")
)

/** The Hub tag for adult content. */
const val ADULT_TAG = "not-for-all-audiences"

/** Drops adult Spaces unless the user has chosen to see them. */
fun List<HfSpace>.hidingAdult(show: Boolean): List<HfSpace> = if (show) this else filterNot { it.isAdult }
