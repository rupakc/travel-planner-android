package io.github.rupakc.travelplanner.feature.social.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ConnectionsRoute
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.MyProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.ProfileRoute
import io.github.rupakc.travelplanner.feature.social.connections.ConnectionsRoute as ConnectionsScreenRoute
import io.github.rupakc.travelplanner.feature.social.profile.MyProfileRoute as MyProfileScreenRoute
import io.github.rupakc.travelplanner.feature.social.profile.PublicProfileRoute as PublicProfileScreenRoute

/**
 * Profile destinations: the caller's own profile (create / edit), a public profile by handle and the connections lists.
 * Screens leave through [onNavigate] (a route from `core:model`) and [onBack]. Reached only when `features.social >= 1`.
 */
fun NavGraphBuilder.socialProfileGraph(
    onBack: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    enabled: Boolean = true,
    onBlocked: () -> Unit = {},
) {
    composable<MyProfileRoute> {
        SocialGate(enabled, onBlocked) {
            MyProfileScreenRoute(
                onBack = onBack,
                onOpenProfile = { onNavigate(ProfileRoute(it)) },
                onOpenConnections = { onNavigate(ConnectionsRoute(it.wire)) },
            )
        }
    }
    composable<ProfileRoute>(deepLinks = listOf(navDeepLink<ProfileRoute>(basePath = DeepLinks.PROFILE_PATTERN.substringBefore("/{")))) {
        SocialGate(enabled, onBlocked) { PublicProfileScreenRoute(onBack = onBack) }
    }
    composable<ConnectionsRoute> {
        SocialGate(enabled, onBlocked) { ConnectionsScreenRoute(onBack = onBack, onOpenProfile = { onNavigate(ProfileRoute(it)) }) }
    }
}
