package com.hfspaces.explorer.data.repository

import com.hfspaces.explorer.data.local.dao.ApiKeysDao
import com.hfspaces.explorer.data.local.dao.CollectionDao
import com.hfspaces.explorer.data.local.dao.FavoritesDao
import com.hfspaces.explorer.data.local.entity.ApiKeyEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.UserApiKey
import com.hfspaces.explorer.data.security.TokenCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class FavoritesRepository(
    private val dao: FavoritesDao,
    private val collections: CollectionDao? = null
) {
    fun observe(): Flow<List<HfSpace>> = dao.observeAll().map { rows -> rows.map { it.toHfSpace() } }

    /** Returns true if the Space is now a favourite. */
    suspend fun toggle(space: HfSpace): Boolean {
        val existing = dao.get(space.id)
        return if (existing != null) {
            dao.delete(space.id)
            collections?.removeSpaceEverywhere(space.id)
            false
        } else {
            dao.insert(FavoriteSpaceEntity.fromHfSpace(space))
            true
        }
    }

    suspend fun get(spaceId: String): HfSpace? = dao.get(spaceId)?.toHfSpace()

    /** Replaces a stored favourite with fresh metadata, keeping its position in the list. */
    suspend fun refreshIfFavorite(space: HfSpace) {
        val existing = dao.get(space.id) ?: return
        dao.insert(FavoriteSpaceEntity.fromHfSpace(space, savedAt = existing.savedAt))
    }

    suspend fun getAll(): List<HfSpace> = dao.getAll().map { it.toHfSpace() }

    /** Adds a Space unless already saved; returns true if it was new. */
    suspend fun addIfMissing(space: HfSpace): Boolean {
        if (dao.get(space.id) != null) return false
        dao.insert(FavoriteSpaceEntity.fromHfSpace(space))
        return true
    }
}

class ApiKeysRepository(
    private val dao: ApiKeysDao,
    private val cipher: TokenCipher
) {
    fun observe(): Flow<List<UserApiKey>> = dao.observeAll()
        .map { rows ->
            rows.map { row ->
                val token = cipher.decrypt(row.token)
                UserApiKey(
                    id = row.id,
                    name = row.name,
                    token = token.orEmpty(),
                    createdAt = row.createdAt,
                    isDefault = row.isDefault
                )
            }
        }
        .flowOn(Dispatchers.Default)

    suspend fun add(name: String, token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "Token is empty" }
        val isFirst = dao.count() == 0
        dao.insert(
            ApiKeyEntity(
                id = "key_${UUID.randomUUID()}",
                name = name.trim().ifEmpty { "Hugging Face token" },
                token = cipher.encrypt(trimmed),
                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                isDefault = isFirst
            )
        )
    }

    suspend fun delete(id: String) {
        dao.delete(id)
        dao.ensureDefault()
    }

    suspend fun setDefault(id: String) = dao.setDefault(id)
}
