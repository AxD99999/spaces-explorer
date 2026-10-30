package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.data.model.ALL
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.HfSpaceRuntime
import com.hfspaces.explorer.data.model.HfSpacesPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
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
    val isPro: Boolean = false,
    val orgs: List<WhoAmIOrg> = emptyList()
)

@Serializable
data class WhoAmIOrg(val name: String? = null)

/** Server-side orderings supported by the Spaces listing. */
enum class HubSort(val param: String?) {
    TRENDING(null),
    LIKES("likes"),
    CREATED("created"),
    MODIFIED("modified")
}

enum class LogKind(val path: String) {
    BUILD("build"),
    RUN("run")
}

@Serializable
private data class LikedRepo(val repo: LikedRepoRef? = null)

@Serializable
private data class LikedRepoRef(val name: String? = null, val type: String? = null)

@Serializable
private data class LogLine(val data: String? = null, val timestamp: String? = null)

/** Client for the Hugging Face Hub (listing, search and metadata). */
class HfHubClient(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://huggingface.co".toHttpUrl()
) {
    suspend fun fetchSpacesPage(
        page: Int,
        search: String?,
        sort: HubSort = HubSort.TRENDING,
        sdk: String? = null
    ): SpacesPageResult {
        val url = baseUrl.newBuilder()
            .addPathSegment("spaces-json")
            .addQueryParameter("p", page.toString())
            .addQueryParameter("includeNonRunning", "true")
            .addQueryParameter("withCount", "false")
            .apply {
                search?.trim()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("search", it) }
                sort.param?.let { addQueryParameter("sort", it) }
                sdk?.takeIf { it.isNotBlank() && it != ALL }?.let { addQueryParameter("sdk", it) }
            }
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

    /** Current runtime (stage, hardware) of a Space; cheap enough to poll while it wakes. */
    suspend fun fetchRuntime(spaceId: String, token: String?): HfSpaceRuntime =
        client.execute(jsonRequest(spaceUrl(spaceId, "runtime"), token)) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "Couldn't read the Space's status (HTTP ${response.code})")
            decodeOrThrow<HfSpaceRuntime>(response.body?.string().orEmpty(), "Space status")
        }

    /** Ids of the Spaces a user has liked (likes are public). */
    suspend fun likedSpaceIds(username: String): List<String> {
        require(USERNAME.matches(username)) { "\"$username\" isn't a valid Hugging Face username" }
        val url = baseUrl.newBuilder().addPathSegments("api/users").addPathSegment(username).addPathSegment("likes").build()
        return client.execute(jsonRequest(url)) { response ->
            if (response.code == 404) throw HttpStatusException(404, "User \"$username\" was not found")
            if (!response.isSuccessful) throw HttpStatusException(response.code, "Couldn't read likes (HTTP ${response.code})")
            decodeOrThrow<List<LikedRepo>>(response.body?.string().orEmpty(), "likes")
                .mapNotNull { it.repo?.takeIf { repo -> repo.type == "space" }?.name }
                .filter(SpaceIds::isValidSpaceId)
        }
    }

    /** Spaces owned by a user or organisation; private ones need a token with access. */
    suspend fun spacesByAuthor(author: String, token: String?): List<HfSpace> {
        require(USERNAME.matches(author)) { "Invalid author \"$author\"" }
        val url = baseUrl.newBuilder()
            .addPathSegments("api/spaces")
            .addQueryParameter("author", author)
            .addQueryParameter("limit", "100")
            .addQueryParameter("full", "true")
            .build()
        return client.execute(jsonRequest(url, token)) { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, "Couldn't list $author's Spaces (HTTP ${response.code})")
            decodeOrThrow<List<HfSpace>>(response.body?.string().orEmpty(), "Space list").filter { SpaceIds.isValidSpaceId(it.id) }
        }
    }

    /** Restarts a Space you own; [factory] also rebuilds it from scratch. */
    suspend fun restartSpace(spaceId: String, token: String, factory: Boolean) {
        val url = spaceUrl(spaceId, "restart").let { if (factory) it.newBuilder().addQueryParameter("factory", "true").build() else it }
        postOwnerAction(url, token, "{}", "restart")
    }

    suspend fun pauseSpace(spaceId: String, token: String) = postOwnerAction(spaceUrl(spaceId, "pause"), token, "{}", "pause")

    /** Requests different hardware; paid tiers bill the owner's account. */
    suspend fun requestHardware(spaceId: String, token: String, flavor: String) {
        require(HARDWARE.matches(flavor)) { "Invalid hardware \"$flavor\"" }
        postOwnerAction(spaceUrl(spaceId, "hardware"), token, """{"flavor":"$flavor"}""", "change hardware")
    }

    /** Streams build or run logs line by line until cancelled or the stream ends. */
    suspend fun streamLogs(spaceId: String, token: String, kind: LogKind, onLine: (String) -> Unit) {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("api/spaces").addSpaceId(spaceId).addPathSegment("logs").addPathSegment(kind.path).build())
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer $token")
            .build()
        client.execute(request) { response ->
            if (response.code == 401 || response.code == 403) throw HttpStatusException(response.code, "Only the Space's owners can read its logs")
            if (!response.isSuccessful) throw HttpStatusException(response.code, "Couldn't open logs (HTTP ${response.code})")
            val source = response.body?.source() ?: return@execute
            val parser = com.hfspaces.explorer.data.gradio.SseParser()
            while (true) {
                val line = source.readUtf8Line() ?: break
                val event = parser.feed(line) ?: continue
                val text = runCatching { AppJson.decodeFromString<LogLine>(event.data).data }.getOrNull() ?: event.data
                onLine(text.trimEnd('\n'))
            }
        }
    }

    private suspend fun postOwnerAction(url: HttpUrl, token: String, json: String, what: String) {
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .build()
        client.execute(request) { response ->
            when {
                response.code == 401 || response.code == 403 ->
                    throw HttpStatusException(response.code, "Your token can't $what this Space. It needs write access and you must be an owner.")
                !response.isSuccessful ->
                    throw HttpStatusException(response.code, "Couldn't $what the Space (HTTP ${response.code})${response.errorSnippet().let { if (it.isEmpty()) "" else ": $it" }}")
            }
        }
    }

    private fun spaceUrl(spaceId: String, action: String? = null): HttpUrl =
        baseUrl.newBuilder().addPathSegments("api/spaces").addSpaceId(spaceId).apply { action?.let { addPathSegment(it) } }.build()

    private fun HttpUrl.Builder.addSpaceId(spaceId: String): HttpUrl.Builder {
        SpaceIds.requireValidSpaceId(spaceId)
        val (owner, name) = spaceId.split('/')
        return addPathSegment(owner).addPathSegment(name)
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
        private val USERNAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")
        private val HARDWARE = Regex("^[a-z0-9-]{2,40}$")
    }
}
