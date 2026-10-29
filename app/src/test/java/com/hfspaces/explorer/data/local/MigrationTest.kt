package com.hfspaces.explorer.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.hfspaces.explorer.data.repository.ApiKeysRepository
import com.hfspaces.explorer.data.repository.FavoritesRepository
import com.hfspaces.explorer.testing.FakeTokenCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cipher = FakeTokenCipher()

    /** Exactly what Room generated for schema v1 (exportSchema was off in v1). */
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `favorite_spaces` (`id` TEXT NOT NULL, `title` TEXT, `author` TEXT NOT NULL, `emoji` TEXT, `colorFrom` TEXT, `colorTo` TEXT, `sdk` TEXT, `stage` TEXT, `hardware` TEXT, `shortDescription` TEXT, `aiCategory` TEXT, `likes` INTEGER NOT NULL, `trendingScore` REAL NOT NULL, `tagsCsv` TEXT NOT NULL, `subdomain` TEXT, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `api_keys` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `token` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `lastUsed` TEXT, `isDefault` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `generated_media` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `url` TEXT, `textContent` TEXT, `title` TEXT NOT NULL, `prompt` TEXT, `spaceId` TEXT NOT NULL, `spaceTitle` TEXT NOT NULL, `spaceEmoji` TEXT, `createdAt` TEXT NOT NULL, `endpoint` TEXT, `elapsedSeconds` REAL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'bc2c99a9a11a572be54aad5d32c2ad36')"
    )

    @Test
    fun `v1 data survives the upgrade and tokens get encrypted`() = runBlocking<Unit> {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1Schema.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO favorite_spaces VALUES ('gradio/hello_world', 'Hello', 'gradio', '👋', NULL, NULL, 'gradio', 'RUNNING', 'cpu-basic', 'desc', NULL, 5, 1.5, 'gradio,demo', NULL, 100)"
            )
            db.execSQL("INSERT INTO api_keys VALUES ('key_1', 'Main', 'hf_plaintext_token', '2026-01-01 10:00', NULL, 1)")
            db.execSQL(
                "INSERT INTO generated_media VALUES ('m1', 'IMAGE', 'https://x.hf.space/file=a.png', NULL, 'cat', 'a cat', 'gradio/hello_world', 'Hello', '👋', '2026-01-01 10:00', '/predict', 2.5)"
            )
            db.version = 1
        }

        val database = AppDatabase.build(context, cipher, name)
        try {
            val favourite = FavoritesRepository(database.favoritesDao()).getAll().single()
            assertEquals("gradio/hello_world", favourite.id)
            assertEquals(listOf("gradio", "demo"), favourite.tags)
            assertEquals("cpu-basic", favourite.hardwareLabel)

            val stored = database.query("SELECT token FROM api_keys", null).use { it.moveToFirst(); it.getString(0) }
            assertTrue("token must be encrypted at rest: $stored", cipher.isEncrypted(stored))
            assertEquals("hf_plaintext_token", ApiKeysRepository(database.apiKeysDao(), cipher).observe().first().single().token)

            val media = database.generatedMediaDao().getAll().single()
            assertEquals("a cat", media.prompt)
            assertNull(media.localPath)
            assertTrue(database.taskDao().getAll().isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun `fresh install creates the current schema`() = runBlocking<Unit> {
        val database = AppDatabase.build(context, cipher, name = null)
        try {
            ApiKeysRepository(database.apiKeysDao(), cipher).apply {
                add("first", " hf_aaaaaaaaaaaaaaaaaaaa ")
                add("second", "hf_bbbbbbbbbbbbbbbbbbbb")
                val keys = observe().first()
                assertEquals("first key becomes default", listOf(true, false), keys.sortedBy { it.name }.map { it.isDefault })
                assertEquals("hf_aaaaaaaaaaaaaaaaaaaa", keys.first { it.name == "first" }.token)
                delete(keys.first { it.isDefault }.id)
                assertTrue("a default remains after deleting it", observe().first().single().isDefault)
            }
        } finally {
            database.close()
        }
    }
}
