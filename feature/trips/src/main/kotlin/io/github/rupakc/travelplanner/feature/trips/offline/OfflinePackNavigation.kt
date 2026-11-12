package io.github.rupakc.travelplanner.feature.trips.offline

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.OfflinePackRoute as OfflinePackDestinationRoute

/**
 * Registers the pack viewer. Called from the trips graph (the trip screen's wiring lands with the trip
 * tabs); leaves only through [onBack].
 */
fun NavGraphBuilder.offlinePackDestination(onBack: () -> Unit) {
    composable<OfflinePackDestinationRoute> { entry ->
        val route = entry.toRoute<OfflinePackDestinationRoute>()
        OfflinePackRoute(tripId = route.tripId, onBack = onBack)
    }
}
