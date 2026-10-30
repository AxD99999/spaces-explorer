package com.hfspaces.explorer.data.repository

import com.hfspaces.explorer.data.gradio.OutputItem
import com.hfspaces.explorer.data.local.dao.GeneratedMediaDao
import com.hfspaces.explorer.data.local.entity.GeneratedMediaEntity
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.remote.SpaceIds
import com.hfspaces.explorer.data.remote.execute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Saved outputs. Files are copied into app storage because Gradio deletes its temporary files,
 * which would otherwise leave the gallery full of dead links.
 */
class MediaRepository(
    private val dao: GeneratedMediaDao,
    private val client: OkHttpClient,
    private val mediaDir: File
) {
    fun observe(): Flow<List<GeneratedMediaItem>> = dao.observeAll()
        .map { rows -> rows.map { it.toModel() } }
        .flowOn(Dispatchers.IO)

    suspend fun saveOutputs(
        outputs: List<OutputItem>,
        space: HfSpace,
        endpoint: String,
        prompt: String?,
        elapsedSeconds: Double,
        token: String?
    ): List<String> {
        val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return outputs.map { output ->
            val id = "media_${UUID.randomUUID()}"
            val entity = GeneratedMediaEntity(
                id = id,
                type = output.type.name,
                url = output.url,
                textContent = output.text,
                title = prompt?.take(60) ?: output.label ?: output.fileName ?: "${space.displayTitle} output",
                prompt = prompt,
                spaceId = space.id,
                spaceTitle = space.displayTitle,
                spaceEmoji = space.displayEmoji,
                createdAt = createdAt,
                endpoint = endpoint,
                elapsedSeconds = elapsedSeconds,
                mimeType = output.mimeType
            )
            dao.insert(entity)
            if (output.url != null) {
                runCatching { download(id, output, token) }.getOrNull()?.let { file ->
                    dao.insert(entity.copy(localPath = file.absolutePath))
                }
            }
            id
        }
    }

    suspend fun latest(): GeneratedMediaItem? = dao.latest()?.toModel()

    suspend fun get(id: String): GeneratedMediaItem? = dao.get(id)?.toModel()

    /** Local copy of the first image among [ids], for notification thumbnails. */
    suspend fun firstImagePath(ids: List<String>): String? =
        if (ids.isEmpty()) null
        else dao.getAll(ids).firstOrNull { it.type == GeneratedMediaType.IMAGE.name && it.localPath != null }?.localPath

    suspend fun delete(id: String) {
        dao.get(id)?.localPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
        dao.delete(id)
    }

    suspend fun clear() {
        dao.clear()
        withContext(Dispatchers.IO) { mediaDir.listFiles()?.forEach { it.delete() } }
    }

    private suspend fun download(id: String, output: OutputItem, token: String?): File {
        val url = output.url ?: throw IOException("No URL")
        mediaDir.mkdirs()
        val target = File(mediaDir, id + extensionFor(output))
        if (url.startsWith("data:")) {
            withContext(Dispatchers.IO) {
                val base64 = url.substringAfter("base64,", missingDelimiterValue = "")
                if (base64.isEmpty()) throw IOException("Unsupported data URL")
                target.writeBytes(Base64.getMimeDecoder().decode(base64))
            }
            return target
        }
        val httpUrl = url.toHttpUrlOrNull()?.takeIf { it.scheme == "https" } ?: throw IOException("Refusing non-https URL")
        val request = Request.Builder().url(httpUrl).apply {
            // Private Spaces need the token for their files; never send it anywhere else.
            if (!token.isNullOrBlank() && SpaceIds.isHuggingFaceHost(httpUrl)) header("Authorization", "Bearer $token")
        }.build()
        client.execute(request) { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) throw IOException("File too large to keep")
            val tmp = File(target.path + ".part")
            tmp.outputStream().use { out ->
                val input = body.byteStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_DOWNLOAD_BYTES) {
                        out.close()
                        tmp.delete()
                        throw IOException("File too large to keep")
                    }
                    out.write(buffer, 0, read)
                }
            }
            if (!tmp.renameTo(target)) throw IOException("Couldn't save file")
        }
        return target
    }

    private fun extensionFor(output: OutputItem): String {
        val fromName = (output.fileName ?: output.url.orEmpty().substringBefore('?').substringAfterLast('/'))
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        val fromMime = when (output.mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "audio/mpeg" -> "mp3"
            "audio/wav", "audio/x-wav" -> "wav"
            "video/mp4" -> "mp4"
            else -> null
        }
        return (fromName ?: fromMime)?.let { ".$it" } ?: ""
    }

    private fun GeneratedMediaEntity.toModel() = GeneratedMediaItem(
        id = id,
        type = runCatching { GeneratedMediaType.valueOf(type) }.getOrDefault(GeneratedMediaType.OTHER),
        url = url,
        localPath = localPath?.takeIf { File(it).exists() },
        mimeType = mimeType,
        textContent = textContent,
        title = title,
        prompt = prompt,
        spaceId = spaceId,
        spaceTitle = spaceTitle,
        spaceEmoji = spaceEmoji,
        createdAt = createdAt,
        endpoint = endpoint,
        elapsedSeconds = elapsedSeconds
    )

    companion object {
        const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024
    }
}
