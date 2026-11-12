package io.github.rupakc.travelplanner.feature.share.myshares

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.MySharesRoute

/** The caller's own share links (`MySharesRoute`), reached from the You tab when `features.sharing` is on. */
fun NavGraphBuilder.mySharesGraph(onBack: () -> Unit) {
    composable<MySharesRoute> { MySharesRouteScreen(onBack = onBack) }
}
