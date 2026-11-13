package io.github.rupakc.travelplanner.feature.social.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ConnectionsRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.ProfileRoute
import io.github.rupakc.travelplanner.feature.social.connections.ConnectionsRoute as ConnectionsScreenRoute
import io.github.rupakc.travelplanner.feature.social.profile.MyProfileRoute as MyProfileScreenRoute
import io.github.rupakc.travelplanner.feature.social.profile.PublicProfileRoute as PublicProfileScreenRoute

/**
 * Profile destinations: the caller's own profile (create / edit), a public profile by handle and the connections lists.
 * Screens leave through [onNavigate] (a route from `core:model`) and [onBack]. Reached only when `features.social >= 1`.
 */
fun NavGraphBuilder.socialProfileGraph(onBack: () -> Unit, onNavigate: (AppRoute) -> Unit) {
    composable<MyProfileRoute> {
        MyProfileScreenRoute(
            onBack = onBack,
            onOpenProfile = { onNavigate(ProfileRoute(it)) },
            onOpenConnections = { onNavigate(ConnectionsRoute(it.wire)) },
        )
    }
    composable<ProfileRoute> { PublicProfileScreenRoute(onBack = onBack) }
    composable<ConnectionsRoute> {
        ConnectionsScreenRoute(onBack = onBack, onOpenProfile = { onNavigate(ProfileRoute(it)) })
    }
}
