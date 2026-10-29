package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.BuildConfig
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

const val USER_AGENT = "HF-Spaces-Explorer-Android/${BuildConfig.VERSION_NAME}"

fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    // Queue streams send heartbeats; a long silence means the connection is dead.
    .readTimeout(90, TimeUnit.SECONDS)
    .addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
    }
    .build()

/** An HTTP error with a message fit for the UI. */
class HttpStatusException(val code: Int, message: String) : IOException(message)

/**
 * Executes [request] on the IO dispatcher and hands the open response to [block].
 *
 * Unlike a bare `execute()`, cancelling the calling coroutine cancels the OkHttp call, which also
 * unblocks a [block] that is in the middle of reading a long event stream.
 */
suspend fun <T> OkHttpClient.execute(request: Request, block: (Response) -> T): T =
    withContext(Dispatchers.IO) {
        val call = newCall(request)
        val canceller = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            call.execute().use(block)
        } catch (e: IOException) {
            ensureActive()
            throw e
        } finally {
            canceller.cancel()
        }
    }

/** Reads a short snippet of an error body for diagnostics without loading huge HTML pages. */
fun Response.errorSnippet(): String =
    runCatching { peekBody(2048).string() }.getOrDefault("")
        .let { body -> if (body.trimStart().startsWith("<")) "" else body.trim().take(300) }
