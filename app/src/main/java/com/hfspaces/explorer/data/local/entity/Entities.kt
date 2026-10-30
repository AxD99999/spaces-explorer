package com.hfspaces.explorer.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.HfSpaceHardware
import com.hfspaces.explorer.data.model.HfSpaceRuntime
import com.hfspaces.explorer.data.remote.AppJson

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
    val savedAt: Long = System.currentTimeMillis(),
    /** Full Hub metadata (added in v2); the columns above remain for v1 rows. */
    val spaceJson: String? = null
) {
    fun toHfSpace(): HfSpace {
        spaceJson?.let { json -> runCatching { return AppJson.decodeFromString<HfSpace>(json) } }
        return HfSpace(
            id = id,
            title = title,
            author = author,
            emoji = emoji,
            colorFrom = colorFrom,
            colorTo = colorTo,
            sdk = sdk,
            runtime = HfSpaceRuntime(stage = stage, hardware = HfSpaceHardware(current = hardware)),
            shortDescription = shortDescription,
            aiCategory = aiCategory,
            likes = likes,
            trendingScore = trendingScore,
            tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(","),
            subdomain = subdomain
        )
    }

    companion object {
        fun fromHfSpace(space: HfSpace, savedAt: Long = System.currentTimeMillis()) = FavoriteSpaceEntity(
            id = space.id,
            title = space.title,
            author = space.displayAuthor,
            emoji = space.emoji,
            colorFrom = space.colorFrom,
            colorTo = space.colorTo,
            sdk = space.sdk,
            stage = space.runtime?.stage,
            hardware = space.hardwareLabel,
            shortDescription = space.description,
            aiCategory = space.aiCategory,
            likes = space.likes,
            trendingScore = space.trendingScore,
            tagsCsv = space.tags.joinToString(","),
            subdomain = space.subdomain,
            savedAt = savedAt,
            spaceJson = AppJson.encodeToString(HfSpace.serializer(), space)
        )
    }
}

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Encrypted with [com.hfspaces.explorer.data.security.TokenCipher] since v2. */
    val token: String,
    val createdAt: String,
    val lastUsed: String? = null,
    val isDefault: Boolean = false
)

@Entity(tableName = "generated_media")
data class GeneratedMediaEntity(
    @PrimaryKey val id: String,
    val type: String,
    val url: String?,
    val textContent: String?,
    val title: String,
    val prompt: String?,
    val spaceId: String,
    val spaceTitle: String,
    val spaceEmoji: String?,
    val createdAt: String,
    val endpoint: String?,
    val elapsedSeconds: Double?,
    val localPath: String? = null,
    val mimeType: String? = null
)

@Entity(tableName = "task_history")
data class TaskEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val spaceJson: String,
    val endpoint: String,
    val status: String,
    val startTime: Long,
    val endTime: Long?,
    val error: String?,
    val resultJson: String?,
    @ColumnInfo(defaultValue = "")
    val savedMediaIds: String = "",
    /** Form values the run started with (v3), see encodeFormValues. */
    val inputsJson: String? = null
)

/** A named set of inputs for one endpoint of a Space. */
@Entity(tableName = "presets", indices = [Index("spaceId")])
data class PresetEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val endpoint: String,
    val name: String,
    val valuesJson: String,
    val createdAt: Long
)

/** A user-made group of favourites. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "collection_spaces",
    primaryKeys = ["collectionId", "spaceId"],
    indices = [Index("spaceId")]
)
data class CollectionSpaceEntity(
    val collectionId: String,
    val spaceId: String
)
