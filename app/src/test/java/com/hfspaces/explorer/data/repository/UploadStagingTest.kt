package com.hfspaces.explorer.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UploadStagingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val stagingDir = File(context.cacheDir, "uploads")
    private val staging = UploadStaging(context.contentResolver, stagingDir, ownAuthority = "app.files")

    /** Stands in for the app's own data, which must never be attachable. */
    private val secret = File(context.filesDir, "secret.db")

    @Before
    fun setUp() {
        stagingDir.deleteRecursively()
        secret.writeText("private")
    }

    @Test
    fun `a picked file is copied into the staging folder and can be uploaded from there`() = runBlocking {
        val picked = Uri.parse("content://media.provider/images/42")
        shadowOf(context.contentResolver).registerInputStream(picked, "pixels".byteInputStream())

        val file = staging.stage(picked)
        assertTrue(file.uri, file.uri.startsWith("file:") && stagingDir.name in file.uri)
        assertEquals(6L, file.sizeBytes)
        assertEquals("pixels", staging.open(file.uri).use { it.readBytes().decodeToString() })
    }

    @Test
    fun `a camera capture in the staging folder is used as it is`() = runBlocking {
        stagingDir.mkdirs()
        val capture = File(stagingDir, "capture_1.jpg").apply { writeText("jpeg") }
        val file = staging.stage(Uri.fromFile(capture))
        assertEquals("capture_1.jpg", file.name)
        assertEquals("image/jpeg", file.mimeType)
        assertEquals(1, stagingDir.listFiles()!!.size)

        // An empty capture means the camera was cancelled.
        val empty = File(stagingDir, "capture_2.jpg").apply { createNewFile() }
        assertTrue(runCatching { staging.stage(Uri.fromFile(empty)) }.exceptionOrNull() is IOException)
    }

    @Test
    fun `another app cannot attach this app's own files`() = runBlocking {
        suspend fun refused(uri: String) = runCatching { staging.stage(Uri.parse(uri)) }.exceptionOrNull() is IOException

        // A share that names a private file by path, directly or by climbing out of the staging folder.
        assertTrue(refused(Uri.fromFile(secret).toString()))
        stagingDir.mkdirs()
        assertTrue(refused("file://${stagingDir.path}/../../files/secret.db"))
        // Our own FileProvider (saved outputs) is not a pick either.
        assertTrue(refused("content://app.files/media/output.png"))
        assertTrue(refused("http://example.com/x.png"))
        assertTrue(refused("android.resource://some.app/raw/x"))
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `uploads read staged files only`() {
        stagingDir.mkdirs()
        val staged = File(stagingDir, "ok.txt").apply { writeText("ok") }
        assertTrue(staging.isStaged(staged))
        assertFalse(staging.isStaged(secret))
        assertFalse(staging.isStaged(File(stagingDir, "missing.txt")))
        assertFalse(staging.isStaged(File(stagingDir, "../../files/secret.db")))

        fun refused(uri: String) = runCatching { staging.open(uri).close() }.exceptionOrNull() is FileNotFoundException
        // What a tampered run history or preset could ask for.
        assertTrue(refused(Uri.fromFile(secret).toString()))
        assertTrue(refused("file://${stagingDir.path}/../../files/secret.db"))
        assertTrue(refused("content://media.provider/images/42"))
        assertTrue(refused("file://${stagingDir.path}/missing.txt"))
        assertFalse(refused(Uri.fromFile(staged).toString()))
    }
}
