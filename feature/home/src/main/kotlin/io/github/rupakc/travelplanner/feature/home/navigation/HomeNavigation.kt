package io.github.rupakc.travelplanner.feature.home.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.home.R
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute

@Suppress("UnusedParameter", "UNUSED_PARAMETER") // callbacks reserved for the feature team
fun NavGraphBuilder.homeGraph(
    onPlanTrip: () -> Unit,
    onOpenNearby: () -> Unit,
    onOpenPlan: (planId: Long) -> Unit,
) {
    composable<HomeRoute> { HomeScreen(onPlanTrip = onPlanTrip, onOpenNearby = onOpenNearby) }
}

@Composable
internal fun HomeScreen(onPlanTrip: () -> Unit, onOpenNearby: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.home_title),
        message = stringResource(R.string.home_message),
        modifier = modifier,
        showTopBar = false,
        actions = listOf(
            PlaceholderAction(stringResource(R.string.home_plan_trip), onPlanTrip),
            PlaceholderAction(stringResource(R.string.home_near_me), onOpenNearby),
        ),
    )
}
