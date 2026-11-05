package io.github.rupakc.travelplanner.navigation

import android.content.Intent
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks

/**
 * Hardening for intents reaching the exported MainActivity (security review S-M1).
 *
 * `NavController.handleDeepLink` honours Navigation's internal extras (destination ids, args), and
 * type-safe destination ids are computable route hashes, so any installed app could land on any
 * destination with arguments of its choosing (e.g. an import URL that starts a request as the user).
 * Only `travelplanner://` VIEW links (the manifest filter, notifications and mapped pushes) may route.
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
    fun sanitize(intent: Intent): Intent {
        NAV_EXTRAS.forEach(intent::removeExtra)
        if (intent.data != null && (intent.action != Intent.ACTION_VIEW || intent.data?.scheme != DeepLinks.SCHEME)) intent.data = null
        return intent
    }

    /** The app deep link [intent] carries (`travelplanner://…` VIEW), or null. */
    fun appLink(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data?.takeIf { it.scheme == DeepLinks.SCHEME }?.toString()
}
