package com.hfspaces.explorer.data.local.dao

import androidx.room.*
import com.hfspaces.explorer.data.local.entity.ApiKeyEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.local.entity.GeneratedMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite_spaces ORDER BY savedAt DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteSpaceEntity>>

    @Query("SELECT * FROM favorite_spaces ORDER BY savedAt DESC")
    suspend fun getAllFavorites(): List<FavoriteSpaceEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_spaces WHERE id = :spaceId)")
    fun isFavoriteFlow(spaceId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_spaces WHERE id = :spaceId)")
    suspend fun isFavorite(spaceId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteSpaceEntity)

    @Query("DELETE FROM favorite_spaces WHERE id = :spaceId")
    suspend fun deleteFavoriteById(spaceId: String)
}

@Dao
interface ApiKeysDao {
    @Query("SELECT * FROM api_keys ORDER BY isDefault DESC, createdAt DESC")
    fun getAllApiKeysFlow(): Flow<List<ApiKeyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKey(id: String)

    @Query("UPDATE api_keys SET isDefault = (id = :defaultId)")
    suspend fun setDefaultApiKey(defaultId: String)

    @Query("SELECT * FROM api_keys WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultApiKey(): ApiKeyEntity?
}

@Dao
interface GeneratedMediaDao {
    @Query("SELECT * FROM generated_media ORDER BY createdAt DESC")
    fun getAllMediaFlow(): Flow<List<GeneratedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(item: GeneratedMediaEntity)

    @Query("DELETE FROM generated_media WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM generated_media")
    suspend fun clearAllMedia()
}
