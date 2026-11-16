package com.hfspaces.explorer.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.hfspaces.explorer.data.gradio.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Copies picked files into app cache straight away. Read access granted by the system picker
 * only lasts while the picking screen is alive, but uploads may start (or retry) later.
 *
 * It is also the gate for what can be uploaded at all. Files reach the app from outside (the
 * picker, shares from other apps), and an upload only ever reads from the staging folder, so
 * nothing can name one of the app's own files (its database, a saved output) and have it sent
 * to a Space.
 *
 * @param ownAuthority the app's FileProvider; content from it is the app's own data, not a pick.
 */
class UploadStaging(
    private val resolver: ContentResolver,
    private val stagingDir: File,
    private val ownAuthority: String? = null
) {
    suspend fun stage(uri: Uri): PickedFile = withContext(Dispatchers.IO) {
        when (uri.scheme) {
            // A camera capture, written straight into the staging folder. Any other path is refused.
            ContentResolver.SCHEME_FILE -> return@withContext adopt(File(uri.path.orEmpty()))
            ContentResolver.SCHEME_CONTENT -> if (uri.authority == ownAuthority) throw IOException("That file can't be attached")
            else -> throw IOException("That kind of file source isn't supported")
        }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }?.let { name = it }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }
            }
        }
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).ifEmpty { "upload" }
        stagingDir.mkdirs()
        val target = File(stagingDir, "${UUID.randomUUID()}_$safeName")
        val input = resolver.openInputStream(uri) ?: throw IOException("Couldn't read the selected file")
        input.use { source -> target.outputStream().use { source.copyTo(it) } }
        PickedFile(
            uri = Uri.fromFile(target).toString(),
            name = safeName,
            mimeType = resolver.getType(uri),
            sizeBytes = size ?: target.length()
        )
    }

    /** Opens a staged file for upload. Anything outside the staging folder is refused. */
    fun open(uri: String): InputStream {
        val parsed = uri.toUri()
        val file = File(parsed.path.orEmpty())
        if (parsed.scheme != ContentResolver.SCHEME_FILE || !isStaged(file)) throw FileNotFoundException("This file is no longer available; pick it again")
        return file.inputStream()
    }

    /** True for an existing file directly inside the staging folder, with links and `..` resolved. */
    fun isStaged(file: File): Boolean =
        runCatching { file.isFile && file.canonicalFile.parentFile == stagingDir.canonicalFile }.getOrDefault(false)

    /** Removes staged copies older than a day. */
    suspend fun prune(maxAgeMillis: Long = 24 * 60 * 60 * 1000L) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        stagingDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    private fun adopt(file: File): PickedFile {
        if (!isStaged(file) || file.length() == 0L) throw IOException("That file can't be attached")
        return PickedFile(
            uri = Uri.fromFile(file.canonicalFile).toString(),
            name = file.name,
            mimeType = MIME_BY_EXTENSION[file.extension.lowercase()],
            sizeBytes = file.length()
        )
    }

    private companion object {
        /** What the camera can produce here. */
        val MIME_BY_EXTENSION = mapOf("jpg" to "image/jpeg", "mp4" to "video/mp4")
    }
}
