package com.hfspaces.explorer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hfspaces.explorer.data.local.dao.ApiKeysDao
import com.hfspaces.explorer.data.local.dao.FavoritesDao
import com.hfspaces.explorer.data.local.dao.GeneratedMediaDao
import com.hfspaces.explorer.data.local.entity.ApiKeyEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.local.entity.GeneratedMediaEntity

@Database(
    entities = [
        FavoriteSpaceEntity::class,
        ApiKeyEntity::class,
        GeneratedMediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun apiKeysDao(): ApiKeysDao
    abstract fun generatedMediaDao(): GeneratedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hf_spaces_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
