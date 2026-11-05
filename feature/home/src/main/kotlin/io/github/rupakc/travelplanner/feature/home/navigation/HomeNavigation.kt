package io.github.rupakc.travelplanner.feature.home.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.feature.home.HomeRoute as HomeScreenRoute
import io.github.rupakc.travelplanner.feature.home.search.GlobalSearchRoute as GlobalSearchScreenRoute

/**
 * Home + global search. [onPlanTrip] opens the Search tab, [onOpenNearby] Near me, [onOpenPlan] a saved
 * plan; every other destination (Search modes, Alerts, Chat, settings rows, global search) goes
 * through [onNavigate]. [onBack] leaves global search.
 */
fun NavGraphBuilder.homeGraph(
    onPlanTrip: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenPlan: (planId: Long) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val navigate: (AppRoute) -> Unit = { route ->
        when (route) {
            SearchRoute() -> onPlanTrip()
            NearbyRoute -> onOpenNearby()
            is PlanDetailRoute -> onOpenPlan(route.planId)
            else -> onNavigate(route)
        }
    }
    composable<HomeRoute> {
        HomeScreenRoute(onNavigate = navigate, viewModel = hiltViewModel())
    }
    composable<GlobalSearchRoute> {
        GlobalSearchScreenRoute(onBack = onBack, onNavigate = navigate, viewModel = hiltViewModel())
    }
}
