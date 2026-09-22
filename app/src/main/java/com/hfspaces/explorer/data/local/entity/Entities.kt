package com.hfspaces.explorer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.model.HfSpace

@Entity(tableName = "favorite_spaces")
data class FavoriteSpaceEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val author: String,
    val emoji: String?,
    val colorFrom: String?,
    val colorTo: String?,
    val sdk: String?,
    val stage: String?,
    val hardware: String?,
    val shortDescription: String?,
    val aiCategory: String?,
    val likes: Int,
    val trendingScore: Double,
    val tagsCsv: String,
    val subdomain: String?,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toHfSpace(): HfSpace {
        return HfSpace(
            id = id,
            title = title,
            author = author,
            emoji = emoji,
            colorFrom = colorFrom,
            colorTo = colorTo,
            sdk = sdk,
            runtime = com.hfspaces.explorer.data.model.HfSpaceRuntime(
                stage = stage,
                hardware = com.hfspaces.explorer.data.model.HfSpaceHardware(current = hardware)
            ),
            shortDescription = shortDescription,
            ai_category = aiCategory,
            likes = likes,
            trendingScore = trendingScore,
            tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(","),
            subdomain = subdomain
        )
    }

    companion object {
        fun fromHfSpace(space: HfSpace): FavoriteSpaceEntity {
            return FavoriteSpaceEntity(
                id = space.id,
                title = space.title,
                author = space.author,
                emoji = space.emoji,
                colorFrom = space.colorFrom,
                colorTo = space.colorTo,
                sdk = space.sdk,
                stage = space.runtime?.stage ?: "RUNNING",
                hardware = space.runtime?.hardware?.current ?: "cpu-basic",
                shortDescription = space.shortDescription ?: space.ai_short_description,
                aiCategory = space.ai_category,
                likes = space.likes ?: 0,
                trendingScore = space.trendingScore ?: 0.0,
                tagsCsv = space.tags?.joinToString(",") ?: "",
                subdomain = space.subdomain
            )
        }
    }
}

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val token: String,
    val createdAt: String,
    val lastUsed: String? = null,
    val isDefault: Boolean = false
)

@Entity(tableName = "generated_media")
data class GeneratedMediaEntity(
    @PrimaryKey val id: String,
    val type: String, // IMAGE, VIDEO, AUDIO, TEXT, OTHER
    val url: String?,
    val textContent: String?,
    val title: String,
    val prompt: String?,
    val spaceId: String,
    val spaceTitle: String,
    val spaceEmoji: String?,
    val createdAt: String,
    val endpoint: String?,
    val elapsedSeconds: Double?
)
