package com.hfspaces.explorer.data.repository

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.hfspaces.explorer.data.model.GeneratedMediaItem
import com.hfspaces.explorer.data.model.GeneratedMediaType
import com.hfspaces.explorer.data.remote.execute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Copies saved outputs into the shared Pictures / Movies / Music folders. */
class MediaExporter(private val context: Context, private val client: OkHttpClient) {

    /** True when [item] can go to the gallery at all. */
    fun canExport(item: GeneratedMediaItem): Boolean =
        item.type in setOf(GeneratedMediaType.IMAGE, GeneratedMediaType.VIDEO, GeneratedMediaType.AUDIO) &&
            (item.localPath != null || item.url?.startsWith("https://") == true)

    /** Android 9 and older need the storage permission for this. */
    val needsLegacyPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Saves [item] and returns the folder it went to. */
    suspend fun saveToGallery(item: GeneratedMediaItem): String = withContext(Dispatchers.IO) {
        require(canExport(item)) { "This item can't be saved to the gallery" }
        val (collection, folder) = when (item.type) {
            GeneratedMediaType.VIDEO -> (if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else null) to Environment.DIRECTORY_MOVIES
            GeneratedMediaType.AUDIO -> (if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else null) to Environment.DIRECTORY_MUSIC
            else -> (if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else null) to Environment.DIRECTORY_PICTURES
        }
        val name = fileName(item)
        val mime = item.mimeType ?: guessMime(name, item.type)
        openSource(item).use { input ->
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$ALBUM")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(collection!!, values) ?: throw IOException("The gallery refused the file")
                try {
                    resolver.openOutputStream(uri)?.use { copy(input, it) } ?: throw IOException("Couldn't write to the gallery")
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(folder), ALBUM).apply { mkdirs() }
                val target = File(dir, name)
                target.outputStream().use { copy(input, it) }
                MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            }
        }
        "$folder/$ALBUM"
    }

    private suspend fun openSource(item: GeneratedMediaItem): InputStream {
        item.localPath?.let { path -> File(path).takeIf { it.exists() }?.let { return it.inputStream() } }
        val url = item.url?.toHttpUrlOrNull()?.takeIf { it.scheme == "https" } ?: throw IOException("The original file is no longer available")
        // Downloaded into memory-bounded temp file, then streamed.
        val tmp = File.createTempFile("export", null, context.cacheDir)
        client.execute(Request.Builder().url(url).build()) { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            response.body?.byteStream()?.use { body -> tmp.outputStream().use { copy(body, it) } } ?: throw IOException("Empty download")
        }
        return object : java.io.FilterInputStream(tmp.inputStream()) {
            override fun close() {
                super.close()
                tmp.delete()
            }
        }
    }

    private fun copy(input: InputStream, output: OutputStream) {
        input.copyTo(output, 64 * 1024)
    }

    private fun fileName(item: GeneratedMediaItem): String {
        val source = item.localPath ?: item.url.orEmpty().substringBefore('?')
        val ext = source.substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 }
            ?: when (item.type) {
                GeneratedMediaType.VIDEO -> "mp4"
                GeneratedMediaType.AUDIO -> "wav"
                else -> "png"
            }
        val base = item.title.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(40).ifEmpty { "output" }
        return "${base}_${System.currentTimeMillis()}.$ext"
    }

    private fun guessMime(name: String, type: GeneratedMediaType): String {
        val ext = name.substringAfterLast('.')
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (type) {
            GeneratedMediaType.VIDEO -> "video/mp4"
            GeneratedMediaType.AUDIO -> "audio/wav"
            else -> "image/png"
        }
    }

    private companion object {
        const val ALBUM = "HF Spaces"
    }
}

/** Builds a content:// share URI for a file in app storage. */
fun Context.shareUriFor(path: String): Uri =
    androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", File(path))
