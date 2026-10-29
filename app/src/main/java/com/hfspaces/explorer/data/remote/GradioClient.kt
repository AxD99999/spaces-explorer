package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.data.gradio.GradioApiInfo
import com.hfspaces.explorer.data.gradio.SseEvent
import com.hfspaces.explorer.data.gradio.SseParser
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream

/** The Space ran and reported a failure (as opposed to a network or protocol problem). */
class SpaceExecutionException(message: String) : IOException(message)

/** Progress reported while a call is queued or generating. */
sealed interface CallUpdate {
    data object Queued : CallUpdate
    data class Generating(val partial: JsonElement?) : CallUpdate
}

data class DiscoveredApi(val info: GradioApiInfo, val apiPrefix: String)

/** A file to upload; [open] is called once, on the IO dispatcher. */
class UploadSource(
    val name: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val open: () -> InputStream
)

/**
 * Talks to a Gradio app over its HTTP API.
 *
 * Gradio 5+ serves the API under `/gradio_api`; Gradio 4 serves it at the root. The prefix is
 * discovered once from `/info` and then reused for calls and uploads.
 */
class GradioClient(private val client: OkHttpClient) {

    suspend fun discover(host: HttpUrl, token: String?): DiscoveredApi {
        var lastError: IOException? = null
        for (prefix in API_PREFIXES) {
            val request = Request.Builder()
                .url(url(host, prefix, "info"))
                .header("Accept", "application/json")
                .auth(host, token)
                .build()
            try {
                val info = client.execute(request) { response ->
                    when {
                        response.code == 404 -> null
                        response.code == 401 || response.code == 403 ->
                            throw HttpStatusException(response.code, "The Space refused access. A Hugging Face token with access may be required.")
                        !response.isSuccessful ->
                            throw HttpStatusException(response.code, "The Space returned HTTP ${response.code}. It may be starting up or broken.")
                        else -> try {
                            AppJson.decodeFromString<GradioApiInfo>(response.body?.string().orEmpty())
                        } catch (e: SerializationException) {
                            throw IOException("This Space did not return a Gradio API description", e)
                        } catch (e: IllegalArgumentException) {
                            throw IOException("This Space did not return a Gradio API description", e)
                        }
                    }
                }
                if (info != null) return DiscoveredApi(info, prefix)
            } catch (e: HttpStatusException) {
                throw e
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("This Space does not expose a Gradio API")
    }

    /** Uploads files and returns their server-side paths, in order. */
    suspend fun upload(host: HttpUrl, apiPrefix: String, files: List<UploadSource>, token: String?): List<String> {
        if (files.isEmpty()) return emptyList()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            files.forEach { file -> addFormDataPart("files", file.name, file.asRequestBody()) }
        }.build()
        val request = Request.Builder()
            .url(url(host, apiPrefix, "upload"))
            .post(body)
            .auth(host, token)
            .build()
        return client.execute(request) { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "Uploading files to the Space failed (HTTP ${response.code})")
            }
            val paths = runCatching { AppJson.parseToJsonElement(response.body?.string().orEmpty()) as JsonArray }
                .getOrElse { throw IOException("The Space returned an unexpected upload response", it) }
                .map { it.jsonPrimitive.content }
            if (paths.size != files.size) throw IOException("The Space accepted ${paths.size} of ${files.size} files")
            paths
        }
    }

    /**
     * Calls [endpoint] with [data] through the queue (`POST /call/<api>` then stream
     * `GET /call/<api>/<event_id>`) and returns the final output array.
     *
     * Errors reported by the Space surface as [SpaceExecutionException] and are never retried
     * against another route, so the real cause reaches the user.
     */
    suspend fun predict(
        host: HttpUrl,
        apiPrefix: String,
        endpoint: String,
        data: JsonArray,
        token: String?,
        onUpdate: (CallUpdate) -> Unit = {}
    ): JsonElement {
        val apiName = endpoint.trim('/')
        require(apiName.isNotEmpty() && API_NAME.matches(apiName)) { "Invalid endpoint name: $endpoint" }

        val body = buildJsonObject { put("data", data) }.toString()
            .toRequestBody("application/json".toMediaType())
        val submit = Request.Builder()
            .url(url(host, apiPrefix, "call", apiName))
            .post(body)
            .auth(host, token)
            .build()

        val eventId = client.execute(submit) { response ->
            when {
                response.code == 404 -> null
                response.code == 401 || response.code == 403 ->
                    throw HttpStatusException(response.code, "The Space refused the request. A Hugging Face token with access may be required.")
                response.code == 422 ->
                    throw SpaceExecutionException("The Space rejected the inputs: ${response.errorSnippet().ifEmpty { "HTTP 422" }}")
                !response.isSuccessful ->
                    throw HttpStatusException(response.code, "The Space returned HTTP ${response.code}${response.errorSnippet().let { if (it.isEmpty()) "" else ": $it" }}")
                else -> runCatching {
                    (AppJson.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject)["event_id"]
                        ?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: throw IOException("The Space did not return an event id")
            }
        }

        // Pre-queue Gradio (3.x) has no /call route; fall back to its synchronous predict API.
        if (eventId == null) return legacyPredict(host, apiName, data, token)

        onUpdate(CallUpdate.Queued)
        val stream = Request.Builder()
            .url(url(host, apiPrefix, "call", apiName, eventId))
            .header("Accept", "text/event-stream")
            .auth(host, token)
            .build()

        return client.execute(stream) { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, "Lost the result stream (HTTP ${response.code})")
            }
            val source = response.body?.source() ?: throw IOException("Empty result stream")
            val parser = SseParser()
            var outcome: JsonElement? = null
            while (outcome == null) {
                val line = source.readUtf8Line()
                val event = if (line == null) parser.finish() else parser.feed(line)
                if (event != null) outcome = handleEvent(event, onUpdate)
                if (line == null) break
            }
            outcome ?: throw IOException("The Space closed the connection before returning a result")
        }
    }

    /** Returns the output for terminal events, null to keep reading. */
    private fun handleEvent(event: SseEvent, onUpdate: (CallUpdate) -> Unit): JsonElement? {
        val parsed = runCatching { AppJson.parseToJsonElement(event.data) }.getOrNull()
        return when (event.event) {
            "complete" -> parsed ?: JsonPrimitive(event.data)
            "error" -> throw SpaceExecutionException(errorMessage(parsed, event.data))
            "generating" -> {
                onUpdate(CallUpdate.Generating(parsed))
                null
            }
            else -> null // heartbeat and unknown events
        }
    }

    private suspend fun legacyPredict(host: HttpUrl, apiName: String, data: JsonArray, token: String?): JsonElement {
        val body = buildJsonObject { put("data", data) }.toString().toRequestBody("application/json".toMediaType())
        for (route in listOf("run", "api")) {
            val request = Request.Builder()
                .url(url(host, "", route, apiName))
                .post(body)
                .auth(host, token)
                .build()
            val result = client.execute(request) { response ->
                when {
                    response.code == 404 -> null
                    !response.isSuccessful -> {
                        val details = response.errorSnippet()
                        throw SpaceExecutionException(
                            "The Space returned HTTP ${response.code}" + if (details.isEmpty()) "" else ": $details"
                        )
                    }
                    else -> {
                        val json = AppJson.parseToJsonElement(response.body?.string().orEmpty())
                        (json as? JsonObject)?.let { obj ->
                            obj["error"]?.let { if (it != JsonNull) throw SpaceExecutionException(errorMessage(it, it.toString())) }
                            obj["data"]
                        } ?: json
                    }
                }
            }
            if (result != null) return result
        }
        throw IOException("Endpoint /$apiName was not found on this Space")
    }

    private fun errorMessage(parsed: JsonElement?, raw: String): String {
        val message = when (parsed) {
            is JsonObject -> (parsed["error"] as? JsonPrimitive)?.contentOrNull ?: (parsed["message"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> parsed.contentOrNull
            else -> null
        }?.takeIf { it.isNotBlank() && it != "null" }
            ?: raw.takeIf { it.isNotBlank() && it != "null" }
        return message
            ?: "The Space reported an error without details. It may be out of GPU quota, overloaded, or hiding error messages."
    }

    private fun url(host: HttpUrl, prefix: String, vararg segments: String): HttpUrl =
        host.newBuilder().apply {
            prefix.trim('/').takeIf { it.isNotEmpty() }?.let { addPathSegments(it) }
            segments.forEach { addPathSegment(it) }
        }.build()

    /** Tokens only go to Hugging Face hosts, whatever URL a caller passes in. */
    private fun Request.Builder.auth(host: HttpUrl, token: String?): Request.Builder = apply {
        if (!token.isNullOrBlank() && SpaceIds.isHuggingFaceHost(host)) header("Authorization", "Bearer $token")
    }

    private fun UploadSource.asRequestBody(): RequestBody = object : RequestBody() {
        override fun contentType() = mimeType?.toMediaTypeOrNull() ?: "application/octet-stream".toMediaType()
        // Content-provider sizes are not always accurate, so stream without a fixed length.
        override fun contentLength() = -1L
        override fun writeTo(sink: BufferedSink) {
            open().source().use { sink.writeAll(it) }
        }
    }

    companion object {
        private val API_PREFIXES = listOf("/gradio_api", "")
        private val API_NAME = Regex("^[A-Za-z0-9_][A-Za-z0-9_.-]*$")
    }
}
