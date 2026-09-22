package com.hfspaces.explorer.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hfspaces.explorer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class HfApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches paginated Hugging Face spaces using spaces-json
     */
    suspend fun fetchSpaces(page: Int = 0, search: String? = null, filter: String? = null): List<HfSpace> = withContext(Dispatchers.IO) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("huggingface.co")
            .addPathSegment("spaces-json")
            .addQueryParameter("p", page.toString())
            .addQueryParameter("includeNonRunning", "true")
            .addQueryParameter("withCount", "false")

        if (!filter.isNullOrBlank() && filter != "all") {
            urlBuilder.addQueryParameter("filter", filter)
        }
        if (!search.isNullOrBlank()) {
            urlBuilder.addQueryParameter("search", search.trim())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")
            .header("User-Agent", "HF-Spaces-Android-Native/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Hugging Face API returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty response from Hugging Face")
            val jsonObject = gson.fromJson(body, JsonObject::class.java)

            if (jsonObject.has("spaces")) {
                val spacesArray = jsonObject.getAsJsonArray("spaces")
                val list = mutableListOf<HfSpace>()
                for (elem in spacesArray) {
                    try {
                        val space = gson.fromJson(elem, HfSpace::class.java)
                        list.add(space)
                    } catch (e: Exception) {
                        // Skip malformed individual items
                    }
                }
                list
            } else {
                emptyList()
            }
        }
    }

    /**
     * Fetches spaces using Hugging Face semantic search API
     */
    suspend fun fetchSemanticSearch(category: String? = null, query: String? = null): List<HfSpace> = withContext(Dispatchers.IO) {
        val urlBuilder = HttpUrl.Builder()
            .scheme("https")
            .host("huggingface.co")
            .addPathSegments("api/spaces/semantic-search")
            .addQueryParameter("includeNonRunning", "true")

        if (!category.isNullOrBlank() && category != "all") {
            urlBuilder.addQueryParameter("category", category)
        }
        if (!query.isNullOrBlank()) {
            urlBuilder.addQueryParameter("q", query.trim())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")
            .header("User-Agent", "HF-Spaces-Android-Native/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Semantic Search returned HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty semantic search response")
            val list = mutableListOf<HfSpace>()

            try {
                val array = gson.fromJson(body, JsonArray::class.java)
                for (elem in array) {
                    val space = gson.fromJson(elem, HfSpace::class.java)
                    list.add(space)
                }
            } catch (e: Exception) {
                val jsonObject = gson.fromJson(body, JsonObject::class.java)
                if (jsonObject.has("spaces")) {
                    for (elem in jsonObject.getAsJsonArray("spaces")) {
                        list.add(gson.fromJson(elem, HfSpace::class.java))
                    }
                }
            }
            list
        }
    }

    /**
     * Fetches space metadata and Gradio API endpoints specification
     */
    suspend fun fetchSpaceInfo(spaceId: String, token: String? = null): SpaceApiSpec = withContext(Dispatchers.IO) {
        // Step 1: Query Space metadata from HF Hub API
        val reqBuilder = Request.Builder()
            .url("https://huggingface.co/api/spaces/$spaceId")
            .header("Accept", "application/json")
            .header("User-Agent", "HF-Spaces-Android-Native/1.0")

        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $token")
        }

        var subdomain = spaceId.replace('/', '-').lowercase()
        var host = "https://$subdomain.hf.space"
        var sdk = "gradio"

        try {
            client.newCall(reqBuilder.build()).execute().use { res ->
                if (res.isSuccessful) {
                    val metaObj = gson.fromJson(res.body?.string(), JsonObject::class.java)
                    if (metaObj.has("subdomain") && !metaObj.get("subdomain").isJsonNull) {
                        subdomain = metaObj.get("subdomain").asString
                    }
                    if (metaObj.has("host") && !metaObj.get("host").isJsonNull) {
                        host = metaObj.get("host").asString
                    } else {
                        host = "https://$subdomain.hf.space"
                    }
                    if (metaObj.has("sdk") && !metaObj.get("sdk").isJsonNull) {
                        sdk = metaObj.get("sdk").asString
                    }
                }
            }
        } catch (e: Exception) {
            // Proceed with fallback host
        }

        // Step 2: Fetch Gradio API schema from host
        val endpoints = mutableListOf<SpaceEndpointSpec>()

        try {
            val infoReq = Request.Builder()
                .url("$host/gradio_api/info")
                .header("Accept", "application/json")
                .header("User-Agent", "HF-Spaces-Android-Native/1.0")

            if (!token.isNullOrBlank()) {
                infoReq.header("Authorization", "Bearer $token")
            }

            client.newCall(infoReq.build()).execute().use { infoRes ->
                if (infoRes.isSuccessful) {
                    val infoObj = gson.fromJson(infoRes.body?.string(), JsonObject::class.java)
                    if (infoObj.has("named_endpoints")) {
                        val named = infoObj.getAsJsonObject("named_endpoints")
                        for (entry in named.entrySet()) {
                            val epObj = entry.value.asJsonObject
                            val params = parseParams(epObj.getAsJsonArray("parameters"))
                            val returns = parseReturns(epObj.getAsJsonArray("returns"))
                            endpoints.add(SpaceEndpointSpec(name = entry.key, parameters = params, returns = returns))
                        }
                    } else if (infoObj.has("unnamed_endpoints")) {
                        val unnamed = infoObj.getAsJsonObject("unnamed_endpoints")
                        for (entry in unnamed.entrySet()) {
                            val epObj = entry.value.asJsonObject
                            val params = parseParams(epObj.getAsJsonArray("parameters"))
                            val returns = parseReturns(epObj.getAsJsonArray("returns"))
                            endpoints.add(SpaceEndpointSpec(name = "/${entry.key}", parameters = params, returns = returns))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If Gradio /gradio_api/info failed, fallback to default /predict endpoint
            endpoints.add(
                SpaceEndpointSpec(
                    name = "/predict",
                    parameters = listOf(
                        SpaceEndpointParam(label = "Prompt", parameter_name = "prompt", component = "textbox", example_input = "")
                    )
                )
            )
        }

        if (endpoints.isEmpty()) {
            endpoints.add(
                SpaceEndpointSpec(
                    name = "/predict",
                    parameters = listOf(
                        SpaceEndpointParam(label = "Prompt", parameter_name = "prompt", component = "textbox", example_input = "")
                    )
                )
            )
        }

        SpaceApiSpec(
            spaceId = spaceId,
            subdomain = subdomain,
            host = host,
            sdk = sdk,
            endpoints = endpoints
        )
    }

    private fun parseParams(array: JsonArray?): List<SpaceEndpointParam> {
        if (array == null) return emptyList()
        val list = mutableListOf<SpaceEndpointParam>()
        for (item in array) {
            val obj = item.asJsonObject
            list.add(
                SpaceEndpointParam(
                    label = if (obj.has("label") && !obj.get("label").isJsonNull) obj.get("label").asString else "Parameter",
                    parameter_name = if (obj.has("parameter_name") && !obj.get("parameter_name").isJsonNull) obj.get("parameter_name").asString else "param",
                    parameter_has_default = obj.has("parameter_has_default") && obj.get("parameter_has_default").asBoolean,
                    parameter_default = if (obj.has("parameter_default") && !obj.get("parameter_default").isJsonNull) obj.get("parameter_default").toString() else null,
                    component = if (obj.has("component") && !obj.get("component").isJsonNull) obj.get("component").asString else "textbox",
                    example_input = if (obj.has("example_input") && !obj.get("example_input").isJsonNull) obj.get("example_input").asString else null
                )
            )
        }
        return list
    }

    private fun parseReturns(array: JsonArray?): List<SpaceEndpointReturn> {
        if (array == null) return emptyList()
        val list = mutableListOf<SpaceEndpointReturn>()
        for (item in array) {
            val obj = item.asJsonObject
            list.add(
                SpaceEndpointReturn(
                    label = if (obj.has("label") && !obj.get("label").isJsonNull) obj.get("label").asString else "Output",
                    component = if (obj.has("component") && !obj.get("component").isJsonNull) obj.get("component").asString else "output"
                )
            )
        }
        return list
    }

    /**
     * Executes inference on a space endpoint (via Gradio Queue API with SSE, or classic predict fallback)
     */
    suspend fun runSpaceInference(
        subdomain: String,
        endpoint: String,
        payloadData: List<Any?>,
        token: String? = null
    ): Pair<Any?, String> = withContext(Dispatchers.IO) {
        val host = "https://$subdomain.hf.space"
        val epName = if (endpoint.startsWith("/")) endpoint else "/$endpoint"

        val reqBody = JsonObject()
        val dataArray = JsonArray()
        for (item in payloadData) {
            when (item) {
                is String -> dataArray.add(item)
                is Number -> dataArray.add(item)
                is Boolean -> dataArray.add(item)
                else -> dataArray.add(item?.toString() ?: "")
            }
        }
        reqBody.add("data", dataArray)

        val postReq = Request.Builder()
            .url("$host/gradio_api/call$epName")
            .post(reqBody.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("User-Agent", "HF-Spaces-Android-Native/1.0")

        if (!token.isNullOrBlank()) {
            postReq.header("Authorization", "Bearer $token")
        }

        try {
            client.newCall(postReq.build()).execute().use { callRes ->
                if (callRes.isSuccessful) {
                    val callData = gson.fromJson(callRes.body?.string(), JsonObject::class.java)
                    val eventId = if (callData.has("event_id")) callData.get("event_id").asString else null

                    if (!eventId.isNullOrBlank()) {
                        // Stream SSE for completion
                        val sseReq = Request.Builder()
                            .url("$host/gradio_api/call$epName/$eventId")
                            .header("Accept", "text/event-stream")
                            .header("User-Agent", "HF-Spaces-Android-Native/1.0")

                        if (!token.isNullOrBlank()) {
                            sseReq.header("Authorization", "Bearer $token")
                        }

                        client.newCall(sseReq.build()).execute().use { sseRes ->
                            if (sseRes.isSuccessful) {
                                val reader = BufferedReader(InputStreamReader(sseRes.body!!.byteStream()))
                                var line: String?
                                var resultData: Any? = null
                                var errorData: String? = null

                                while (reader.readLine().also { line = it } != null) {
                                    val l = line?.trim() ?: continue
                                    if (l.startsWith("event: complete")) {
                                        val next = reader.readLine()?.trim() ?: ""
                                        if (next.startsWith("data: ")) {
                                            resultData = next.substring(6)
                                            break
                                        }
                                    } else if (l.startsWith("event: error")) {
                                        val next = reader.readLine()?.trim() ?: ""
                                        if (next.startsWith("data: ")) {
                                            errorData = next.substring(6)
                                            break
                                        }
                                    }
                                }

                                if (errorData != null) {
                                    throw Exception("Model inference error: $errorData")
                                }
                                if (resultData != null) {
                                    return@withContext Pair(resultData, host)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Try classic /api/predict fallback
            val fallbackBody = JsonObject().apply {
                addProperty("fn_index", 0)
                add("data", dataArray)
            }
            val fbReq = Request.Builder()
                .url("$host/api/predict")
                .post(fallbackBody.toString().toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")

            if (!token.isNullOrBlank()) {
                fbReq.header("Authorization", "Bearer $token")
            }

            client.newCall(fbReq.build()).execute().use { fbRes ->
                if (fbRes.isSuccessful) {
                    val resStr = fbRes.body?.string() ?: ""
                    return@withContext Pair(resStr, host)
                } else {
                    throw Exception("Inference failed with HTTP ${fbRes.code}")
                }
            }
        }

        throw Exception("Failed to receive output from space model")
    }

    /**
     * Validates a Hugging Face User Access Token via whoami-v2
     */
    suspend fun validateHfToken(token: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://huggingface.co/api/whoami-v2")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("User-Agent", "HF-Spaces-Android-Native/1.0")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val userObj = gson.fromJson(response.body?.string(), JsonObject::class.java)
                    val name = if (userObj.has("name")) userObj.get("name").asString else "HF User"
                    Pair(true, name)
                } else {
                    Pair(false, null)
                }
            }
        } catch (e: Exception) {
            Pair(false, null)
        }
    }
}
