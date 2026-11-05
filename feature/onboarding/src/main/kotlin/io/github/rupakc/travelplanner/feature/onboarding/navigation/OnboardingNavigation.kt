package io.github.rupakc.travelplanner.feature.onboarding.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.onboarding.R

/** Welcome → 6 questions → result → feature picker. [onFinished] gets `fromSettings`. */
fun NavGraphBuilder.onboardingGraph(onFinished: (fromSettings: Boolean) -> Unit) {
    composable<OnboardingRoute> { entry ->
        val route = entry.toRoute<OnboardingRoute>()
        OnboardingScreen(onFinish = { onFinished(route.fromSettings) })
    }
}

@Composable
internal fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.onboarding_title),
        message = stringResource(R.string.onboarding_message),
        modifier = modifier,
        actions = listOf(PlaceholderAction(stringResource(R.string.onboarding_finish), onFinish)),
    )
}
