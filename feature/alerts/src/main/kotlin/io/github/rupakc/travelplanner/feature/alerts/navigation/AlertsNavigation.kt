package io.github.rupakc.travelplanner.feature.alerts.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.AlertPrefsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.NotificationsRoute
import io.github.rupakc.travelplanner.feature.alerts.NotificationsInboxRoute
import io.github.rupakc.travelplanner.feature.alerts.WatchedPricesRoute
import io.github.rupakc.travelplanner.feature.alerts.v2.AlertPrefsRoute as AlertPrefsScreenRoute

/**
 * Watched prices (deep link parity with web `/alerts?kind=&watch=`) and the notifications inbox.
 * [onReplan]: a re-plan search was started (show its results). [onOpenAlert]: open Alerts focused
 * on a watch (kind null = fare watch). [onSearch]: the empty state's "Search flights".
 */
fun NavGraphBuilder.alertsGraph(
    onReplan: () -> Unit,
    onBack: () -> Unit,
    onOpenAlert: (kind: String?, watchId: Long?) -> Unit = { _, _ -> },
    onSearch: () -> Unit = {},
    onOpenPrefs: () -> Unit = {},
) {
    composable<AlertsRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ALERTS_PATTERN }),
    ) { WatchedPricesRoute(onReplanned = onReplan, onSearch = onSearch, onOpenPrefs = onOpenPrefs) }
    composable<AlertPrefsRoute> { AlertPrefsScreenRoute(onBack = onBack) }
    composable<NotificationsRoute> {
        NotificationsInboxRoute(onBack = onBack, onOpenAlert = { link -> onOpenAlert(link.kind, link.watchId) })
    }
}
