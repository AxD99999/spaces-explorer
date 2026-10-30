package com.hfspaces.explorer.data.repository

import com.hfspaces.explorer.data.gradio.SpaceApiSpec
import com.hfspaces.explorer.data.gradio.toEndpoints
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.data.remote.GradioClient
import com.hfspaces.explorer.data.remote.HfHubClient
import com.hfspaces.explorer.data.remote.HubSort
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.data.remote.SpacesPageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class SpacesRepository(
    private val hub: HfHubClient,
    private val gradio: GradioClient,
    private val cacheDir: File,
    private val hostResolver: (HfSpace) -> HttpUrl? = SpaceIds::resolveHost
) {
    /** Spaces seen in listings, so detail screens open instantly and survive the list changing. */
    private val known = ConcurrentHashMap<String, HfSpace>()

    /** Recently read API descriptions; details and runner screens open back to back. */
    private val specs = ConcurrentHashMap<String, Pair<Long, SpaceApiSpec>>()
    private val cacheFile get() = File(cacheDir, "spaces_first_page.json")

    suspend fun fetchPage(
        page: Int,
        search: String?,
        sort: HubSort = HubSort.TRENDING,
        sdk: String? = null
    ): SpacesPageResult {
        val result = hub.fetchSpacesPage(page, search, sort, sdk)
        remember(result.spaces)
        if (page == 0 && search.isNullOrBlank() && sort == HubSort.TRENDING && sdk == null) writeCache(result.spaces)
        return result
    }

    suspend fun fetchRuntime(spaceId: String, token: String?) = hub.fetchRuntime(spaceId, token)

    /**
     * Wakes a sleeping Space: any request to its host makes the Hub start it. The response
     * itself doesn't matter, so failures are ignored; callers poll [fetchRuntime] for progress.
     */
    suspend fun poke(space: HfSpace, token: String?) {
        val host = hostResolver(space) ?: return
        runCatching { gradio.ping(host, token) }
    }

    /** Forgets a cached API description, e.g. after the Space restarted. */
    fun invalidateSpec(spaceId: String) {
        specs.remove(spaceId)
    }

    suspend fun semanticSearch(category: String?, query: String?): List<HfSpace> =
        hub.semanticSearch(category, query).also(::remember)

    /** The last successfully loaded trending page, for offline starts. */
    suspend fun cachedFirstPage(): List<HfSpace>? = withContext(Dispatchers.IO) {
        runCatching {
            cacheFile.takeIf { it.exists() }?.readText()
                ?.let { AppJson.decodeFromString(ListSerializer(HfSpace.serializer()), it) }
        }.getOrNull()?.also(::remember)
    }

    fun knownSpace(spaceId: String): HfSpace? = known[spaceId]

    suspend fun fetchSpace(spaceId: String, token: String?): HfSpace =
        hub.fetchSpace(spaceId, token).also { known[it.id] = merge(known[it.id], it) }

    /**
     * Resolves where the Space is hosted and reads its Gradio API description. Listing entries
     * often lack the host, so this falls back to the Space's own metadata.
     */
    suspend fun discoverApi(space: HfSpace, token: String?, maxAgeMillis: Long = SPEC_TTL_MS): SpaceApiSpec {
        SpaceIds.requireValidSpaceId(space.id)
        specs[space.id]?.let { (at, spec) -> if (System.currentTimeMillis() - at <= maxAgeMillis) return spec }
        val host = (if (space.host != null || space.subdomain != null) hostResolver(space) else null)
            ?: runCatching { hostResolver(fetchSpace(space.id, token)) }.getOrNull()
            ?: hostResolver(space)
            ?: throw IOException("Couldn't work out where ${space.id} is hosted")
        val discovered = gradio.discover(host, token)
        return SpaceApiSpec(
            spaceId = space.id,
            host = host,
            apiPrefix = discovered.apiPrefix,
            endpoints = discovered.info.toEndpoints()
        ).also { specs[space.id] = System.currentTimeMillis() to it }
    }

    private companion object {
        const val SPEC_TTL_MS = 5 * 60 * 1000L
    }

    private fun remember(spaces: List<HfSpace>) {
        spaces.forEach { known[it.id] = merge(known[it.id], it) }
    }

    /** Listing entries lack `host`/`subdomain`; keep them once metadata has supplied them. */
    private fun merge(old: HfSpace?, new: HfSpace): HfSpace =
        if (old == null) new else new.copy(host = new.host ?: old.host, subdomain = new.subdomain ?: old.subdomain)

    private suspend fun writeCache(spaces: List<HfSpace>) = withContext(Dispatchers.IO) {
        runCatching {
            cacheFile.writeText(AppJson.encodeToString(ListSerializer(HfSpace.serializer()), spaces))
        }
    }
}
