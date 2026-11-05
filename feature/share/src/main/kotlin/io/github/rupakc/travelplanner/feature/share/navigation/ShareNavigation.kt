package io.github.rupakc.travelplanner.feature.share.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.share.R

/** Public share viewer; opens without a session (`travelplanner://share/{token}`). */
fun NavGraphBuilder.shareGraph(onBack: () -> Unit) {
    composable<ShareRoute>(deepLinks = listOf(navDeepLink<ShareRoute>(basePath = "travelplanner://share"))) {
        ShareScreen(onBack = onBack)
    }
}

@Composable
internal fun ShareScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.share_title),
        message = stringResource(R.string.share_message),
        modifier = modifier,
        onBack = onBack,
    )
}
