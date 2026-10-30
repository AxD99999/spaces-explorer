package com.hfspaces.explorer.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hfspaces.explorer.data.local.dao.ApiKeysDao
import com.hfspaces.explorer.data.local.entity.PresetEntity
import com.hfspaces.explorer.data.local.entity.CollectionSpaceEntity
import com.hfspaces.explorer.data.local.entity.CollectionEntity
import com.hfspaces.explorer.data.local.dao.PresetDao
import com.hfspaces.explorer.data.local.dao.CollectionDao
import com.hfspaces.explorer.data.local.dao.FavoritesDao
import com.hfspaces.explorer.data.local.dao.GeneratedMediaDao
import com.hfspaces.explorer.data.local.dao.TaskDao
import com.hfspaces.explorer.data.local.entity.ApiKeyEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.local.entity.GeneratedMediaEntity
import com.hfspaces.explorer.data.local.entity.TaskEntity
import com.hfspaces.explorer.data.security.TokenCipher

@Database(
    entities = [
        FavoriteSpaceEntity::class,
        ApiKeyEntity::class,
        GeneratedMediaEntity::class,
        TaskEntity::class,
        PresetEntity::class,
        CollectionEntity::class,
        CollectionSpaceEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoritesDao(): FavoritesDao
    abstract fun apiKeysDao(): ApiKeysDao
    abstract fun generatedMediaDao(): GeneratedMediaDao
    abstract fun taskDao(): TaskDao
    abstract fun presetDao(): PresetDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "hf_spaces_database"

        fun build(context: Context, cipher: TokenCipher, name: String? = NAME): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
            }
            // No destructive fallback: a missing migration must fail loudly rather than wipe user data.
            return builder.addMigrations(migration1To2(cipher), MIGRATION_2_3).build()
        }
    }
}

/** v2: full Space JSON on favourites, local copies for media, task history, encrypted tokens. */
fun migration1To2(cipher: TokenCipher) = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `favorite_spaces` ADD COLUMN `spaceJson` TEXT")
        db.execSQL("ALTER TABLE `generated_media` ADD COLUMN `localPath` TEXT")
        db.execSQL("ALTER TABLE `generated_media` ADD COLUMN `mimeType` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_history` (`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, " +
                "`spaceJson` TEXT NOT NULL, `endpoint` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`startTime` INTEGER NOT NULL, `endTime` INTEGER, `error` TEXT, `resultJson` TEXT, " +
                "`savedMediaIds` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))"
        )

        val plaintext = mutableListOf<Pair<String, String>>()
        db.query("SELECT `id`, `token` FROM `api_keys`").use { cursor ->
            while (cursor.moveToNext()) {
                val token = cursor.getString(1)
                if (!cipher.isEncrypted(token)) plaintext += cursor.getString(0) to token
            }
        }
        plaintext.forEach { (id, token) ->
            val values = ContentValues().apply { put("token", cipher.encrypt(token)) }
            db.update("api_keys", SQLiteDatabase.CONFLICT_NONE, values, "id = ?", arrayOf(id))
        }
    }
}

/** v3: run inputs for "run again", input presets, favourite collections. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `task_history` ADD COLUMN `inputsJson` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `presets` (`id` TEXT NOT NULL, `spaceId` TEXT NOT NULL, `endpoint` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `valuesJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_presets_spaceId` ON `presets` (`spaceId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `collection_spaces` (`collectionId` TEXT NOT NULL, `spaceId` TEXT NOT NULL, " +
                "PRIMARY KEY(`collectionId`, `spaceId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_spaces_spaceId` ON `collection_spaces` (`spaceId`)")
    }
}
