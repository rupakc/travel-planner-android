package io.github.rupakc.travelplanner.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlayRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.core.model.navigation.YouRoute
import kotlin.reflect.KClass

/**
 * Navigation-suite destinations. Journey on ([forJourney] true): Trips, Explore, Alerts, Play, You (R4). Journey off (previous
 * backend or flag off): the classic Home, Search, Chat, My trips, Alerts. Settings is the top bar's (classic) or You's (journey).
 */
enum class TopLevelDestination(
    val route: AppRoute,
    val routeClass: KClass<out AppRoute>,
    val icon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    HOME(HomeRoute, HomeRoute::class, Icons.Filled.Home, R.string.nav_home),
    SEARCH(SearchRoute(), SearchRoute::class, Icons.Filled.Search, R.string.nav_search),
    CHAT(ChatRoute(), ChatRoute::class, Icons.AutoMirrored.Filled.Send, R.string.nav_chat),
    PLANS(PlansRoute, PlansRoute::class, Icons.AutoMirrored.Filled.List, R.string.nav_plans),
    ALERTS(AlertsRoute(), AlertsRoute::class, Icons.Filled.Notifications, R.string.nav_alerts),
    TRIPS(TripsRoute, TripsRoute::class, Icons.Filled.Place, R.string.nav_trips),
    EXPLORE(SearchRoute(), SearchRoute::class, Icons.Filled.Search, R.string.nav_explore),
    PLAY(PlayRoute, PlayRoute::class, Icons.Filled.Star, R.string.nav_play),
    YOU(YouRoute, YouRoute::class, Icons.Filled.Person, R.string.nav_you),
    ;

    /** The `ui_tap` tid of this bar / rail item: `nav.<key lowercase>`. */
    val telemetryId: String get() = "nav.${name.lowercase()}"

    companion object {
        private val CLASSIC = listOf(HOME, SEARCH, CHAT, PLANS, ALERTS)
        private val JOURNEY = listOf(TRIPS, EXPLORE, ALERTS, PLAY, YOU)

        /** The tabs shown for the journey flag. */
        fun forJourney(journey: Boolean): List<TopLevelDestination> = if (journey) JOURNEY else CLASSIC
    }
}
