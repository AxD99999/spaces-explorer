package com.hfspaces.explorer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.PickedFile
import com.hfspaces.explorer.data.gradio.encodeFormValues
import com.hfspaces.explorer.data.local.AppDatabase
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.HfSpaceDomain
import com.hfspaces.explorer.data.model.HfSpaceRuntime
import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.testing.FakeTokenCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val opened = mutableListOf<AppDatabase>()

    /** One "phone": its own database and settings. */
    private inner class Device(name: String) {
        val db = AppDatabase.build(context, FakeTokenCipher(), name = null).also { opened += it }
        val favourites = FavoritesRepository(db.favoritesDao(), db.collectionDao())
        val collections = CollectionsRepository(db.collectionDao())
        val presets = PresetsRepository(db.presetDao())
        val settings = SettingsRepository(context.getSharedPreferences("settings-$name", Context.MODE_PRIVATE))
        val backups = BackupManager(db.favoritesDao(), db.collectionDao(), db.presetDao(), settings, context.contentResolver)
    }

    @After fun tearDown() = opened.forEach { it.close() }

    private val hello = HfSpace(id = "gradio/hello_world", title = "Hello World", emoji = "👋", sdk = "gradio", likes = 5, host = "https://gradio-hello-world.hf.space")
    private val flux = HfSpace(id = "black-forest-labs/FLUX.1-dev", title = "FLUX.1 [dev]", sdk = "gradio")

    @Test
    fun `a backup moves favourites collections presets and settings to another device`() = runBlocking<Unit> {
        val old = Device("old")
        old.favourites.toggle(hello)
        old.favourites.toggle(flux)
        val images = old.collections.create("Image tools")
        old.collections.setMember(images, flux.id, true)
        old.collections.create("Empty")
        old.presets.save(hello.id, "/predict", "Greeting", mapOf(0 to FormValue.Text("Ada"), 1 to FormValue.Bool(true)))
        old.settings.setThemeMode(ThemeMode.DARK)
        old.settings.setShowAdultSpaces(true)

        val file = old.backups.export(now = 1234)
        assertFalse("tokens never go into a backup", file.contains("token", ignoreCase = true))

        val new = Device("new")
        assertEquals(ImportSummary(favourites = 2, collections = 2, presets = 1), new.backups.import(file))
        assertEquals(setOf("Hello World", "FLUX.1 [dev]"), new.favourites.getAll().map { it.displayTitle }.toSet())
        assertEquals(mapOf("Empty" to 0, "Image tools" to 1), new.collections.observe().first().associate { it.name to it.count })
        val preset = new.presets.observe(hello.id).first().single()
        assertEquals("Greeting", preset.name)
        assertEquals(mapOf(0 to FormValue.Text("Ada"), 1 to FormValue.Bool(true)), preset.values)
        assertEquals(ThemeMode.DARK, new.settings.settings.value.themeMode)
        assertTrue(new.settings.settings.value.showAdultSpaces)

        // Importing again adds nothing and duplicates nothing.
        assertTrue(new.backups.import(file).isEmpty)
        assertEquals(2, new.favourites.getAll().size)
        assertEquals(2, new.collections.observe().first().size)
        assertEquals(1, new.presets.observe(hello.id).first().size)
    }

    @Test
    fun `importing only adds to what is already there`() = runBlocking<Unit> {
        val source = Device("source")
        source.favourites.toggle(hello.copy(title = "Title from the backup"))
        source.favourites.toggle(flux)
        val shared = source.collections.create("mine")
        source.collections.setMember(shared, flux.id, true)

        val target = Device("target")
        target.favourites.toggle(hello)
        val existing = target.collections.create("Mine")

        assertEquals(ImportSummary(favourites = 1, collections = 0, presets = 0), target.backups.import(source.backups.export()))
        assertEquals("Hello World", target.favourites.get(hello.id)?.displayTitle)
        // Same name in another case is the same collection; it gained the imported member.
        assertEquals(setOf(flux.id), target.collections.observeMembers().first()[existing])
    }

    @Test
    fun `a file cannot choose where a space is hosted or make the app upload its own files`() = runBlocking<Unit> {
        val hostile = HfSpace(
            id = "trusted/space",
            host = "https://attacker.hf.space",
            subdomain = "attacker",
            runtime = HfSpaceRuntime(stage = "RUNNING", domains = listOf(HfSpaceDomain("attacker.hf.space", "READY")))
        )
        val secret = FormValue.Files(listOf(PickedFile("file:///data/data/app/databases/hf_spaces_database", "db", null, null)))
        val file = AppJson.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                app = BackupManager.APP,
                version = BackupManager.VERSION,
                favourites = listOf(BackupFavourite(hostile), BackupFavourite(HfSpace(id = "../../etc/passwd")), BackupFavourite(HfSpace(id = "no-slash"))),
                collections = listOf(BackupCollection("  ", listOf("trusted/space")), BackupCollection("x".repeat(500), listOf("trusted/space", "not/a favourite", "bad id"))),
                presets = listOf(
                    BackupPreset("trusted/space", "/predict", "Leak", mapOf(0 to FormValue.Text("hi"), 1 to secret)),
                    BackupPreset("bad id", "/predict", "Skipped"),
                    BackupPreset("trusted/space", "/predict", "   ")
                ),
                settings = BackupSettings(themeMode = "NEON")
            )
        )

        val device = Device("victim")
        assertEquals(ImportSummary(favourites = 1, collections = 1, presets = 1), device.backups.import(file))

        val imported = device.favourites.getAll().single()
        assertEquals("trusted/space", imported.id)
        assertNull(imported.host)
        assertNull(imported.subdomain)
        assertEquals("the host is worked out again from the id", "https://trusted-space.hf.space/", SpaceIds.resolveHost(imported).toString())

        val preset = device.presets.observe("trusted/space").first().single()
        assertEquals(mapOf<Int, FormValue>(0 to FormValue.Text("hi")), preset.values)
        assertFalse(encodeFormValues(preset.values).contains("file:"))

        val collection = device.collections.observe().first().single()
        assertEquals(40, collection.name.length)
        assertEquals(1, collection.count)
        assertEquals("an unknown theme is ignored", ThemeMode.SYSTEM, device.settings.settings.value.themeMode)
    }

    @Test
    fun `files that are not backups are refused`() = runBlocking<Unit> {
        val device = Device("refuse")
        suspend fun refusal(json: String) = runCatching { device.backups.import(json) }.exceptionOrNull() as? BackupFormatException

        assertTrue(refusal("not json at all")!!.message!!.contains("isn't a Spaces Explorer backup"))
        assertTrue(refusal("""{"spaces":[]}""")!!.message!!.contains("isn't a Spaces Explorer backup"))
        assertTrue(refusal("""{"app":"other-app","version":1}""")!!.message!!.contains("isn't a Spaces Explorer backup"))
        assertTrue(refusal("""{"app":"spaces-explorer","version":99}""")!!.message!!.contains("newer version"))
        assertTrue(refusal("""{"app":"spaces-explorer","version":1,"favourites":"oops"}""") != null)
        // Unknown extra fields from a later minor change are fine.
        assertTrue(device.backups.import("""{"app":"spaces-explorer","version":1,"somethingNew":true}""").isEmpty)
        assertTrue(device.favourites.getAll().isEmpty())
    }

    @Test
    fun `the suggested file name carries the date`() {
        assertTrue(BackupManager.suggestedFileName(0).matches(Regex("""spaces-explorer-backup-\d{4}-\d{2}-\d{2}\.json""")))
    }
}
