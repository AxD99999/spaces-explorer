package com.hfspaces.explorer.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.decodeFormValues
import com.hfspaces.explorer.data.gradio.encodeFormValues
import com.hfspaces.explorer.data.local.dao.CollectionDao
import com.hfspaces.explorer.data.local.dao.FavoritesDao
import com.hfspaces.explorer.data.local.dao.PresetDao
import com.hfspaces.explorer.data.local.entity.CollectionEntity
import com.hfspaces.explorer.data.local.entity.CollectionSpaceEntity
import com.hfspaces.explorer.data.local.entity.FavoriteSpaceEntity
import com.hfspaces.explorer.data.local.entity.PresetEntity
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.data.remote.SpaceIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.UUID

/**
 * What goes into a backup file: the things a user builds up by hand. Tokens are left out on
 * purpose (they are tied to this device's Keystore and shouldn't sit in a file), and so are runs
 * and saved outputs, which are large and easy to recreate.
 */
@Serializable
data class BackupFile(
    val app: String = "",
    val version: Int = 0,
    val exportedAt: Long = 0,
    val favourites: List<BackupFavourite> = emptyList(),
    val collections: List<BackupCollection> = emptyList(),
    val presets: List<BackupPreset> = emptyList(),
    val settings: BackupSettings? = null
)

@Serializable
data class BackupFavourite(val space: HfSpace, val savedAt: Long = 0)

@Serializable
data class BackupCollection(val name: String, val spaces: List<String> = emptyList())

@Serializable
data class BackupPreset(
    val spaceId: String,
    val endpoint: String,
    val name: String,
    val values: Map<Int, FormValue> = emptyMap(),
    val createdAt: Long = 0
)

@Serializable
data class BackupSettings(
    val themeMode: String? = null,
    val dynamicColor: Boolean? = null,
    val notifyOnFinish: Boolean? = null,
    val showAdultSpaces: Boolean? = null
)

/** What an import added; things already present are not counted. */
data class ImportSummary(val favourites: Int, val collections: Int, val presets: Int) {
    val isEmpty: Boolean get() = favourites == 0 && collections == 0 && presets == 0
}

class BackupFormatException(message: String) : IOException(message)

/**
 * Exports and imports [BackupFile]s. System backups are switched off for this app, so this is how
 * favourites, collections and presets move to another phone.
 *
 * Importing only ever adds: nothing already on the device is changed or removed. The file is
 * treated as untrusted, since it may come from anywhere.
 */
