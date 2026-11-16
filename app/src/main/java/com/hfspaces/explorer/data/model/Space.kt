package com.hfspaces.explorer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

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
    val host: String? = null,
    @SerialName("private")
    val isPrivate: Boolean = false,
    /** `false`, or the gating mode ("auto" / "manual"). */
    val gated: JsonElement? = null,
    /** Models and datasets the Hub found in the Space's code. Untyped: a surprise here must not break the Space. */
    val models: JsonElement? = null,
    val datasets: JsonElement? = null,
    /**
     * The Space's README front matter. Space metadata and author listings keep the title, emoji and
     * description here rather than at the top level; see [normalized].
     */
    val cardData: JsonElement? = null
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

    val isGated: Boolean
        get() = (gated as? JsonPrimitive)?.let { it.isString || it.booleanOrNull == true } == true

    val license: String?
        get() = card("license")

    /** Version of the SDK the Space is built with, e.g. Gradio "5.25.2". */
    val sdkVersion: String?
        get() = card("sdk_version")

    val linkedModels: List<String>
        get() = (models.strings() + (cardData as? JsonObject)?.get("models").strings()).distinct()

    val linkedDatasets: List<String>
        get() = (datasets.strings() + (cardData as? JsonObject)?.get("datasets").strings()).distinct()

    /**
     * Lifts the title, emoji, colours and description out of [cardData] when the top level lacks
     * them, and keeps only the card keys the app reads. Card data is written by Space authors, so
     * every value is read leniently.
     */
    fun normalized(): HfSpace {
        val card = cardData as? JsonObject ?: return if (cardData == null) this else copy(cardData = null)
        return copy(
            title = title.orCard("title"),
            emoji = emoji.orCard("emoji"),
            colorFrom = colorFrom.orCard("colorFrom"),
            colorTo = colorTo.orCard("colorTo"),
            shortDescription = shortDescription.orCard("short_description"),
            pinned = pinned || (card["pinned"] as? JsonPrimitive)?.booleanOrNull == true,
            cardData = JsonObject(card.filterKeys { it in KEPT_CARD_KEYS }).takeIf { it.isNotEmpty() }
        )
    }

    private fun card(key: String): String? =
        ((cardData as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun String?.orCard(key: String): String? = this?.takeIf { it.isNotBlank() } ?: card(key)

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

private val KEPT_CARD_KEYS = setOf("license", "sdk_version", "models", "datasets")

private fun JsonElement?.strings(): List<String> =
    (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()

/**
 * This copy of a Space, with whatever it lacks filled in from an [older] one. Listings carry the
 * author, AI summary and trending score; metadata carries the host, card and linked models; so
 * neither may simply replace the other.
 */
fun HfSpace.mergedOver(older: HfSpace?): HfSpace {
    if (older == null || older.id != id) return this
    return copy(
        title = title ?: older.title,
        author = author ?: older.author,
        authorData = authorData ?: older.authorData,
        emoji = emoji ?: older.emoji,
        colorFrom = colorFrom ?: older.colorFrom,
        colorTo = colorTo ?: older.colorTo,
        sdk = sdk ?: older.sdk,
        runtime = runtime ?: older.runtime,
        shortDescription = shortDescription ?: older.shortDescription,
        aiShortDescription = aiShortDescription ?: older.aiShortDescription,
        aiCategory = aiCategory ?: older.aiCategory,
        trendingScore = if (trendingScore != 0.0) trendingScore else older.trendingScore,
        createdAt = createdAt ?: older.createdAt,
        lastModified = lastModified ?: older.lastModified,
        tags = tags.ifEmpty { older.tags },
        featured = featured || older.featured,
        subdomain = subdomain ?: older.subdomain,
        host = host ?: older.host,
        gated = gated ?: older.gated,
        models = models ?: older.models,
        datasets = datasets ?: older.datasets,
        cardData = cardData ?: older.cardData
    )
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
