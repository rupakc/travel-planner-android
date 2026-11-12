package io.github.rupakc.travelplanner.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingRoute as OnboardingScreenRoute

/**
 * The first run has the web's five steps: welcome → how you travel (6 questions, result, feature picker) →
 * home airport + passport → notifications (only when the permission is still needed) → first trip or explore.
 * "Plan my first trip" calls [onPlanFirstTrip] when given (else [onFinished]); every other end calls [onFinished].
 * Skip setup sends `skipped: true` and counts as seen. Gate: [io.github.rupakc.travelplanner.feature.onboarding.OnboardingGate].
 * Edit mode: navigate to `OnboardingRoute(fromSettings = true)` (Settings → "Retake quiz"): it starts
 * from the saved answers/toggles, saves with "Save changes", never asks for notifications and
 * calls [onFinished] with `true` (also when closed with ✕, without saving).
 * First run (`fromSettings = false`) marks onboarding complete before calling [onFinished] with `false`.
 */
fun NavGraphBuilder.onboardingGraph(onFinished: (fromSettings: Boolean) -> Unit, onPlanFirstTrip: (() -> Unit)? = null) {
    composable<OnboardingRoute> { entry ->
        val route = entry.toRoute<OnboardingRoute>()
        OnboardingScreenRoute(
            onFinished = { planFirstTrip ->
                if (planFirstTrip && !route.fromSettings && onPlanFirstTrip != null) onPlanFirstTrip() else onFinished(route.fromSettings)
            },
        )
    }
}
