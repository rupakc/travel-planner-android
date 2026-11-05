package io.github.rupakc.travelplanner.feature.plan.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.MyPlanRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute

/**
 * My trips (top level), trip detail (deep link `travelplanner://plan/{planId}`) and My Plan.
 *
 * @param onOpenPlan open a saved plan full screen (compact widths)
 * @param onOpenResults show Results for the plan just loaded into the shared draft (no re-stream)
 * @param onPlanTrip go to Search (empty My trips)
 * @param onOpenTrips go to My trips (from My Plan)
 */
fun NavGraphBuilder.planGraph(
    onOpenPlan: (planId: Long) -> Unit,
    onBack: () -> Unit,
    onOpenResults: () -> Unit = {},
    onPlanTrip: () -> Unit = {},
    onOpenTrips: () -> Unit = {},
) {
    composable<PlansRoute> { PlansRoute(onOpenPlan = onOpenPlan, onPlanTrip = onPlanTrip, onOpenResults = onOpenResults) }
    composable<PlanDetailRoute>(deepLinks = listOf(navDeepLink<PlanDetailRoute>(basePath = PLAN_DEEP_LINK_BASE))) {
        PlanDetailRoute(onBack = onBack, onOpenResults = onOpenResults)
    }
    composable<MyPlanRoute> { MyPlanRoute(onBack = onBack, onOpenTrips = onOpenTrips) }
}

/** `travelplanner://plan` + `/{planId}` (matches [DeepLinks.PLAN_PATTERN]). */
internal val PLAN_DEEP_LINK_BASE = DeepLinks.PLAN_PATTERN.substringBefore("/{")
