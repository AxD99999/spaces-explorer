package com.hfspaces.explorer.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.hfspaces.explorer.data.local.AppDatabase
import com.hfspaces.explorer.data.local.entity.ApiKeyEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.local.entity.GeneratedMediaEntity
import com.hfspaces.explorer.data.model.*
import com.hfspaces.explorer.data.remote.HfApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class SpacesRepository(private val api: HfApiClient) {
    suspend fun getSpaces(page: Int, search: String? = null, filter: String? = null): List<HfSpace> {
        return api.fetchSpaces(page, search, filter)
    }

    suspend fun getSemanticSpaces(category: String? = null, query: String? = null): List<HfSpace> {
        return api.fetchSemanticSearch(category, query)
    }

    suspend fun getSpaceInfo(spaceId: String, token: String? = null): SpaceApiSpec {
        return api.fetchSpaceInfo(spaceId, token)
    }
}

class FavoritesRepository(private val database: AppDatabase) {
    private val dao = database.favoritesDao()

    fun getAllFavorites(): Flow<List<HfSpace>> {
        return dao.getAllFavoritesFlow().map { list ->
            list.map { it.toHfSpace() }
        }
    }

    fun isFavorite(spaceId: String): Flow<Boolean> {
        return dao.isFavoriteFlow(spaceId)
    }

    suspend fun toggleFavorite(space: HfSpace): Boolean {
        val exists = dao.isFavorite(space.id)
        if (exists) {
            dao.deleteFavoriteById(space.id)
            return false
        } else {
            dao.insertFavorite(FavoriteSpaceEntity.fromHfSpace(space))
            return true
        }
    }
}

class ApiKeysRepository(private val database: AppDatabase) {
    private val dao = database.apiKeysDao()

    fun getAllApiKeys(): Flow<List<UserApiKey>> {
        return dao.getAllApiKeysFlow().map { list ->
            list.map { UserApiKey(id = it.id, name = it.name, token = it.token, createdAt = it.createdAt, lastUsed = it.lastUsed, isDefault = it.isDefault) }
        }
    }

    suspend fun addApiKey(name: String, token: String, isFirst: Boolean): UserApiKey {
        val id = "key_${UUID.randomUUID().toString().take(8)}"
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val entity = ApiKeyEntity(id = id, name = name.trim().ifEmpty { "Hugging Face Key" }, token = token.trim(), createdAt = dateStr, isDefault = isFirst)
        dao.insertApiKey(entity)
        return UserApiKey(id, entity.name, entity.token, entity.createdAt, isDefault = entity.isDefault)
    }

    suspend fun deleteApiKey(id: String) {
        dao.deleteApiKey(id)
    }

    suspend fun setDefaultApiKey(id: String) {
        dao.setDefaultApiKey(id)
    }

    suspend fun getDefaultKey(): UserApiKey? {
        val entity = dao.getDefaultApiKey() ?: return null
        return UserApiKey(id = entity.id, name = entity.name, token = entity.token, createdAt = entity.createdAt, isDefault = entity.isDefault)
    }
}

class MediaRepository(private val database: AppDatabase) {
    private val dao = database.generatedMediaDao()

    fun getAllMedia(): Flow<List<GeneratedMediaItem>> {
        return dao.getAllMediaFlow().map { list ->
            list.map {
                GeneratedMediaItem(
                    id = it.id,
                    type = try { GeneratedMediaType.valueOf(it.type) } catch (e: Exception) { GeneratedMediaType.OTHER },
                    url = it.url,
                    textContent = it.textContent,
                    title = it.title,
                    prompt = it.prompt,
                    spaceId = it.spaceId,
                    spaceTitle = it.spaceTitle,
                    spaceEmoji = it.spaceEmoji,
                    createdAt = it.createdAt,
                    endpoint = it.endpoint,
                    elapsedSeconds = it.elapsedSeconds
                )
            }
        }
    }

    suspend fun saveMedia(item: GeneratedMediaItem) {
        dao.insertMedia(
            GeneratedMediaEntity(
                id = item.id,
                type = item.type.name,
                url = item.url,
                textContent = item.textContent,
                title = item.title,
                prompt = item.prompt,
                spaceId = item.spaceId,
                spaceTitle = item.spaceTitle,
                spaceEmoji = item.spaceEmoji,
                createdAt = item.createdAt,
                endpoint = item.endpoint,
                elapsedSeconds = item.elapsedSeconds
            )
        )
    }

    suspend fun deleteMedia(id: String) {
        dao.deleteMediaById(id)
    }

    suspend fun clearAll() {
        dao.clearAllMedia()
    }
}
