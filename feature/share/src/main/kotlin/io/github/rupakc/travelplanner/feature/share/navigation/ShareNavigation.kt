package io.github.rupakc.travelplanner.feature.share.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.share.ShareScreen
import io.github.rupakc.travelplanner.feature.share.ShareViewModel

/** App link base: `travelplanner://share/{token}`. */
internal const val SHARE_APP_LINK_BASE = "travelplanner://share"

/**
 * Public share viewer; opens without a session.
 *
 * @param onOpenPlan open a plan just saved with "Save a copy"
 * @param onPlanTrip "Plan your own trip" (Search, or Login when signed out — the app's auth gate decides)
 */
fun NavGraphBuilder.shareGraph(
    onBack: () -> Unit,
    onOpenPlan: (planId: Long) -> Unit = {},
    onPlanTrip: () -> Unit = onBack,
) {
    composable<ShareRoute>(
        // App scheme only: the manifest declares no https filter and MainActivity drops non-app data (S-M1).
        // Opening web share links would need verified App Links plus an explicit allowance in DeepLinkIntents.
        deepLinks = listOf(navDeepLink<ShareRoute>(basePath = SHARE_APP_LINK_BASE)),
    ) {
        ShareRoute(onBack = onBack, onOpenPlan = onOpenPlan, onPlanTrip = onPlanTrip)
    }
}

@Composable
internal fun ShareRoute(onBack: () -> Unit, onOpenPlan: (Long) -> Unit, onPlanTrip: () -> Unit) {
    val viewModel: ShareViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ShareScreen(
        state,
        viewModel::onEvent,
        onBack = onBack,
        onPlanOwn = onPlanTrip,
        onOpenLink = rememberLinkOpener(),
        onOpenSaved = onOpenPlan,
        snackbar = snackbar,
    )
}
