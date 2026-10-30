package com.hfspaces.explorer.data.repository

import android.content.SharedPreferences
import com.hfspaces.explorer.data.gradio.FormValue
import com.hfspaces.explorer.data.gradio.decodeFormValues
import com.hfspaces.explorer.data.gradio.encodeFormValues
import com.hfspaces.explorer.data.local.dao.CollectionDao
import com.hfspaces.explorer.data.local.dao.CollectionWithCount
import com.hfspaces.explorer.data.local.dao.PresetDao
import com.hfspaces.explorer.data.local.entity.CollectionEntity
import com.hfspaces.explorer.data.local.entity.CollectionSpaceEntity
import com.hfspaces.explorer.data.local.entity.PresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class InputPreset(
    val id: String,
    val name: String,
    val endpoint: String,
    val values: Map<Int, FormValue>
)

/** Named input sets per Space. Files are left out: staged copies don't last. */
class PresetsRepository(private val dao: PresetDao) {
    fun observe(spaceId: String): Flow<List<InputPreset>> = dao.observeForSpace(spaceId).map { rows ->
        rows.mapNotNull { row ->
            decodeFormValues(row.valuesJson)?.let { InputPreset(row.id, row.name, row.endpoint, it) }
        }
    }

    suspend fun save(spaceId: String, endpoint: String, name: String, values: Map<Int, FormValue>) {
        val storable = values.filterValues { it !is FormValue.Files }
        dao.insert(
            PresetEntity(
                id = "preset_${UUID.randomUUID()}",
                spaceId = spaceId,
                endpoint = endpoint,
                name = name.trim().ifEmpty { "Preset" }.take(60),
                valuesJson = encodeFormValues(storable),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: String) = dao.delete(id)
}

data class SpaceCollection(val id: String, val name: String, val count: Int)

/** User-made groups of favourites. */
class CollectionsRepository(private val dao: CollectionDao) {
    fun observe(): Flow<List<SpaceCollection>> =
        dao.observeWithCounts().map { rows -> rows.map { SpaceCollection(it.id, it.name, it.count) } }

    /** collectionId -> spaceIds */
    fun observeMembers(): Flow<Map<String, Set<String>>> = dao.observeMemberships().map { links ->
        links.groupBy({ it.collectionId }, { it.spaceId }).mapValues { it.value.toSet() }
    }

    suspend fun create(name: String): String {
        val id = "col_${UUID.randomUUID()}"
        dao.insert(CollectionEntity(id, name.trim().ifEmpty { "Collection" }.take(40), System.currentTimeMillis()))
        return id
    }

    suspend fun rename(id: String, name: String) = dao.rename(id, name.trim().ifEmpty { "Collection" }.take(40))

    suspend fun delete(id: String) {
        dao.deleteMemberships(id)
        dao.delete(id)
    }

    suspend fun setMember(collectionId: String, spaceId: String, member: Boolean) {
        if (member) dao.addSpace(CollectionSpaceEntity(collectionId, spaceId)) else dao.removeSpace(collectionId, spaceId)
    }

    suspend fun removeEverywhere(spaceId: String) = dao.removeSpaceEverywhere(spaceId)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val notifyOnFinish: Boolean = true,
    val askedNotificationPermission: Boolean = false,
    val showAdultSpaces: Boolean = false
)

/** Small user preferences kept in SharedPreferences. */
class SettingsRepository(private val prefs: SharedPreferences) {
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = edit { putString(KEY_THEME, mode.name) }
    fun setDynamicColor(enabled: Boolean) = edit { putBoolean(KEY_DYNAMIC, enabled) }
    fun setNotifyOnFinish(enabled: Boolean) = edit { putBoolean(KEY_NOTIFY, enabled) }
    fun markNotificationPermissionAsked() = edit { putBoolean(KEY_ASKED_NOTIFY, true) }
    fun setShowAdultSpaces(enabled: Boolean) = edit { putBoolean(KEY_ADULT, enabled) }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        _settings.value = read()
    }

    private fun read() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
        notifyOnFinish = prefs.getBoolean(KEY_NOTIFY, true),
        askedNotificationPermission = prefs.getBoolean(KEY_ASKED_NOTIFY, false),
        showAdultSpaces = prefs.getBoolean(KEY_ADULT, false)
    )

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_NOTIFY = "notify_on_finish"
        const val KEY_ASKED_NOTIFY = "asked_notification_permission"
        const val KEY_ADULT = "show_adult_spaces"
    }
}

/** The last few searches, newest first. */
class RecentSearches(private val prefs: SharedPreferences) {
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    fun add(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        write(listOf(q) + _items.value.filterNot { it.equals(q, ignoreCase = true) })
    }

    fun remove(query: String) = write(_items.value - query)

    fun clear() = write(emptyList())

    private fun write(list: List<String>) {
        val trimmed = list.take(MAX)
        prefs.edit().putString(KEY, trimmed.joinToString(SEPARATOR)).apply()
        _items.value = trimmed
    }

    private fun read(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY = "recent_searches"
        const val SEPARATOR = "\u001F"
        const val MAX = 8
    }
}
