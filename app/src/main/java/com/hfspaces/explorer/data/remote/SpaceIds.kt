package com.hfspaces.explorer.data.remote

import com.hfspaces.explorer.data.model.HfSpace
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Validation for Space ids and Space hosts.
 *
 * Space ids end up in Hub URL paths and the host decides where the user's HF token is sent,
 * so neither is ever taken from API data without passing through here.
 */
object SpaceIds {
    private val NAME_PART = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,95}$")
    private val SUBDOMAIN = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private const val SPACE_HOST_SUFFIX = ".hf.space"

    fun isValidSpaceId(id: String): Boolean {
        val parts = id.split('/')
        return parts.size == 2 &&
            parts.all { NAME_PART.matches(it) && it != "." && it != ".." && !it.contains("..") }
    }

    fun requireValidSpaceId(id: String): String {
        require(isValidSpaceId(id)) { "Invalid Space id: \"$id\"" }
        return id
    }

    /** The Hub's convention for a Space's default subdomain, e.g. `gradio/hello_world` -> `gradio-hello-world`. */
    fun defaultSubdomain(spaceId: String): String =
        spaceId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Returns the https origin for a `*.hf.space` host, or null if [raw] is anything else. */
    fun parseSpaceHost(raw: String?): HttpUrl? {
        if (raw.isNullOrBlank()) return null
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.lowercase()
        if (!host.endsWith(SPACE_HOST_SUFFIX)) return null
        val label = host.removeSuffix(SPACE_HOST_SUFFIX)
        if (!SUBDOMAIN.matches(label)) return null
        return HttpUrl.Builder().scheme("https").host(host).build()
    }

    fun hostForSubdomain(subdomain: String?): HttpUrl? {
        if (subdomain.isNullOrBlank() || !SUBDOMAIN.matches(subdomain)) return null
        return HttpUrl.Builder().scheme("https").host("$subdomain$SPACE_HOST_SUFFIX").build()
    }

    /**
     * Best known host for a Space: the Hub's `host` field, then a READY domain from the runtime,
     * then the subdomain, then the conventional subdomain derived from the id.
     */
    fun resolveHost(space: HfSpace): HttpUrl? {
        parseSpaceHost(space.host)?.let { return it }
        val domains = space.runtime?.domains.orEmpty()
        val ready = domains.firstOrNull { it.stage.equals("READY", ignoreCase = true) } ?: domains.firstOrNull()
        parseSpaceHost(ready?.domain)?.let { return it }
        hostForSubdomain(space.subdomain)?.let { return it }
        return if (isValidSpaceId(space.id)) hostForSubdomain(defaultSubdomain(space.id)) else null
    }

    /** Hosts that may receive the user's HF token. */
    fun isHuggingFaceHost(url: HttpUrl): Boolean {
        if (url.scheme != "https") return false
        val host = url.host.lowercase()
        return host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(SPACE_HOST_SUFFIX)
    }
}
