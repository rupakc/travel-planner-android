package io.github.rupakc.travelplanner.feature.social.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.SocialRoute

/**
 * Social destinations (profile, follows, blocks, ideas feed, comments, reports). Phase 5 task T5.1 only registers the
 * entry route as an empty placeholder; T5.2 and T5.3 replace it with the real screens and add their own routes.
 * The app shell shows entry points only when `features.social >= 1`.
 */
fun NavGraphBuilder.socialGraph() {
    composable<SocialRoute> { Box(Modifier.fillMaxSize()) }
}
