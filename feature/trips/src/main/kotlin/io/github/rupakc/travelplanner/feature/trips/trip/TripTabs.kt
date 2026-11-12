package io.github.rupakc.travelplanner.feature.trips.trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip

/** The tabs of a trip. [id] is the route / telemetry spelling (`TripRoute.tab`, `trip_view.surface`). */
enum class TripTab(val id: String) {
    Overview("overview"),
    Prep("prep"),
    Today("today"),
    Money("money"),
    Journal("journal"),
    Recap("recap"),
    Offline("offline"),
    ;

    companion object {
        /** The tab for a route argument, or null when unknown. */
        fun fromId(id: String?): TripTab? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Content of one tab, plugged in by the task that owns it (Today, Money, Journal, Recap, Offline): the graph takes a
 * `Map<TripTab, TripTabContent>`. Overview and Prep are built in. A tab without content is not shown.
 */
typealias TripTabContent = @Composable (trip: Trip, modifier: Modifier) -> Unit

/**
 * Switches the open trip to another tab. [TripScreen] provides it to the tab content, so a tab (Today's "Add expense")
 * can move to a sibling tab without navigating: navigating to the route already on top of the stack does nothing.
 * Null outside a trip screen.
 */
val LocalTripTabSelector = compositionLocalOf<((TripTab) -> Unit)?> { null }

/** The tabs of a phase, in order, and the one opened by default (web `tabsFor`). */
data class TabLayout(val tabs: List<TripTab>, val default: TripTab)

/** The tabs actually [visible] and which is [selected]. */
data class ResolvedTabs(val visible: List<TripTab>, val selected: TripTab)

/** Which tabs a trip shows in each phase (web `journey/tripTabs.js`). */
object TripTabs {
    /** Tabs by phase; an unknown phase counts as planning. */
    fun forPhase(phase: Phase?): TabLayout =
        when (phase ?: Phase.PLANNING) {
            Phase.DREAMING -> {
                TabLayout(listOf(TripTab.Overview), TripTab.Overview)
            }

            Phase.PLANNING -> {
                TabLayout(listOf(TripTab.Overview, TripTab.Prep, TripTab.Money, TripTab.Offline), TripTab.Overview)
            }

            Phase.READY -> {
                TabLayout(listOf(TripTab.Prep, TripTab.Overview, TripTab.Offline, TripTab.Money), TripTab.Prep)
            }

            Phase.ON_THE_ROAD -> {
                TabLayout(listOf(TripTab.Today, TripTab.Offline, TripTab.Money, TripTab.Journal, TripTab.Overview), TripTab.Today)
            }

            Phase.MEMORIES -> {
                TabLayout(listOf(TripTab.Recap, TripTab.Journal, TripTab.Money, TripTab.Overview), TripTab.Recap)
            }
        }

    /**
     * The phase's tabs that exist ([available]: built in or given content), the default one (else the first visible),
     * and a [requested] tab (a route argument or a click) when it exists: a tab outside the phase stays reachable by
     * route and is then added at the end.
     */
    fun resolve(phase: Phase?, requested: TripTab?, available: Set<TripTab>): ResolvedTabs {
        val layout = forPhase(phase)
        val visible = layout.tabs.filter { it in available }.toMutableList()
        if (requested != null && requested in available && requested !in visible) visible += requested
        val selected =
            when {
                requested != null && requested in visible -> requested
                layout.default in visible -> layout.default
                else -> visible.firstOrNull() ?: TripTab.Overview
            }
        return ResolvedTabs(visible.ifEmpty { listOf(TripTab.Overview) }, selected)
    }
}
