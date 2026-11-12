package io.github.rupakc.travelplanner.feature.share.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import io.github.rupakc.travelplanner.core.model.navigation.ShareCreateRoute
import io.github.rupakc.travelplanner.feature.share.create.ShareCreateDialogRoute

/**
 * The Share links 2.0 dialog (`ShareCreateRoute`). Features open it through `LocalShareLauncher`; the app shell turns that
 * into a navigation to the route. [onClose] pops it.
 */
fun NavGraphBuilder.shareCreateGraph(onClose: () -> Unit) {
    dialog<ShareCreateRoute> { ShareCreateDialogRoute(onClose = onClose) }
}
