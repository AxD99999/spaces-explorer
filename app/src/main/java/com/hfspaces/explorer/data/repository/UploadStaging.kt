package com.hfspaces.explorer.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.hfspaces.explorer.data.gradio.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Copies picked files into app cache straight away. Read access granted by the system picker
 * only lasts while the picking screen is alive, but uploads may start (or retry) later.
 */
class UploadStaging(
    private val resolver: ContentResolver,
    private val stagingDir: File
) {
    suspend fun stage(uri: Uri): PickedFile = withContext(Dispatchers.IO) {
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

    /** Removes staged copies older than a day. */
    suspend fun prune(maxAgeMillis: Long = 24 * 60 * 60 * 1000L) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        stagingDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }
}
