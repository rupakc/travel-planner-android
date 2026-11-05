package io.github.rupakc.travelplanner.feature.alerts.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.alerts.R
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.NotificationsRoute

/** Watches (deep link parity with web `/alerts?kind=&watch=`) and the notifications inbox. */
@Suppress("UnusedParameter", "UNUSED_PARAMETER") // callbacks reserved for the feature team
fun NavGraphBuilder.alertsGraph(
    onReplan: () -> Unit,
    onBack: () -> Unit,
) {
    composable<AlertsRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ALERTS_PATTERN }),
    ) { AlertsScreen() }
    composable<NotificationsRoute> { NotificationsScreen(onBack = onBack) }
}

@Composable
internal fun AlertsScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.alerts_title),
        message = stringResource(R.string.alerts_message),
        modifier = modifier,
        showTopBar = false,
    )
}

@Composable
internal fun NotificationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.alerts_notifications_title),
        message = stringResource(R.string.alerts_notifications_message),
        modifier = modifier,
        onBack = onBack,
    )
}
