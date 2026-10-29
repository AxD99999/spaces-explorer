package com.hfspaces.explorer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.hfspaces.explorer.data.local.entity.ApiKeyEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.local.entity.GeneratedMediaEntity
import com.hfspaces.explorer.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite_spaces ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<FavoriteSpaceEntity>>

    @Query("SELECT * FROM favorite_spaces ORDER BY savedAt DESC")
    suspend fun getAll(): List<FavoriteSpaceEntity>

    @Query("SELECT * FROM favorite_spaces WHERE id = :spaceId")
    suspend fun get(spaceId: String): FavoriteSpaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteSpaceEntity)

    @Query("DELETE FROM favorite_spaces WHERE id = :spaceId")
    suspend fun delete(spaceId: String)
}

@Dao
interface ApiKeysDao {
    @Query("SELECT * FROM api_keys ORDER BY isDefault DESC, createdAt DESC")
    fun observeAll(): Flow<List<ApiKeyEntity>>

    @Query("SELECT COUNT(*) FROM api_keys")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE api_keys SET isDefault = (id = :defaultId)")
    suspend fun setDefault(defaultId: String)

    /** Keeps exactly one default after the current one is deleted. */
    @Query(
        "UPDATE api_keys SET isDefault = 1 WHERE id = (SELECT id FROM api_keys ORDER BY createdAt DESC LIMIT 1) " +
            "AND NOT EXISTS (SELECT 1 FROM api_keys WHERE isDefault = 1)"
    )
    suspend fun ensureDefault()
}

@Dao
interface GeneratedMediaDao {
    @Query("SELECT * FROM generated_media ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GeneratedMediaEntity>>

    @Query("SELECT * FROM generated_media")
    suspend fun getAll(): List<GeneratedMediaEntity>

    @Query("SELECT * FROM generated_media WHERE id = :id")
    suspend fun get(id: String): GeneratedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: GeneratedMediaEntity)

    @Query("DELETE FROM generated_media WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM generated_media")
    suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task_history ORDER BY startTime DESC")
    suspend fun getAll(): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM task_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM task_history WHERE status != 'RUNNING'")
    suspend fun deleteFinished()

    /** Tasks that were running when the process died can't be resumed. */
    @Query("UPDATE task_history SET status = 'ERROR', error = :reason, endTime = :now WHERE status = 'RUNNING'")
    suspend fun markInterrupted(reason: String, now: Long)

    /** Keeps the history bounded. */
    @Query("DELETE FROM task_history WHERE id NOT IN (SELECT id FROM task_history ORDER BY startTime DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
