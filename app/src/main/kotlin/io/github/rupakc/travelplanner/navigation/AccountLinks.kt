package io.github.rupakc.travelplanner.navigation

import androidx.core.net.toUri
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResetPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute

/**
 * The emailed links (R3): `https://<prod host>/verify?token=` and `/reset-password?token=`. Pure and strict, because the
 * exported activity receives whatever any app sends: the exact host, the exact path, only `token`, a bounded URL-safe value.
 * Never a route to anything but the verify / reset screens, which take the token as their argument.
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
        val params = uri.queryParameterNames
        if (params.size != 1 || "token" !in params) return null
        val token = uri.getQueryParameters("token").singleOrNull()?.takeIf(TOKEN::matches) ?: return null
        return when (uri.path) {
            "/verify" -> VerifyEmailRoute(token)
            "/reset-password" -> ResetPasswordRoute(token)
            else -> null
        }
    }
}
