package io.github.rupakc.travelplanner.core.domain.auth

import java.net.URI

/**
 * Only a same-origin OAuth start path is ever opened (port of the web `safeStart` in
 * `SocialSignInButtons.jsx`). The raw string must be free of `..` and backslashes; the resolved URL
 * must have the base URL's scheme, host and port, no user info, and a path (raw and decoded) under
 * `/api/auth/` with no dot segments, so `%2e%2e` and `%5c` are caught after decoding.
 */
object SafeStartPath {
    private const val PREFIX = "/api/auth/"
    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443

    /** The absolute URL to open for [startUrl] on the server at [baseUrl] (query kept, fragment dropped), or null. */
    fun resolve(startUrl: String?, baseUrl: String): String? {
        if (startUrl.isNullOrEmpty() || startUrl.contains("..") || startUrl.contains('\\')) return null
        return runCatching { check(URI(startUrl), URI(baseUrl)) }.getOrNull()
    }

    private fun check(start: URI, base: URI): String? {
        if (base.scheme == null || base.host == null || base.isOpaque) return null
        val url = base.resolve(start)
        val sameOrigin =
            !url.isOpaque &&
                url.scheme.equals(base.scheme, ignoreCase = true) &&
                url.host.equals(base.host, ignoreCase = true) &&
                url.effectivePort() == base.effectivePort() &&
                url.rawUserInfo == null
        val rawPath = url.rawPath.orEmpty()
        val path = url.path.orEmpty()
        val safePath =
            rawPath.startsWith(PREFIX) && path.startsWith(PREFIX) && !path.contains("..") && !path.contains('\\')
        if (!sameOrigin || !safePath) return null
        val query = url.rawQuery?.let { "?$it" }.orEmpty()
        return "${base.scheme}://${base.rawAuthority}$rawPath$query"
    }

    private fun URI.effectivePort(): Int =
        when {
            port != -1 -> port
            scheme.equals("https", ignoreCase = true) -> HTTPS_PORT
            scheme.equals("http", ignoreCase = true) -> HTTP_PORT
            else -> -1
        }
}
