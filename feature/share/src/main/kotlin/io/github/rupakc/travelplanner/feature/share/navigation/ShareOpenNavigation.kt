package io.github.rupakc.travelplanner.feature.share.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.ShareOpenLinkRoute
import io.github.rupakc.travelplanner.feature.share.open.PasteShareLinkScreen

/** The paste-a-share-link screen (R6). [onOpen] receives a validated share token, which the app opens as `ShareRoute(token)`. */
fun NavGraphBuilder.shareOpenGraph(onBack: () -> Unit, onOpen: (token: String) -> Unit) {
    composable<ShareOpenLinkRoute> { PasteShareLinkScreen(onBack = onBack, onOpen = onOpen) }
}
