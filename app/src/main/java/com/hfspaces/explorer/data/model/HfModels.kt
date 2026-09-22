package com.hfspaces.explorer.data.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable

@Serializable
data class HfAuthorData(
    val _id: String? = null,
    val avatarUrl: String? = null,
    val fullname: String? = null,
    val name: String? = null,
    val type: String? = null,
    val isPro: Boolean? = false,
    val isHf: Boolean? = false,
    val isHfAdmin: Boolean? = false,
    val isMod: Boolean? = false,
    val followerCount: Int? = 0,
    val isUserFollowing: Boolean? = false
)

@Serializable
data class HfSpaceHardware(
    val current: String? = null,
    val requested: String? = null
)

@Serializable
data class HfSpaceDomain(
    val domain: String = "",
    val stage: String = ""
)

@Serializable
data class HfSpaceRuntime(
    val stage: String? = "RUNNING",
    val hardware: HfSpaceHardware? = null,
    val gcTimeout: Int? = null,
    val devMode: Boolean? = false,
    val domains: List<HfSpaceDomain>? = null,
    val sha: String? = null
)

@Serializable
data class HfSpace(
    val id: String,
    val title: String? = null,
    val author: String = "",
    val authorData: HfAuthorData? = null,
    val colorFrom: String? = null,
    val colorTo: String? = null,
    val emoji: String? = null,
    val sdk: String? = "gradio",
    val runtime: HfSpaceRuntime? = null,
    val shortDescription: String? = null,
    @SerializedName("ai_short_description")
    val ai_short_description: String? = null,
    @SerializedName("ai_category")
    val ai_category: String? = null,
    val likes: Int? = 0,
    val trendingScore: Double? = 0.0,
    val createdAt: String? = null,
    val lastModified: String? = null,
    val tags: List<String>? = emptyList(),
    val pinned: Boolean? = false,
    val featured: Boolean? = false,
    val visibility: String? = "public",
    val disabled: Boolean? = false,
    val subdomain: String? = null,
    val repoType: String? = "space",
    val isLikedByUser: Boolean? = false
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: id.substringAfter('/')

    val displayAuthor: String
        get() = author.takeIf { it.isNotBlank() } ?: id.substringBefore('/')

    val displayEmoji: String
        get() = emoji?.takeIf { it.isNotBlank() } ?: "🤗"

    val isRunning: Boolean
        get() = runtime?.stage?.equals("RUNNING", ignoreCase = true) == true

    val isSleeping: Boolean
        get() = runtime?.stage?.equals("SLEEPING", ignoreCase = true) == true

    val hardwareType: String
        get() = runtime?.hardware?.current ?: "cpu-basic"

    val calculatedCategory: String
        get() {
            if (!ai_category.isNullOrBlank()) return ai_category
            val t = tags?.joinToString(" ")?.lowercase() ?: ""
            val fullText = "$title $id $shortDescription $ai_short_description $t".lowercase()

            return when {
                fullText.contains("image edit") || fullText.contains("inpaint") || fullText.contains("remove background") -> "Image Editing"
                fullText.contains("text-to-image") || fullText.contains("image-generation") || fullText.contains("diffusion") || fullText.contains("flux") -> "Image Generation"
                fullText.contains("video") || fullText.contains("animate") || fullText.contains("motion") -> "Video Generation"
                fullText.contains("chat") || fullText.contains("bot") || fullText.contains("assistant") -> "Chatbots"
                fullText.contains("music") || fullText.contains("audio") || fullText.contains("speech") || fullText.contains("tts") -> "Audio & Music"
                fullText.contains("llm") || fullText.contains("text generation") || fullText.contains("summariz") -> "Text Generation"
                fullText.contains("ocr") || fullText.contains("pdf") || fullText.contains("document") -> "OCR & Documents"
                fullText.contains("3d") || fullText.contains("mesh") || fullText.contains("splat") -> "3D Modeling"
                fullText.contains("vision") || fullText.contains("yolo") || fullText.contains("detect") -> "Computer Vision"
                else -> "AI Space"
            }
        }
}

@Serializable
data class HfSpacesApiResponse(
    val spaces: List<HfSpace> = emptyList(),
    val numItemsPerPage: Int? = 24,
    val numTotalItems: Int? = null,
    val pageIndex: Int? = 0
)

