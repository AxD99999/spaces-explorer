package com.hfspaces.explorer.data.model

import com.hfspaces.explorer.data.gradio.OutputItem
import com.hfspaces.explorer.system.pickShortcutSpaces
import com.hfspaces.explorer.ui.common.compactCount
import com.hfspaces.explorer.ui.common.savedCopies
import com.hfspaces.explorer.ui.library.FavouriteSort
import com.hfspaces.explorer.ui.library.arrangeFavourites
import com.hfspaces.explorer.ui.space.SpaceViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HelpersTest {
    private val a = HfSpace(id = "x/a", title = "Zeta", likes = 1, sdk = "gradio")
    private val b = HfSpace(id = "x/b", title = "alpha", likes = 9, sdk = "gradio")
    private val c = HfSpace(id = "x/c", title = "Mid", likes = 5, sdk = "docker")

    @Test
    fun `shortcuts prefer recent runs then favourites without duplicates or non-runnable spaces`() {
        assertEquals(listOf(b, a), pickShortcutSpaces(recent = listOf(b), favorites = listOf(a, b, c), max = 4))
        assertEquals(listOf(b), pickShortcutSpaces(recent = listOf(b), favorites = listOf(a), max = 1))
    }

    @Test
    fun `favourites filter by collection and sort`() {
        val all = listOf(a, b, c)
        val members = mapOf("col" to setOf("x/a", "x/c"))
        assertEquals(all, arrangeFavourites(all, members, null, FavouriteSort.RECENT))
        assertEquals(listOf(a, c), arrangeFavourites(all, members, "col", FavouriteSort.RECENT))
        assertEquals(listOf(b, c, a), arrangeFavourites(all, members, null, FavouriteSort.NAME))
        assertEquals(listOf(c, a), arrangeFavourites(all, members, "col", FavouriteSort.LIKES))
        assertEquals(emptyList<HfSpace>(), arrangeFavourites(all, members, "missing", FavouriteSort.RECENT))
    }

    @Test
    fun `shared mime types match input filters`() {
        assertTrue(SpaceViewModel.mimeMatches("image/*", "image/png"))
        assertTrue(SpaceViewModel.mimeMatches("*/*", null))
        assertFalse(SpaceViewModel.mimeMatches("image/*", "audio/wav"))
        assertFalse(SpaceViewModel.mimeMatches("video/*", null))
    }

    @Test
    fun `adult spaces are recognised by the hub tag and hidden unless shown`() {
        val adult = HfSpace(id = "x/n", tags = listOf("gradio", "Not-For-All-Audiences"))
        assertTrue(adult.isAdult)
        assertFalse(a.isAdult)
        assertEquals(listOf(a), listOf(a, adult).hidingAdult(show = false))
        assertEquals(listOf(a, adult), listOf(a, adult).hidingAdult(show = true))
    }

    @Test
    fun `outputs use their saved copies when those belong to the same run`() {
        fun output(type: GeneratedMediaType) = OutputItem(type, url = "https://x.hf.space/file=a")
        fun saved(id: String, type: GeneratedMediaType, path: String?) =
            GeneratedMediaItem(id = id, type = type, localPath = path, title = "t", spaceId = "x/a", spaceTitle = "A", createdAt = "now")
        val outputs = listOf(output(GeneratedMediaType.IMAGE), output(GeneratedMediaType.AUDIO), output(GeneratedMediaType.TEXT))
        val copies = listOf(
            saved("m1", GeneratedMediaType.IMAGE, "/data/m1.png"),
            saved("m2", GeneratedMediaType.AUDIO, null),
            saved("m3", GeneratedMediaType.TEXT, null)
        )
        // The image has a file; the audio download failed, so its link stays; text has no file at all.
        assertEquals(listOf("m1", null, null), savedCopies(outputs, copies).map { it?.id })
        // Before the run is saved, or with another run's copies, nothing is substituted.
        assertEquals(listOf(null, null, null), savedCopies(outputs, emptyList()).map { it?.id })
        assertEquals(listOf(null, null, null), savedCopies(outputs, copies.take(2)).map { it?.id })
        assertEquals(listOf<String?>(null), savedCopies(listOf(output(GeneratedMediaType.VIDEO)), listOf(saved("m1", GeneratedMediaType.IMAGE, "/p"))).map { it?.id })
    }

    @Test
    fun `counts are shortened`() {
        assertEquals(listOf("0", "999", "1k", "1.2k", "12.3k", "999k", "1M", "2.5M"), listOf(0, 999, 1000, 1234, 12_345, 999_999, 1_000_000, 2_500_000).map(::compactCount))
    }
}
