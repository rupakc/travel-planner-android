package io.github.rupakc.travelplanner.core.domain.server

import java.net.URI
import java.net.URISyntaxException

/** Outcome of [ServerUrl.validate]. */
sealed interface ServerUrlResult {
    /** [url]: normalised (lower-case scheme and host, no trailing slash, path prefix kept). */
    data class Valid(val url: String) : ServerUrlResult

    /** Nothing entered. */
    data object Blank : ServerUrlResult

    /** Not a URL (bad syntax, missing scheme, query or fragment). */
    data object Malformed : ServerUrlResult

    /** A scheme other than http/https. */
    data object UnsupportedScheme : ServerUrlResult

    /** No host (e.g. `http://`). */
    data object MissingHost : ServerUrlResult

    /** `http://` where only HTTPS is allowed (`prod` flavour: `AppBuildInfo.cleartextAllowed == false`). */
    data object HttpsRequired : ServerUrlResult
}

/**
 * Pure validator for the backend base URL (Login → Server, Settings → Server). It mirrors what
 * `SettingsRepository.setBaseUrl` accepts, so the UI can explain problems before saving.
 * Rules: http/https only, a host is required, no query/fragment, trailing slashes dropped;
 * when [allowHttp] is false (`prod`), plain http is rejected.
 */
object ServerUrl {
    fun validate(raw: String, allowHttp: Boolean = true): ServerUrlResult {
        val input = raw.trim()
        if (input.isEmpty()) return ServerUrlResult.Blank
        if (!input.contains("://")) return ServerUrlResult.Malformed
        val rawScheme = input.substringBefore("://").lowercase()
        if (rawScheme != HTTP && rawScheme != HTTPS) return ServerUrlResult.UnsupportedScheme
        val rest = input.substringAfter("://")
        if (rest.isBlank() || rest.startsWith("/")) return ServerUrlResult.MissingHost
        val uri =
            try {
                URI(input)
            } catch (_: URISyntaxException) {
                return ServerUrlResult.Malformed
            }
        val scheme = uri.scheme?.lowercase() ?: return ServerUrlResult.Malformed
        if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return ServerUrlResult.Malformed
        val host =
            uri.host?.takeIf { it.isNotBlank() } ?: return if (uri.rawAuthority.isNullOrBlank()) ServerUrlResult.MissingHost else ServerUrlResult.Malformed
        if (scheme == HTTP && !allowHttp) return ServerUrlResult.HttpsRequired
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return ServerUrlResult.Valid("$scheme://${host.lowercase()}$port$path")
    }

    private const val HTTP = "http"
    private const val HTTPS = "https"
}
