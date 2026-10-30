package com.hfspaces.explorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceFiltersTest {
    private val running = HfSpace(id = "a/running", title = "Bravo", sdk = "gradio", likes = 5, runtime = HfSpaceRuntime(stage = "RUNNING", hardware = HfSpaceHardware("t4-small")), lastModified = "2026-01-02")
    private val sleeping = HfSpace(id = "a/sleeping", title = "alpha", sdk = "docker", likes = 50, runtime = HfSpaceRuntime(stage = "SLEEPING"), pinned = true, lastModified = "2026-03-01")
    private val broken = HfSpace(id = "a/broken", title = "Charlie", sdk = "gradio", likes = 1, runtime = HfSpaceRuntime(stage = "RUNTIME_ERROR"), featured = true)
    private val all = listOf(running, sleeping, broken)

    @Test
    fun `default filter keeps hub order`() {
        assertEquals(all, all.applyLocalFilters(SpaceFilterState()))
    }

    @Test
    fun `status sdk hardware pinned and featured filters`() {
        assertEquals(listOf(running), all.applyLocalFilters(SpaceFilterState(status = StatusFilter.RUNNING)))
        assertEquals(listOf(sleeping), all.applyLocalFilters(SpaceFilterState(status = StatusFilter.SLEEPING)))
        assertEquals(listOf(running, broken), all.applyLocalFilters(SpaceFilterState(sdk = "GRADIO")))
        assertEquals(listOf(running), all.applyLocalFilters(SpaceFilterState(hardware = "t4-small")))
        assertEquals(listOf(sleeping), all.applyLocalFilters(SpaceFilterState(pinnedOnly = true)))
        assertEquals(listOf(broken), all.applyLocalFilters(SpaceFilterState(featuredOnly = true)))
    }

    @Test
    fun `sorting`() {
        assertEquals(listOf(sleeping, running, broken), all.applyLocalFilters(SpaceFilterState(sortBy = SortOption.LIKES_DESC)))
        assertEquals(listOf(sleeping, running, broken), all.applyLocalFilters(SpaceFilterState(sortBy = SortOption.NAME_ASC)))
        assertEquals(listOf(sleeping, running, broken), all.applyLocalFilters(SpaceFilterState(sortBy = SortOption.RECENT_DESC)))
    }

    @Test
    fun `append drops duplicates across pages`() {
        assertEquals(listOf(running, sleeping, broken), listOf(running, sleeping).appendDistinct(listOf(sleeping, broken)))
    }

    @Test
    fun `remote query changes on search category sdk and server sorts only`() {
        val base = SpaceFilterState(search = "flux")
        // Local-only changes keep the loaded pages.
        assertFalse(base.remoteQueryDiffers(base.copy(search = " flux ", pinnedOnly = true, hardware = "t4-small")))
        assertFalse(base.remoteQueryDiffers(base.copy(sortBy = SortOption.NAME_ASC)))
        // These are answered by the Hub.
        assertTrue(base.remoteQueryDiffers(base.copy(search = "sdxl")))
        assertTrue(base.remoteQueryDiffers(base.copy(category = "chatbots")))
        assertTrue(base.remoteQueryDiffers(base.copy(sdk = "gradio")))
        assertTrue(base.remoteQueryDiffers(base.copy(sortBy = SortOption.LIKES_DESC)))
    }

    @Test
    fun `stage normalisation`() {
        assertEquals(SpaceStage.ERROR, SpaceStage.from("RUNTIME_ERROR"))
        assertEquals(SpaceStage.BUILDING, SpaceStage.from("APP_STARTING"))
        assertEquals(SpaceStage.UNKNOWN, SpaceStage.from(null))
        assertEquals(SpaceStage.RUNNING, SpaceStage.from("running"))
    }
}
