package io.github.rupakc.travelplanner.navigation

import android.content.Intent
import androidx.core.net.toUri
import io.github.rupakc.travelplanner.BuildConfig
import io.github.rupakc.travelplanner.core.domain.auth.OauthCallback
import io.github.rupakc.travelplanner.core.domain.auth.OauthCallbackLink
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks

/**
 * Hardening for intents reaching the exported MainActivity (security review S-M1).
 *
 * `NavController.handleDeepLink` honours Navigation's internal extras (destination ids, args), and
 * type-safe destination ids are computable route hashes, so any installed app could land on any
 * destination with arguments of its choosing (e.g. an import URL that starts a request as the user).
 * Only `travelplanner://` VIEW links (the manifest filter, notifications and mapped pushes), the OAuth return and the
 * emailed verify / reset links (exact prod host, exact path, one bounded `token`: [AccountLinks]) may route.
 */
internal object DeepLinkIntents {
    /** Navigation's internal deep-link extras (`NavController.KEY_DEEP_LINK_*`). */
    val NAV_EXTRAS: List<String> =
        listOf(
            "android-support-nav:controller:deepLinkIds",
            "android-support-nav:controller:deepLinkArgs",
            "android-support-nav:controller:deepLinkExtras",
            "android-support-nav:controller:deepLinkIntent",
        )

    /**
     * Strips Navigation's internal extras from [intent] (in place) and keeps its data only for a `travelplanner://`
     * VIEW intent, the one shape the gate knows how to hold and replay. Run after `withPushDeepLink`, which turns a
     * system-shown push into such a VIEW intent.
     */
    fun sanitize(intent: Intent, webHost: String = WEB_HOST): Intent {
        NAV_EXTRAS.forEach(intent::removeExtra)
        val data = intent.data
        val isView = intent.action == Intent.ACTION_VIEW
        val allowed =
            isView &&
                (data?.scheme == DeepLinks.SCHEME || OauthCallbackLink.parse(data?.toString()) != null || AccountLinks.route(data?.toString(), webHost) != null)
        if (data != null && !allowed) intent.data = null
        return intent
    }

    /** The app deep link [intent] carries (`travelplanner://…` VIEW), or null. */
    fun appLink(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.takeIf { it.scheme == DeepLinks.SCHEME }?.toString()

    /** The emailed verify / reset link [intent] carries (`https://<prod host>/verify|reset-password?token=`), or null. */
    fun accountLink(intent: Intent?, webHost: String = WEB_HOST): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.toString()?.takeIf { AccountLinks.route(it, webHost) != null }

    /** The link the auth gate may hold and replay: an app link or an account link. */
    fun heldLink(intent: Intent?): String? = appLink(intent) ?: accountLink(intent)

    /** The screen a held [link] opens when it is an account link, else null (app links go through the NavController). */
    fun accountRoute(link: String, webHost: String = WEB_HOST): AppRoute? = AccountLinks.route(link, webHost)

    /** Host of the production web app (the emailed links' host); blank in a build without `WEB_BASE_URL`, which then accepts none. */
    val WEB_HOST: String get() = runCatching { BuildConfig.WEB_BASE_URL.toUri().host }.getOrNull().orEmpty()

    /**
     * The OAuth return (`voyager://auth/callback?code=|error=`, bounded params) [intent] carries, or null. The data
     * is only the browser's answer: it is acted on solely while an attempt this process started is pending.
     */
    fun oauthCallback(intent: Intent?): OauthCallback? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.let { OauthCallbackLink.parse(it.toString()) }
}