class BackupManager(
    private val favorites: FavoritesDao,
    private val collections: CollectionDao,
    private val presets: PresetDao,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver
) {
    suspend fun export(now: Long = System.currentTimeMillis()): String {
        val members = collections.getMemberships().groupBy({ it.collectionId }, { it.spaceId })
        val current = settings.settings.value
        val backup = BackupFile(
            app = APP,
            version = VERSION,
            exportedAt = now,
            favourites = favorites.getAll().map { BackupFavourite(it.toHfSpace(), it.savedAt) },
            collections = collections.getAll().map { BackupCollection(it.name, members[it.id].orEmpty()) },
            presets = presets.getAll().mapNotNull { row ->
                decodeFormValues(row.valuesJson)?.let { BackupPreset(row.spaceId, row.endpoint, row.name, it, row.createdAt) }
            },
            settings = BackupSettings(current.themeMode.name, current.dynamicColor, current.notifyOnFinish, current.showAdultSpaces)
        )
        return AppJson.encodeToString(BackupFile.serializer(), backup)
    }

    suspend fun import(json: String): ImportSummary {
        val backup = try {
            AppJson.decodeFromString(BackupFile.serializer(), json)
        } catch (e: SerializationException) {
            throw BackupFormatException("This file isn't a Spaces Explorer backup")
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("This file isn't a Spaces Explorer backup")
        }
        if (backup.app != APP) throw BackupFormatException("This file isn't a Spaces Explorer backup")
        if (backup.version > VERSION) throw BackupFormatException("This backup was made by a newer version of the app")

        var addedFavourites = 0
        backup.favourites.asSequence().filter { SpaceIds.isValidSpaceId(it.space.id) }.take(MAX_FAVOURITES).forEach { entry ->
            if (favorites.get(entry.space.id) == null) {
                favorites.insert(FavoriteSpaceEntity.fromHfSpace(entry.space.sanitized(), entry.savedAt.takeIf { it > 0 } ?: System.currentTimeMillis()))
                addedFavourites++
            }
        }

        var addedCollections = 0
        val byName = collections.getAll().associate { it.name.lowercase() to it.id }.toMutableMap()
        backup.collections.take(MAX_COLLECTIONS).forEach { entry ->
            val name = entry.name.trim().take(MAX_NAME)
            if (name.isEmpty()) return@forEach
            val id = byName.getOrPut(name.lowercase()) {
                addedCollections++
                "col_${UUID.randomUUID()}".also { collections.insert(CollectionEntity(it, name, System.currentTimeMillis())) }
            }
            // Collections group favourites; a Space that isn't one would never show up in them.
            entry.spaces.asSequence().filter { SpaceIds.isValidSpaceId(it) }.take(MAX_FAVOURITES).forEach { spaceId ->
                if (favorites.get(spaceId) != null) collections.addSpace(CollectionSpaceEntity(id, spaceId))
            }
        }

        var addedPresets = 0
        val existing = presets.getAll().mapTo(HashSet()) { PresetKey(it.spaceId, it.endpoint, it.name, it.valuesJson) }
        backup.presets.asSequence()
            .filter { SpaceIds.isValidSpaceId(it.spaceId) && it.endpoint.length <= MAX_ENDPOINT && it.name.isNotBlank() }
            .take(MAX_PRESETS)
            .forEach { entry ->
                // Presets never hold files. One in a backup could point at any file the app can read,
                // which a later run would upload, so they are dropped rather than trusted.
                val values = encodeFormValues(entry.values.filterValues { it !is FormValue.Files })
                val name = entry.name.trim().take(MAX_PRESET_NAME)
                if (existing.add(PresetKey(entry.spaceId, entry.endpoint, name, values))) {
                    presets.insert(
                        PresetEntity(
                            id = "preset_${UUID.randomUUID()}",
                            spaceId = entry.spaceId,
                            endpoint = entry.endpoint,
                            name = name,
                            valuesJson = values,
                            createdAt = entry.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    )
                    addedPresets++
                }
            }

        backup.settings?.let { saved ->
            saved.themeMode?.let { mode -> runCatching { ThemeMode.valueOf(mode) }.getOrNull()?.let(settings::setThemeMode) }
            saved.dynamicColor?.let(settings::setDynamicColor)
            saved.notifyOnFinish?.let(settings::setNotifyOnFinish)
            saved.showAdultSpaces?.let(settings::setShowAdultSpaces)
        }
        return ImportSummary(addedFavourites, addedCollections, addedPresets)
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val json = export()
        (resolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn't write to that location")).use { it.write(json.toByteArray()) }
    }

    suspend fun importFrom(uri: Uri): ImportSummary {
        val json = withContext(Dispatchers.IO) {
            (resolver.openInputStream(uri) ?: throw IOException("Couldn't read that file")).use { input ->
                val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
                if (bytes.size > MAX_FILE_BYTES) throw BackupFormatException("This file is too large to be a backup")
                bytes.toString(Charsets.UTF_8)
            }
        }
        return import(json)
    }

    /**
     * Where a Space is hosted decides where a token is sent, so it is never taken from a file:
     * the host is looked up again from the Hub the next time the Space is opened.
     */
    private fun HfSpace.sanitized(): HfSpace =
        normalized().copy(host = null, subdomain = null, runtime = runtime?.copy(domains = emptyList()))

    private data class PresetKey(val spaceId: String, val endpoint: String, val name: String, val values: String)

    companion object {
        const val APP = "spaces-explorer"
        const val VERSION = 1
        private const val MAX_FILE_BYTES = 10 * 1024 * 1024
        private const val MAX_FAVOURITES = 5_000
        private const val MAX_COLLECTIONS = 200
        private const val MAX_PRESETS = 2_000
        private const val MAX_NAME = 40
        private const val MAX_PRESET_NAME = 60
        private const val MAX_ENDPOINT = 200

        fun suggestedFileName(now: Long = System.currentTimeMillis()): String =
            "spaces-explorer-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now)) + ".json"
    }
}

/** `InputStream.readNBytes(int)` needs API 33; this reads at most [limit] bytes on any version. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read == -1) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
