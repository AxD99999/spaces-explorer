package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.data.model.HfSpace
import com.hfspaces.explorer.data.model.HfSpaceDomain
import com.hfspaces.explorer.data.model.HfSpaceRuntime
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceIdsTest {
    @Test
    fun `accepts normal space ids`() {
        listOf("gradio/hello_world", "black-forest-labs/FLUX.1-dev", "a/b", "user123/My.Space-v2").forEach {
            assertTrue(it, SpaceIds.isValidSpaceId(it))
        }
    }

    @Test
    fun `rejects ids that could escape the URL path`() {
        listOf(
            "", "noslash", "a/b/c", "../etc", "../../etc/passwd", "a/..", "a/.", "a/b?x=1", "a/b#frag",
            "a/b c", "-a/b", "a/b%2f", "a/b\nc", "a/..b"
        ).forEach { assertFalse(it, SpaceIds.isValidSpaceId(it)) }
    }

    @Test
    fun `default subdomain follows hub convention`() {
        assertEquals("gradio-hello-world", SpaceIds.defaultSubdomain("gradio/hello_world"))
        assertEquals("black-forest-labs-flux-1-dev", SpaceIds.defaultSubdomain("black-forest-labs/FLUX.1-dev"))
    }

    @Test
    fun `only https hf space hosts are accepted`() {
        assertEquals("https://foo-bar.hf.space/".toHttpUrl(), SpaceIds.parseSpaceHost("https://foo-bar.hf.space"))
        assertEquals("https://foo-bar.hf.space/".toHttpUrl(), SpaceIds.parseSpaceHost("foo-bar.hf.space"))
        listOf(
            "http://foo.hf.space",
            "https://evil.com",
            "https://evil.com/x.hf.space",
            "https://foo.hf.space.evil.com",
            "https://user:pw@foo.hf.space",
            "https://foo.hf.space:8443",
            "https://a.b.hf.space",
            "https://-bad.hf.space",
            "evil.com/x?",
            null,
            ""
        ).forEach { assertNull(it.toString(), SpaceIds.parseSpaceHost(it)) }
    }

    @Test
    fun `host and path are dropped from accepted urls`() {
        assertEquals("https://foo.hf.space/".toHttpUrl(), SpaceIds.parseSpaceHost("https://foo.hf.space/some/path?q=1"))
    }

    @Test
    fun `resolve host prefers hub host then ready domain then id`() {
        val base = HfSpace(id = "owner/name")
        assertEquals("https://owner-name.hf.space/".toHttpUrl(), SpaceIds.resolveHost(base))
        val withDomain = base.copy(
            runtime = HfSpaceRuntime(domains = listOf(HfSpaceDomain("stale.hf.space", "STOPPED"), HfSpaceDomain("renamed.hf.space", "READY")))
        )
        assertEquals("https://renamed.hf.space/".toHttpUrl(), SpaceIds.resolveHost(withDomain))
        assertEquals("https://explicit.hf.space/".toHttpUrl(), SpaceIds.resolveHost(withDomain.copy(host = "https://explicit.hf.space")))
    }

    @Test
    fun `a hostile host field falls back to the derived host`() {
        val space = HfSpace(id = "owner/name", host = "https://attacker.example", subdomain = "x.attacker")
        assertEquals("https://owner-name.hf.space/".toHttpUrl(), SpaceIds.resolveHost(space))
    }

    @Test
    fun `tokens only go to hugging face hosts`() {
        assertTrue(SpaceIds.isHuggingFaceHost("https://huggingface.co/api".toHttpUrl()))
        assertTrue(SpaceIds.isHuggingFaceHost("https://cdn-lfs.huggingface.co/x".toHttpUrl()))
        assertTrue(SpaceIds.isHuggingFaceHost("https://foo.hf.space/".toHttpUrl()))
        assertFalse(SpaceIds.isHuggingFaceHost("http://foo.hf.space/".toHttpUrl()))
        assertFalse(SpaceIds.isHuggingFaceHost("https://raw.githubusercontent.com/x".toHttpUrl()))
        assertFalse(SpaceIds.isHuggingFaceHost("https://nothuggingface.co/".toHttpUrl()))
    }

    @Test
    fun `space links are recognised wherever they appear`() {
        assertEquals("gradio/hello_world", SpaceIds.fromUrl("https://huggingface.co/spaces/gradio/hello_world"))
        assertEquals("gradio/hello_world", SpaceIds.fromUrl("https://huggingface.co/spaces/gradio/hello_world/tree/main?x=1#readme"))
        assertEquals("black-forest-labs/FLUX.1-dev", SpaceIds.fromUrl("Look at this: HTTPS://HF.co/spaces/black-forest-labs/FLUX.1-dev."))
        assertEquals("a/b", SpaceIds.fromUrl("huggingface.co/spaces/a/b"))
        assertEquals("a/b", SpaceIds.fromUrl("https://www.huggingface.co/spaces/a/b"))

        // Not Space pages.
        assertNull(SpaceIds.fromUrl("https://huggingface.co/spaces/gradio"))
        assertNull(SpaceIds.fromUrl("https://huggingface.co/gradio/hello_world"))
        assertNull(SpaceIds.fromUrl("https://huggingface.co/datasets/a/b"))
        assertNull(SpaceIds.fromUrl("https://example.com/spaces/a/b"))
        assertNull(SpaceIds.fromUrl("https://huggingface.co/spaces/a/.."))
        assertNull(SpaceIds.fromUrl("a prompt about cats/dogs"))
        assertNull(SpaceIds.fromUrl(null))
    }

    @Test
    fun `typed text may also be a bare id`() {
        assertEquals("gradio/hello_world", SpaceIds.fromLink("  gradio/hello_world "))
        assertEquals("a/b", SpaceIds.fromLink("https://hf.co/spaces/a/b"))
        assertNull(SpaceIds.fromLink("image generation"))
        assertNull(SpaceIds.fromLink("a/b/c"))
        assertNull(SpaceIds.fromLink(""))
    }

    @Test
    fun `the web app address comes from the hub and stays on hf space`() {
        assertEquals("https://gradio-hello-world.hf.space/", SpaceIds.webAppUrl(HfSpace(id = "gradio/hello_world", host = "https://gradio-hello-world.hf.space")))
        // Static Spaces live one label deeper; fine for a browser, though never for API calls.
        val static = HfSpace(id = "a/site", runtime = HfSpaceRuntime(domains = listOf(HfSpaceDomain("a-site.static.hf.space", "READY"))))
        assertEquals("https://a-site.static.hf.space/", SpaceIds.webAppUrl(static))
        assertNull(SpaceIds.resolveHost(static)?.takeIf { it.host.contains("static") })
        // Anything off hf.space is ignored in favour of the conventional address.
        val hostile = HfSpace(id = "owner/name", host = "https://attacker.example", runtime = HfSpaceRuntime(domains = listOf(HfSpaceDomain("evil.example"))))
        assertEquals("https://owner-name.hf.space/", SpaceIds.webAppUrl(hostile))
        assertEquals("https://owner-name.hf.space/", SpaceIds.webAppUrl(HfSpace(id = "owner/name", host = "https://user:pw@x.hf.space")))
        assertEquals("https://owner-name.hf.space/", SpaceIds.webAppUrl(HfSpace(id = "owner/name", host = "https://.hf.space")))
    }

    @Test
    fun `repo ids are checked before they go into links`() {
        assertTrue(SpaceIds.isValidRepoId("gpt2"))
        assertTrue(SpaceIds.isValidRepoId("openai/whisper-large-v3"))
        assertFalse(SpaceIds.isValidRepoId("a/b/c"))
        assertFalse(SpaceIds.isValidRepoId("../secret"))
        assertFalse(SpaceIds.isValidRepoId("a b"))
        assertFalse(SpaceIds.isValidRepoId(""))
    }
}
