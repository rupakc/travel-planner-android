package io.github.rupakc.travelplanner.navigation

import io.github.rupakc.travelplanner.AuthGate
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute

/** Where a deep link held behind the auth gate is kept (survives process death; [ShellViewModel]). */
interface PendingDeepLinks {
    fun keep(link: String)

    /** The kept link, removed on read. */
    fun take(): String?

    fun clear()
}

/**
 * The auth gate's deep-link bookkeeping (architecture review M3), pure so it can be unit-tested.
 *
 * - [latest] is the app link the user just opened (launch intent or a new intent) and has not seen yet.
 * - When the gate redirects to Login / change password / onboarding, [latest] moves to [PendingDeepLinks].
 * - When the gate then sends a READY user Home, the pending link is returned exactly once for replay.
 * - Once a destination is actually shown outside the gate screens (a link opened while READY, the public share
 *   viewer, or anything after Back), both are dropped, so a link the user already saw or left is never replayed.
 */
internal class DeepLinkHold(
    private val pending: PendingDeepLinks,
    initial: String?,
) {
    var latest: String? = initial
        private set

    /** A new app intent arrived ([link] null when it carries none). */
    fun onIntent(link: String?) {
        latest = link
    }

    /** After the intent was handed to the NavController: a READY user now sees it, so nothing is held. */
    fun afterHandled(gate: AuthGate) {
        if (gate == AuthGate.READY) latest = null
    }

    /**
     * One run of the gate check. [target] is where the gate sends the user (null = stay), [onGateScreen] whether the
     * current destination is Login / change password / onboarding. Returns the link to replay after navigating to
     * [target] (replayed when that is [home], the start screen).
     */
    fun onGateCheck(
        gate: AuthGate,
        target: AppRoute?,
        onGateScreen: Boolean,
        home: AppRoute = HomeRoute,
    ): String? {
        if (gate == AuthGate.LOADING) return null
        if (target == null) {
            if (!onGateScreen) {
                // A real destination is on screen (opened link, share viewer, or the user moved on): nothing to replay.
                latest = null
                pending.clear()
            }
            return null
        }
        if (gate != AuthGate.READY) latest?.let(pending::keep)
        latest = null
        return if (gate == AuthGate.READY && target == home) pending.take() else null
    }
}
