package io.github.rupakc.travelplanner.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingRoute as OnboardingScreenRoute

/**
 * Welcome → 6 questions → result → feature picker (→ notification rationale on first run).
 * Edit mode: navigate to `OnboardingRoute(fromSettings = true)` (Settings → "Retake quiz"): it starts
 * from the saved answers/toggles, saves with "Save changes", never asks for notifications and
 * calls [onFinished] with `true` (also when closed with ✕, without saving).
 * First run (`fromSettings = false`) marks onboarding complete before calling [onFinished] with `false`.
 */
fun NavGraphBuilder.onboardingGraph(onFinished: (fromSettings: Boolean) -> Unit) {
    composable<OnboardingRoute> { entry ->
        val route = entry.toRoute<OnboardingRoute>()
        OnboardingScreenRoute(onFinished = { onFinished(route.fromSettings) })
    }
}
