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
}
