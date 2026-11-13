package io.github.rupakc.travelplanner.feature.social.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.IdeaRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ProfileRoute
import io.github.rupakc.travelplanner.core.model.navigation.PublishIdeaRoute
import io.github.rupakc.travelplanner.core.model.navigation.SocialRoute
import io.github.rupakc.travelplanner.feature.social.feed.FeedRoute
import io.github.rupakc.travelplanner.feature.social.idea.IdeaDetailRoute
import io.github.rupakc.travelplanner.feature.social.publish.PublishIdeaRoute as PublishIdeaScreenRoute

/**
 * Social destinations: the ideas feed (`SocialRoute`, also `travelplanner://feed` for social notifications), one idea with its
 * comments and "Share as an idea". Screens leave through [onBack] and [onNavigate] (a route from `core:model`). The app shell shows
 * entry points only when `features.social >= 1`; profile screens are in [socialProfileGraph].
 */
fun NavGraphBuilder.socialGraph(onBack: () -> Unit = {}, onNavigate: (AppRoute) -> Unit = {}) {
    composable<SocialRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.FEED_PATTERN })) {
        FeedRoute(onBack = onBack, onOpenIdea = { onNavigate(IdeaRoute(it)) }, onOpenPlan = { onNavigate(PlanDetailRoute(it)) })
    }
    composable<IdeaRoute> {
        IdeaDetailRoute(onBack = onBack, onOpenProfile = { onNavigate(ProfileRoute(it)) }, onOpenPlan = { onNavigate(PlanDetailRoute(it)) })
    }
    composable<PublishIdeaRoute> {
        PublishIdeaScreenRoute(onBack = onBack, onOpenProfile = { onNavigate(MyProfileRoute) }, onOpenIdea = { onNavigate(IdeaRoute(it)) })
    }
}
