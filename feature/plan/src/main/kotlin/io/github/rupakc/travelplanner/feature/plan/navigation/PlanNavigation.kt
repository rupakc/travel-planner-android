package io.github.rupakc.travelplanner.feature.plan.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.plan.R

@Suppress("UnusedParameter", "UNUSED_PARAMETER") // callbacks reserved for the feature team
fun NavGraphBuilder.planGraph(
    onOpenPlan: (planId: Long) -> Unit,
    onBack: () -> Unit,
) {
    composable<PlansRoute> { PlansScreen() }
    composable<PlanDetailRoute> { PlanDetailScreen(onBack = onBack) }
}

@Composable
internal fun PlansScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.plan_title),
        message = stringResource(R.string.plan_message),
        modifier = modifier,
        showTopBar = false,
    )
}

@Composable
internal fun PlanDetailScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.plan_detail_title),
        message = stringResource(R.string.plan_detail_message),
        modifier = modifier,
        onBack = onBack,
    )
}
