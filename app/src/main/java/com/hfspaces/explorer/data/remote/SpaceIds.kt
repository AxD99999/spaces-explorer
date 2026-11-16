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
    private val SPACE_URL = Regex(
        """(?:https?://)?(?:www\.)?(?:huggingface\.co|hf\.co)/spaces/([A-Za-z0-9][A-Za-z0-9._-]*)/([A-Za-z0-9][A-Za-z0-9._-]*)""",
        RegexOption.IGNORE_CASE
    )

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

    /**
     * The Space a Hub link points at, e.g. `https://huggingface.co/spaces/gradio/hello_world`
     * anywhere in [text]. Null when there is no such link.
     */
    fun fromUrl(text: String?): String? {
        val match = SPACE_URL.find(text.orEmpty()) ?: return null
        // A link at the end of a sentence drags its full stop along.
        val id = "${match.groupValues[1]}/${match.groupValues[2].trimEnd('.')}"
        return id.takeIf(::isValidSpaceId)
    }

    /** Like [fromUrl], and also accepts a bare `owner/name`. For text the user typed to find a Space. */
    fun fromLink(text: String?): String? = fromUrl(text) ?: text?.trim()?.takeIf(::isValidSpaceId)

    /**
     * Where the Space's own web app is served, for opening in a browser. Unlike [resolveHost] this
     * also accepts static Spaces (`name.static.hf.space`): the app never calls this address itself,
     * so no token can end up there.
     */
    fun webAppUrl(space: HfSpace): String? {
        val domains = space.runtime?.domains.orEmpty()
        val candidates = listOfNotNull(space.host, domains.firstOrNull { it.stage.equals("READY", ignoreCase = true) }?.domain) +
            domains.map { it.domain }
        candidates.firstNotNullOfOrNull(::browsableSpaceHost)?.let { return it }
        val fallback = hostForSubdomain(space.subdomain)
            ?: if (isValidSpaceId(space.id)) hostForSubdomain(defaultSubdomain(space.id)) else null
        return fallback?.toString()
    }

    private fun browsableSpaceHost(raw: String): String? {
        if (raw.isBlank()) return null
        val url = (if (raw.contains("://")) raw else "https://$raw").toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val host = url.host.lowercase()
        return if (host.endsWith(SPACE_HOST_SUFFIX) && host.length > SPACE_HOST_SUFFIX.length) "https://$host/" else null
    }

    /** Model and dataset ids (`name` or `owner/name`), checked before they go into a Hub URL. */
    fun isValidRepoId(id: String): Boolean {
        val parts = id.split('/')
        return parts.size in 1..2 && parts.all { NAME_PART.matches(it) && !it.contains("..") }
    }

    /** Hosts that may receive the user's HF token. */
    fun isHuggingFaceHost(url: HttpUrl): Boolean {
        if (url.scheme != "https") return false
        val host = url.host.lowercase()
        return host == "huggingface.co" || host.endsWith(".huggingface.co") || host.endsWith(SPACE_HOST_SUFFIX)
    }
}
