package io.github.rupakc.travelplanner.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import kotlin.reflect.KClass

/** Navigation-suite destinations (spec: Home, Search, Chat, My trips, Alerts; Settings from the top bar). */
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
}