data class SemanticCategory(
    val id: String,
    val name: String,
    val emoji: String
)

val HF_CATEGORIES = listOf(
    SemanticCategory("all", "All Spaces", "✨"),
    SemanticCategory("image-generation", "Image Generation", "🎨"),
    SemanticCategory("text-generation", "Text Generation", "✍️"),
    SemanticCategory("video-generation", "Video Generation", "🎥"),
    SemanticCategory("chatbots", "Chatbots", "💬"),
    SemanticCategory("image-editing", "Image Editing", "🪄"),
    SemanticCategory("music-generation", "Music Generation", "🎵"),
    SemanticCategory("speech-synthesis", "Speech Synthesis", "🎙️"),
    SemanticCategory("text-analysis", "Text Analysis", "📊"),
    SemanticCategory("data-visualization", "Data Visualization", "📈"),
    SemanticCategory("ocr", "OCR & Documents", "📄")
)

data class SpaceFilterState(
    val search: String = "",
    val status: String = "all", // "all", "running", "sleeping"
    val sdk: String = "all", // "all", "gradio", "docker", "streamlit", "static"
    val category: String = "all",
    val hardware: String = "all",
    val author: String = "all",
    val pinnedOnly: Boolean = false,
    val featuredOnly: Boolean = false,
    val sortBy: SortOption = SortOption.TRENDING_DESC
) {
    val isActive: Boolean
        get() = search.isNotBlank() ||
                status != "all" ||
                sdk != "all" ||
                category != "all" ||
                hardware != "all" ||
                author != "all" ||
                pinnedOnly ||
                featuredOnly ||
                sortBy != SortOption.TRENDING_DESC

    val activeCount: Int
        get() {
            var c = 0
            if (status != "all") c++
            if (sdk != "all") c++
            if (category != "all") c++
            if (hardware != "all") c++
            if (author != "all") c++
            if (pinnedOnly) c++
            if (featuredOnly) c++
            if (sortBy != SortOption.TRENDING_DESC) c++
            return c
        }
}

enum class SortOption(val displayName: String) {
    TRENDING_DESC("Trending Score"),
    LIKES_DESC("Most Likes"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    RECENT_DESC("Recently Updated"),
    CREATED_DESC("Newest Created")
}

@Serializable
data class UserApiKey(
    val id: String,
    val name: String,
    val token: String,
    val createdAt: String,
    val lastUsed: String? = null,
    val isDefault: Boolean = false
)

@Serializable
data class SpaceEndpointParam(
    val label: String = "",
    val parameter_name: String = "",
    val parameter_has_default: Boolean? = false,
    val parameter_default: String? = null,
    val component: String? = null,
    val example_input: String? = null
)

@Serializable
data class SpaceEndpointReturn(
    val label: String = "",
    val component: String? = null
)

@Serializable
data class SpaceEndpointSpec(
    val name: String,
    val parameters: List<SpaceEndpointParam> = emptyList(),
    val returns: List<SpaceEndpointReturn> = emptyList()
)

@Serializable
data class SpaceApiSpec(
    val spaceId: String,
    val subdomain: String? = null,
    val host: String,
    val sdk: String? = "gradio",
    val endpoints: List<SpaceEndpointSpec> = emptyList()
)

enum class GeneratedMediaType {
    IMAGE, VIDEO, AUDIO, TEXT, OTHER
}

@Serializable
data class GeneratedMediaItem(
    val id: String,
    val type: GeneratedMediaType,
    val url: String? = null,
    val textContent: String? = null,
    val title: String,
    val prompt: String? = null,
    val spaceId: String,
    val spaceTitle: String,
    val spaceEmoji: String? = "🤗",
    val createdAt: String,
    val endpoint: String? = null,
    val elapsedSeconds: Double? = null
)

enum class TaskStatus {
    RUNNING, COMPLETED, ERROR
}

data class ActiveSpaceTask(
    val id: String,
    val space: HfSpace,
    val endpoint: String,
    val formValues: Map<String, Any> = emptyMap(),
    val status: TaskStatus = TaskStatus.RUNNING,
    val startTime: Long = System.currentTimeMillis(),
    val elapsedSeconds: Double = 0.0,
    val result: Any? = null,
    val error: String? = null,
    val savedMediaIds: List<String> = emptyList()
)
