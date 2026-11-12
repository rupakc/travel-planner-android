package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags

/** Where the app starts. */
enum class StartRoute {
    /** Journey UI on, signed in, at least one trip. */
    Trips,

    /** Journey UI on but nothing to resume (signed out, no trips, trips failed to load): Search with its Chat / Near me entries. */
    Explore,

    /** Journey UI off (previous backend or flag off): the five existing tabs and their own start. */
    Classic,
}

/**
 * The home rule. [tripCount] is null when the trips could not be loaded (or are unknown): that never blocks
 * the user, it starts in [StartRoute.Explore].
 */
fun resolveStartRoute(flags: JourneyFlags, signedIn: Boolean, tripCount: Int?): StartRoute =
    when {
        !flags.journey || !flags.trips -> StartRoute.Classic
        signedIn && (tripCount ?: 0) > 0 -> StartRoute.Trips
        else -> StartRoute.Explore
    }

/** Splash gate: no flash of the wrong start screen. */
object StartGate {
    const val MAX_WAIT_MS = 2_000L

    /** True while the splash should stay: config unknown, or (journey on, signed in) trips count unknown, for at most [MAX_WAIT_MS]. */
    fun hold(configKnown: Boolean, flags: JourneyFlags, signedIn: Boolean, tripCountKnown: Boolean, waitedMs: Long): Boolean {
        if (waitedMs >= MAX_WAIT_MS) return false
        if (!configKnown) return true
        return flags.journey && flags.trips && signedIn && !tripCountKnown
    }
}
