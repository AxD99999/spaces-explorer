package com.hfspaces.explorer.data.model

import com.hfspaces.explorer.data.remote.AppJson
import com.hfspaces.explorer.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceMetadataTest {
    private fun decode(json: String) = AppJson.decodeFromString<HfSpace>(json)

    @Test
    fun `metadata takes its title emoji and description from the card`() {
        // Shape captured from /api/spaces/{id}: none of these are top-level fields there.
        val space = decode(Fixtures.read("space_metadata.json")).normalized()
        assertEquals("FLUX.1 [dev]", space.displayTitle)
        assertEquals("🖥️", space.displayEmoji)
        assertEquals("Generate images from text", space.description)
        assertEquals("yellow", space.colorFrom)
        assertTrue(space.pinned)
        assertEquals("mit", space.license)
        assertEquals("5.25", space.sdkVersion)
        assertTrue(space.isGated)
        assertFalse(space.isPrivate)
        assertEquals(listOf("black-forest-labs/FLUX.1-dev", "madebyollin/taef1"), space.linkedModels)
        assertEquals(listOf("someone/prompts"), space.linkedDatasets)
    }

    @Test
    fun `only the card keys the app reads are kept and survive a round trip`() {
        val space = decode(Fixtures.read("space_metadata.json")).normalized()
        val stored = AppJson.encodeToString(HfSpace.serializer(), space)
        assertFalse("app_file" in stored)
        assertFalse("hf_oauth_scopes" in stored)
        val restored = decode(stored)
        assertEquals(space, restored)
        assertEquals("mit", restored.license)
    }

    @Test
    fun `odd card values never break a space`() {
        // Authors write the card by hand, so any key can hold anything.
        val space = decode(
            """{"id":"a/b","gated":false,"models":"not-a-list",
                "cardData":{"title":{"en":"Nested"},"emoji":["x"],"license":["mit","apache-2.0"],"pinned":"yes","models":[1,"a/model",null]}}"""
        ).normalized()
        assertEquals("b", space.displayTitle)
        assertEquals("🤗", space.displayEmoji)
        assertNull(space.license)
        assertFalse(space.pinned)
        assertFalse(space.isGated)
        assertEquals(listOf("1", "a/model"), space.linkedModels)

        assertNull(decode("""{"id":"a/b","cardData":"oops"}""").normalized().cardData)
        assertEquals("Top", decode("""{"id":"a/b","title":"Top","cardData":{"title":"Card"}}""").normalized().displayTitle)
    }

    @Test
    fun `listing and metadata fill each other in`() {
        val listed = decode(
            """{"id":"a/b","title":"Listed","emoji":"🎨","shortDescription":"From the listing","ai_category":"Image Generation",
                "authorData":{"name":"a","avatarUrl":"/avatars/a.svg"},"likes":5,"trendingScore":12,"featured":true,"tags":["gradio"],
                "runtime":{"stage":"SLEEPING"}}"""
        )
        val fresh = decode(
            """{"id":"a/b","likes":7,"host":"https://a-b.hf.space","subdomain":"a-b","runtime":{"stage":"RUNNING"},
                "cardData":{"license":"mit"},"models":["x/y"]}"""
        ).normalized()

        val merged = fresh.mergedOver(listed)
        assertEquals("Listed", merged.displayTitle)
        assertEquals("🎨", merged.displayEmoji)
        assertEquals("From the listing", merged.description)
        assertEquals("Image Generation", merged.category)
        assertEquals("https://huggingface.co/avatars/a.svg", merged.avatarUrl)
        assertTrue(merged.featured)
        assertEquals(12.0, merged.trendingScore, 0.0)
        // What the new copy does say wins.
        assertEquals(7, merged.likes)
        assertEquals(SpaceStage.RUNNING, merged.stage)
        assertEquals("https://a-b.hf.space", merged.host)

        // The other way round: a later listing must not drop the host or the card.
        val relisted = listed.copy(likes = 9).mergedOver(merged)
        assertEquals(9, relisted.likes)
        assertEquals("https://a-b.hf.space", relisted.host)
        assertEquals("mit", relisted.license)
        assertEquals(listOf("x/y"), relisted.linkedModels)

        // Never merges two different Spaces.
        assertEquals(fresh, fresh.mergedOver(listed.copy(id = "a/other")))
        assertEquals(fresh, fresh.mergedOver(null))
    }
}
