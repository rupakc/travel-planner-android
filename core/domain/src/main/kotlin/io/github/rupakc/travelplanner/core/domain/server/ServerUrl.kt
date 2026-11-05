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
        val scheme = input.substringBefore("://", missingDelimiterValue = "").lowercase()
        val rest = input.substringAfter("://", missingDelimiterValue = "")
        val uri = if (rest.isBlank()) null else parse(input)
        val host = uri?.host?.takeIf { it.isNotBlank() }
        return when {
            input.isEmpty() -> ServerUrlResult.Blank
            !input.contains("://") -> ServerUrlResult.Malformed
            scheme != HTTP && scheme != HTTPS -> ServerUrlResult.UnsupportedScheme
            rest.isBlank() || rest.startsWith("/") -> ServerUrlResult.MissingHost
            uri == null || host == null -> ServerUrlResult.Malformed
            uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null -> ServerUrlResult.Malformed
            scheme == HTTP && !allowHttp -> ServerUrlResult.HttpsRequired
            else -> ServerUrlResult.Valid(normalise(scheme, host, uri))
        }
    }

    private fun parse(input: String): URI? =
        try {
            URI(input)
        } catch (_: URISyntaxException) {
            null
        }

    private fun normalise(scheme: String, host: String, uri: URI): String {
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://${host.lowercase()}$port${uri.rawPath.orEmpty().trimEnd('/')}"
    }

    private const val HTTP = "http"
    private const val HTTPS = "https"
}
