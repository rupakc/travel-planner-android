package io.github.rupakc.travelplanner.feature.play.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.PlayRoute
import io.github.rupakc.travelplanner.feature.play.PlayRoute as PlayScreenRoute

/** Play tab (game progress and bucket list). The shell wires this (Task 17); [onHidden] fires when the game is off. */
fun NavGraphBuilder.playGraph(onHidden: () -> Unit) {
    composable<PlayRoute> { PlayScreenRoute(onHidden = onHidden) }
}
