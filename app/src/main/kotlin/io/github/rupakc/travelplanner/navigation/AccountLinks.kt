package io.github.rupakc.travelplanner.navigation

import androidx.core.net.toUri
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResetPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute

/**
 * The emailed links (R3): `https://<prod host>/verify?token=` and `/reset-password?token=`, plus the share links (R6)
 * `https://<prod host>/share/<token>`. Pure and strict, because the
 * exported activity receives whatever any app sends: the exact host, the exact path, only `token`, a bounded URL-safe value.
 * Never a route to anything but the verify / reset screens and the public share viewer, which take the token as their argument.
 */
internal object AccountLinks {
    const val MAX_TOKEN = 512
    private val TOKEN = Regex("^[A-Za-z0-9_.~-]{1,$MAX_TOKEN}$")

    /** The route [link] opens for [host] (the prod web host, no scheme), or null for anything else. */
    fun route(link: String?, host: String): AppRoute? {
        if (link.isNullOrBlank() || host.isBlank()) return null
        val uri = runCatching { link.toUri() }.getOrNull() ?: return null
        if (uri.scheme != "https" || !uri.host.equals(host, ignoreCase = true)) return null
        if (uri.port != -1 || uri.userInfo != null || uri.fragment != null) return null
        if (uri.queryParameterNames.isEmpty()) return shareRoute(uri.path)
        val params = uri.queryParameterNames
        if (params.size != 1 || "token" !in params) return null
        val token = uri.getQueryParameters("token").singleOrNull()?.takeIf(TOKEN::matches) ?: return null
        return when (uri.path) {
            "/verify" -> VerifyEmailRoute(token)
            "/reset-password" -> ResetPasswordRoute(token)
            else -> null
        }
    }

    /** `/share/<token>` (R6: the public share page; no query at all) as the share viewer, else null. */
    private fun shareRoute(path: String?): AppRoute? {
        val segments = path.orEmpty().split('/')
        if (segments.size != SHARE_SEGMENTS || segments[0].isNotEmpty() || segments[1] != "share") return null
        return segments[2].takeIf(SHARE_TOKEN::matches)?.let(::ShareRoute)
    }

    private const val SHARE_SEGMENTS = 3
    private val SHARE_TOKEN = Regex("^[A-Za-z0-9_-]{1,128}$")
}
