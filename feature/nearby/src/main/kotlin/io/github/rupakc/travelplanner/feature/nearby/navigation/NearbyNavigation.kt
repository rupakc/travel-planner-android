package io.github.rupakc.travelplanner.feature.nearby.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.nearby.R
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute

fun NavGraphBuilder.nearbyGraph(onBack: () -> Unit) {
    composable<NearbyRoute>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.NEARBY_PATTERN })) { NearbyScreen(onBack = onBack) }
}

@Composable
internal fun NearbyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.nearby_title),
        message = stringResource(R.string.nearby_message),
        modifier = modifier,
        onBack = onBack,
    )
}
