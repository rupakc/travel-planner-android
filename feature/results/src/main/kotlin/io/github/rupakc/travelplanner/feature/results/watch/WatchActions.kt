package io.github.rupakc.travelplanner.feature.results.watch

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.WatchState

/*
 * Watch entry points, one small composable per kind. Today a tap creates the watch
 * straight away (alert mode "any drop", the web default); at integration each body
 * can open Team G's dialog (core/ui/.../watch) instead without touching the cards.
 */

/** "Watch fare" → "Watching · $412". Hidden when [available] is false (no live fares / trip not watchable). */
@Composable
fun WatchFareAction(available: Boolean, state: WatchState?, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    if (!available) return
    WatchButton(
        idle = stringResource(R.string.results_watch_fare),
        idleA11y = stringResource(R.string.results_watch_fare_a11y),
        state = state,
        onWatch = onWatch,
        modifier = modifier,
    )
}

/** "Watch price" on a hotel or activity card (named for TalkBack). */
@Composable
fun WatchPriceAction(available: Boolean, itemName: String, state: WatchState?, onWatch: () -> Unit, modifier: Modifier = Modifier) {
    if (!available) return
    WatchButton(
        idle = stringResource(R.string.results_watch_price),
        idleA11y = stringResource(R.string.results_watch_price_a11y, itemName),
        state = state,
        onWatch = onWatch,
        modifier = modifier,
    )
}

@Composable
private fun WatchButton(idle: String, idleA11y: String, state: WatchState?, onWatch: () -> Unit, modifier: Modifier) {
    val watching = state != null && !state.busy && state.watchId != null
    val price = state?.price?.let { localPrice(it, state.currency ?: "USD") }?.takeIf(String::isNotEmpty)
    val label =
        when {
            watching && price != null -> stringResource(R.string.results_watching_price, price)
            watching -> stringResource(R.string.results_watching)
            else -> idle
        }
    TpButton(
        text = label,
        onClick = { if (!watching) onWatch() },
        modifier = modifier.semantics { contentDescription = if (watching) label else idleA11y },
        variant = if (watching) TpButtonVariant.Tonal else TpButtonVariant.Text,
        loading = state?.busy == true,
        leadingIcon = if (watching) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
    )
}
