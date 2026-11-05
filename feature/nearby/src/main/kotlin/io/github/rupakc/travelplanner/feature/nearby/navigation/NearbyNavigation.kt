package io.github.rupakc.travelplanner.feature.nearby.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.feature.nearby.NearMeRoute

/** Near me (deep link `travelplanner://nearby`, used by nearby and trip-start notifications). */
fun NavGraphBuilder.nearbyGraph(onBack: () -> Unit) {
    composable<NearbyRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.NEARBY_PATTERN })) { NearMeRoute(onBack = onBack) }
}
