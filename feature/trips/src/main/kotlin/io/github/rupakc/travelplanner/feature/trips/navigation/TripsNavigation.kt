package io.github.rupakc.travelplanner.feature.trips.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.feature.trips.hub.TripsHubRoute

/**
 * The Trips feature graph. Today it holds the hub; later tasks add the trip screen, Today, expenses, journal,
 * recap and the offline pack here, each in its own package.
 *
 * Leaving the feature is by lambda (the app turns them into routes): [onOpenTrip] opens a trip by id,
 * [onPlanTrip] goes to the planner (Search). The hub itself is a top-level tab and draws no app bar.
 */
fun NavGraphBuilder.tripsGraph(onOpenTrip: (tripId: Long) -> Unit, onPlanTrip: () -> Unit) {
    composable<TripsRoute> { TripsHubRoute(onOpenTrip = onOpenTrip, onPlanTrip = onPlanTrip) }
}
