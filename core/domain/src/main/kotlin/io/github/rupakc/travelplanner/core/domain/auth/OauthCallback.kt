package io.github.rupakc.travelplanner.core.domain.auth

import io.github.rupakc.travelplanner.core.model.auth.OauthCallbackErrors
import java.net.URI

/** What the browser handed back on `voyager://auth/callback`. */
sealed interface OauthCallback {
    /** The single-use handoff code to exchange. */
    data class Code(val code: String) : OauthCallback

    /** One of [OauthCallbackErrors.ALL] (anything else is folded into `bad_request`). */
    data class Error(val error: String) : OauthCallback
}

/** Strict parser for the OAuth return link: only `voyager://auth/callback` carrying a bounded `code` or `error`. */
object OauthCallbackLink {
    const val SCHEME = "voyager"
    const val HOST = "auth"
    const val PATH = "/callback"
    const val REDIRECT_URI = "voyager://auth/callback"
    const val MAX_PARAM_LENGTH = 512
    private val SAFE = Regex("^[A-Za-z0-9_\\-.~]+$")

    /** True when [uri] is exactly the callback link (any query). */
    fun matches(uri: String?): Boolean = parseUri(uri) != null

    /** The callback in [uri], or null for anything else (wrong scheme/host/path, no or oversized params, odd characters). */
    fun parse(uri: String?): OauthCallback? {
        val parsed = parseUri(uri) ?: return null
        val params =
            parsed.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { part ->
                part.substringBefore('=') to part.substringAfter('=', "")
            }
        val code = params["code"]
        val error = params["error"]
        return when {
            code != null -> code.takeIf { it.length <= MAX_PARAM_LENGTH && SAFE.matches(it) }?.let(OauthCallback::Code)
            error != null -> error.takeIf { it.length <= MAX_PARAM_LENGTH && SAFE.matches(it) }?.let { OauthCallback.Error(normalise(it)) }
            else -> null
        }
    }

    private fun normalise(error: String): String = if (error in OauthCallbackErrors.ALL) error else "bad_request"

    private fun parseUri(uri: String?): URI? {
        if (uri == null || uri.length > MAX_URI_LENGTH) return null
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        val ok =
            parsed.scheme == SCHEME && parsed.rawAuthority == HOST && parsed.rawPath == PATH &&
                parsed.rawUserInfo == null && parsed.rawFragment == null
        return parsed.takeIf { ok }
    }

    private const val MAX_URI_LENGTH = 1100
}
