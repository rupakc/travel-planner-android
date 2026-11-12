package io.github.rupakc.travelplanner.feature.trips.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.TripRoute as TripDestination
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.feature.trips.hub.TripsHubRoute
import io.github.rupakc.travelplanner.feature.trips.trip.TripRoute
import io.github.rupakc.travelplanner.feature.trips.trip.TripTab
import io.github.rupakc.travelplanner.feature.trips.trip.TripTabContent

/**
 * The Trips feature graph: the hub and a trip. Later tasks add Today, expenses, journal, recap and the offline pack
 * by passing [tabContent] (one composable per [TripTab]); a tab without content is not shown.
 *
 * Leaving the feature is by lambda (the app turns them into routes): [onOpenTrip] opens a trip by id, [onPlanTrip] goes to
 * the planner (Search), [onBack] pops the trip, [onTripDeleted] leaves a deleted trip, [onOpenPlanner] opens Results with the
 * trip's plan already in the shared draft and [onPlanIt] goes to Search for a dreaming trip (with its destination to prefill).
 * The hub itself is a top-level tab and draws no app bar.
 */
fun NavGraphBuilder.tripsGraph(
    onOpenTrip: (tripId: Long) -> Unit,
    onPlanTrip: () -> Unit,
    onBack: () -> Unit = {},
    onTripDeleted: () -> Unit = onBack,
    onOpenPlanner: () -> Unit = onPlanTrip,
    onPlanIt: (destination: String?) -> Unit = { onPlanTrip() },
    tabContent: Map<TripTab, TripTabContent> = emptyMap(),
) {
    composable<TripsRoute> { TripsHubRoute(onOpenTrip = onOpenTrip, onPlanTrip = onPlanTrip) }
    composable<TripDestination> {
        TripRoute(onBack = onBack, onDeleted = onTripDeleted, onOpenPlanner = onOpenPlanner, onPlanIt = onPlanIt, tabContent = tabContent)
    }
}
