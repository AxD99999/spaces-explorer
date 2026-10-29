package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.data.model.ALL
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.HfSpacesPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class SpacesPageResult(
    val spaces: List<HfSpace>,
    /** False once the Hub has nothing more for this query. */
    val hasMore: Boolean
)

@Serializable
data class WhoAmI(
    val name: String? = null,
    val fullname: String? = null,
    val type: String? = null,
    val isPro: Boolean = false
)

/** Client for the Hugging Face Hub (listing, search and metadata). */
class HfHubClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://huggingface.co".toHttpUrl()
) {
    suspend fun fetchSpacesPage(page: Int, search: String?): SpacesPageResult {
        val url = baseUrl.newBuilder()
            .addPathSegment("spaces-json")
            .addQueryParameter("p", page.toString())
            .addQueryParameter("includeNonRunning", "true")
            .addQueryParameter("withCount", "false")
            .apply { search?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("search", it) } }
            .build()
        return client.execute(jsonRequest(url)) { response ->
            // Past the last page the Hub answers with an HTML error page rather than an empty list.
            if (!response.isSuccessful) {
                if (page > 0 && response.code in 400..499) return@execute SpacesPageResult(emptyList(), hasMore = false)
                throw HttpStatusException(response.code, "Hugging Face returned HTTP ${response.code} while listing Spaces")
            }
            val body = response.body?.string().orEmpty()
            val parsed = decodeOrThrow<HfSpacesPage>(body, "Space list")
            val spaces = parsed.spaces.filter { SpaceIds.isValidSpaceId(it.id) }
            val perPage = parsed.numItemsPerPage ?: DEFAULT_PAGE_SIZE
            SpacesPageResult(spaces, hasMore = parsed.spaces.size >= perPage)
        }
    }

    /** Semantic search returns one ranked list, without paging. */
    suspend fun semanticSearch(category: String?, query: String?): List<HfSpace> {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/spaces/semantic-search")
            .addQueryParameter("includeNonRunning", "true")
            .apply {
                category?.takeIf { it.isNotBlank() && it != ALL }?.let { addQueryParameter("category", it) }
                query?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("q", it) }
            }
            .build()
        return client.execute(jsonRequest(url)) { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "Semantic search returned HTTP ${response.code}")
            }
            val element = decodeOrThrow<JsonElement>(response.body?.string().orEmpty(), "search results")
            val array = when (element) {
                is JsonArray -> element
                is JsonObject -> element["spaces"] as? JsonArray ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            array.mapNotNull { item -> runCatching { AppJson.decodeFromJsonElement<HfSpace>(item) }.getOrNull() }
                .filter { SpaceIds.isValidSpaceId(it.id) }
        }
    }

    suspend fun fetchSpace(spaceId: String, token: String?): HfSpace {
        SpaceIds.requireValidSpaceId(spaceId)
        val (owner, name) = spaceId.split('/')
        val url = baseUrl.newBuilder()
            .addPathSegments("api/spaces")
            .addPathSegment(owner)
            .addPathSegment(name)
            .build()
        return client.execute(jsonRequest(url, token)) { response ->
            when {
                response.code == 401 || response.code == 403 ->
                    throw HttpStatusException(response.code, "This Space is private or gated. Add a Hugging Face token with access to it.")
                response.code == 404 -> throw HttpStatusException(404, "Space \"$spaceId\" was not found")
                !response.isSuccessful -> throw HttpStatusException(response.code, "Hugging Face returned HTTP ${response.code} for $spaceId")
            }
            decodeOrThrow<HfSpace>(response.body?.string().orEmpty(), "Space metadata")
        }
    }

    /** Returns the account for [token], or null when the Hub rejects it. */
    suspend fun whoAmI(token: String): WhoAmI? {
        val url = baseUrl.newBuilder().addPathSegments("api/whoami-v2").build()
        return client.execute(jsonRequest(url, token)) { response ->
            when {
                response.code == 401 -> null
                !response.isSuccessful -> throw HttpStatusException(response.code, "Token check failed with HTTP ${response.code}")
                else -> decodeOrThrow<WhoAmI>(response.body?.string().orEmpty(), "account info")
            }
        }
    }

    private fun jsonRequest(url: HttpUrl, token: String? = null): Request =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()

    private inline fun <reified T> decodeOrThrow(body: String, what: String): T =
        try {
            AppJson.decodeFromString<T>(body)
        } catch (e: SerializationException) {
            throw java.io.IOException("Unexpected $what format from Hugging Face", e)
        } catch (e: IllegalArgumentException) {
            throw java.io.IOException("Unexpected $what format from Hugging Face", e)
        }

    companion object {
        const val DEFAULT_PAGE_SIZE = 24
    }
}
