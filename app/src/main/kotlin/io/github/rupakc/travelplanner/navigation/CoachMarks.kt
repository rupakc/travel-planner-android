package io.github.rupakc.travelplanner.navigation

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.R

/** The three first-run tips, one per tab, in the order a new user meets them. */
enum class CoachTip(
    val id: String,
    val tab: TopLevelDestination,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
) {
    TRIPS("trips", TopLevelDestination.TRIPS, R.string.coach_trips_title, R.string.coach_trips_body),
    EXPLORE("explore", TopLevelDestination.EXPLORE, R.string.coach_explore_title, R.string.coach_explore_body),
    PLAY("play", TopLevelDestination.PLAY, R.string.coach_play_title, R.string.coach_play_body),
}

/** Pure rules for the one-time coach marks. State (which tips a user has seen) lives in `CoachMarkStore`, keyed by user. */
internal object CoachMarks {
    val ALL_IDS: Set<String> = CoachTip.entries.mapTo(linkedSetOf()) { it.id }

    /**
     * The tip to show on [tab], or null. Only with the journey tabs, only for a signed-in user whose state is loaded ([seen] non-null),
     * and never again once seen or skipped.
     */
    fun tipFor(tab: TopLevelDestination?, seen: Set<String>?, journey: Boolean): CoachTip? {
        if (!journey || tab == null || seen == null) return null
        return CoachTip.entries.firstOrNull { it.tab == tab && it.id !in seen }
    }
}
