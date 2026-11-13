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
 * on a watch (kind null = fare watch). [onSearch]: the empty state's "Search flights". [onOpenTrip]: open a trip's Today tab
 * (a trip-phase row was tapped).
 * [onOpenIdea] / [onOpenProfile]: open the idea or profile a social row names (null: the feed).
 * [onOpenH2h]: open a head-to-head match (a quiz invite or result row); [onOpenPlay]: the Play tab (daily quiz row).
 * The inbox itself has the deep link `travelplanner://notifications`.
 */
fun NavGraphBuilder.alertsGraph(
    onReplan: () -> Unit,
    onBack: () -> Unit,
    onOpenAlert: (kind: String?, watchId: Long?) -> Unit = { _, _ -> },
    onSearch: () -> Unit = {},
    onOpenPrefs: () -> Unit = {},
    onOpenTrip: (tripId: Long) -> Unit = {},
    onOpenFeed: (() -> Unit)? = null,
    onOpenH2h: (matchId: String) -> Unit = {},
    onOpenPlay: () -> Unit = {},
    onOpenIdea: ((ideaId: Long) -> Unit)? = null,
    onOpenProfile: ((handle: String) -> Unit)? = null,
) {
    composable<AlertsRoute>(
        deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ALERTS_PATTERN }),
    ) { WatchedPricesRoute(onReplanned = onReplan, onSearch = onSearch, onOpenPrefs = onOpenPrefs) }
    composable<AlertPrefsRoute> { AlertPrefsScreenRoute(onBack = onBack) }
    composable<NotificationsRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.NOTIFICATIONS_PATTERN })) {
        NotificationsInboxRoute(
            onBack = onBack,
            onOpenAlert = { link -> onOpenAlert(link.kind, link.watchId) },
            onOpenTrip = onOpenTrip,
            onOpenFeed =
                onOpenFeed ?: {},
            onOpenH2h = onOpenH2h,
            onOpenPlay = onOpenPlay,
            // A social row naming an idea or profile opens it; without the entry (social off) it falls back to the feed, else nothing.
            onOpenIdea = onOpenIdea ?: { _: Long -> onOpenFeed?.invoke().let { } },
            onOpenProfile = onOpenProfile ?: { _: String -> onOpenFeed?.invoke().let { } },
        )
    }
}
