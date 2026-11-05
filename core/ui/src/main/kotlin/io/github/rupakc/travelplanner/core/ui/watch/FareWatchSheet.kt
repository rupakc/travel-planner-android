package io.github.rupakc.travelplanner.core.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogController
import io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.TargetError
import io.github.rupakc.travelplanner.core.domain.alerts.WatchTrip
import io.github.rupakc.travelplanner.core.domain.alerts.formatDateRange
import io.github.rupakc.travelplanner.core.domain.alerts.formatUsd
import io.github.rupakc.travelplanner.core.domain.alerts.rangePosition
import io.github.rupakc.travelplanner.core.domain.alerts.routeLabel
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the user did in a watch sheet; the host forwards it to its controller ([applyTo]). */
sealed interface WatchSheetEvent {
    data class SetMode(val mode: AlertMode) : WatchSheetEvent

    data class SetTarget(val text: String) : WatchSheetEvent

    data class SetFlexible(val on: Boolean) : WatchSheetEvent

    data class SetEmail(val on: Boolean) : WatchSheetEvent

    data object Submit : WatchSheetEvent

    /** "Watch this instead" on a no-live-price suggestion. */
    data class PickSuggestion(val index: Int) : WatchSheetEvent

    /** Hotel "Not this one?" toggle. */
    data object ToggleAlternatives : WatchSheetEvent

    data class SwapTo(val index: Int) : WatchSheetEvent
}

/** Forwards [event] to a fare controller; [onWatched] gets the created watch. */
fun WatchSheetEvent.applyTo(controller: FareWatchDialogController, scope: CoroutineScope, onWatched: (FareWatch) -> Unit = {}) {
    when (this) {
        is WatchSheetEvent.SetMode -> controller.setMode(mode)
        is WatchSheetEvent.SetTarget -> controller.setTarget(text)
        is WatchSheetEvent.SetFlexible -> controller.setFlexible(on)
        is WatchSheetEvent.SetEmail -> controller.setEmail(on)
        WatchSheetEvent.Submit -> scope.launch { controller.submit()?.let(onWatched) }
        else -> Unit
    }
}

@Composable
internal fun targetErrorText(error: TargetError?, example: String = "450"): String? =
    when (error) {
        TargetError.NOT_A_PRICE -> stringResource(R.string.watch_target_error, example)
        null -> null
    }

/**
 * "Watch this fare" bottom sheet (web `WatchDialog`). Stateful host: give it a controller built
 * with `FareWatchesRepository::create`. [userEmail] enables "Also email me" when the server
 * supports email ([FareWatchDialogState.emailAvailable]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareWatchSheet(
    controller: FareWatchDialogController,
    userEmail: String?,
    onDismiss: () -> Unit,
    onSeeAlerts: (FareWatch) -> Unit,
    onWatched: (FareWatch) -> Unit = {},
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FareWatchSheetContent(state, userEmail, { it.applyTo(controller, scope, onWatched) }, onDismiss, onSeeAlerts)
    }
}

/** Stateless body of [FareWatchSheet] (tests and previews render this). */
@Composable
fun FareWatchSheetContent(
    state: FareWatchDialogState,
    userEmail: String?,
    onEvent: (WatchSheetEvent) -> Unit,
    onDismiss: () -> Unit,
    onSeeAlerts: (FareWatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip
    val done = state.done
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TravelTheme.spacing.xl)
            .padding(bottom = TravelTheme.spacing.xl)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        val title =
            when {
                done == null -> R.string.watch_fare_title
                done.alreadyWatching == true -> R.string.watch_fare_title_already
                else -> R.string.watch_fare_title_done
            }
        Text(stringResource(title), style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            listOfNotNull(routeLabel(trip), formatDateRange(trip.departureDate, trip.returnDate, currentLocale())).joinToString(" · "),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        if (done == null) FareForm(state, userEmail, onEvent, onDismiss) else FareDone(done, trip.numTravelers, onDismiss, onSeeAlerts)
    }
}

@Composable
private fun FareForm(state: FareWatchDialogState, userEmail: String?, onEvent: (WatchSheetEvent) -> Unit, onDismiss: () -> Unit) {
    val travellers = pluralStringResource(R.plurals.watch_total_for_travellers, state.trip.numTravelers, state.trip.numTravelers)
    val price = state.price
    if (price != null) {
        ToneBox(io.github.rupakc.travelplanner.core.domain.alerts.Tone.NEUTRAL) {
            PriceLead(stringResource(R.string.watch_today), formatUsd(price), travellers)
            TypicalRangeBar(state.typicalLow, state.typicalHigh, price)
        }
    }
    val check = state.targetCheck
    AlertModeGroup(
        mode = state.mode,
        onMode = { onEvent(WatchSheetEvent.SetMode(it)) },
        target = state.target,
        onTarget = { onEvent(WatchSheetEvent.SetTarget(it)) },
        currency = "USD",
        anyDropHint = stringResource(R.string.watch_fare_any_drop_hint),
        targetError = targetErrorText(state.shownTargetError),
        helper =
            when {
                check.alreadyUnder && price != null -> stringResource(R.string.watch_fare_target_already_under, formatUsd(price))
                state.typicalLow != null -> stringResource(R.string.watch_fare_target_prefilled)
                else -> travellers
            },
    )
    CheckRow(
        checked = state.flexible,
        title = stringResource(R.string.watch_flexible),
        subtitle = stringResource(R.string.watch_flexible_hint),
        onChange = { onEvent(WatchSheetEvent.SetFlexible(it)) },
    )
    if (state.emailAvailable) EmailOption(state.email, userEmail, { onEvent(WatchSheetEvent.SetEmail(it)) })
    state.error?.let { err ->
        ToneBox(io.github.rupakc.travelplanner.core.domain.alerts.Tone.WARN, live = true) {
            Text(err.message ?: stringResource(R.string.watch_fare_error))
        }
    }
    SheetButtons(
        primary = stringResource(if (state.saving) R.string.watch_fare_saving else R.string.watch_start),
        onPrimary = { onEvent(WatchSheetEvent.Submit) },
        primaryEnabled = state.shownTargetError == null,
        loading = state.saving,
        secondary = stringResource(R.string.watch_cancel),
        onSecondary = onDismiss,
    )
}

@Composable
private fun FareDone(watch: FareWatch, fallbackTravellers: Int, onDismiss: () -> Unit, onSeeAlerts: (FareWatch) -> Unit) {
    val n = watch.numTravelers ?: fallbackTravellers
    val price = watch.baselinePrice ?: watch.lastPrice
    ToneBox(io.github.rupakc.travelplanner.core.domain.alerts.Tone.GOOD, live = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text(
                    if (price != null) {
                        stringResource(R.string.watch_fare_done_price, formatUsd(price), pluralStringResource(R.plurals.watch_total_for_travellers, n, n))
                    } else {
                        stringResource(R.string.watch_fare_done_pending)
                    },
                )
                when {
                    watch.alreadyBelowTarget == true -> Text(stringResource(R.string.watch_already_below_target), style = TravelTheme.typography.titleSmall)
                    watch.alreadyLow == true -> Text(stringResource(R.string.watch_fare_already_low), style = TravelTheme.typography.titleSmall)
                }
            }
        }
    }
    SheetButtons(
        primary = stringResource(R.string.watch_done),
        onPrimary = onDismiss,
        secondary = stringResource(R.string.watch_see_alerts),
        onSecondary = { onSeeAlerts(watch) },
    )
}

/** Google's typical range with today's price marked (web `TypicalRangeBar`). */
@Composable
internal fun TypicalRangeBar(low: Double?, high: Double?, price: Double) {
    val pos = rangePosition(low, high, price) ?: return
    val where =
        when {
            price < low!! -> R.string.watch_range_below
            price > high!! -> R.string.watch_range_above
            else -> R.string.watch_range_within
        }
    val text = stringResource(where, formatUsd(low), formatUsd(high!!))
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = text }) {
        Box(Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.sm)) {
            LinearProgressIndicator(
                progress = { (pos / PERCENT).toFloat() },
                modifier = Modifier.fillMaxWidth().height(TravelTheme.spacing.sm),
                color = TravelTheme.colors.primary,
                trackColor = TravelTheme.colors.surfaceVariant,
                drawStopIndicator = {},
            )
        }
        Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

/** Cancel / primary row (stacks on narrow widths and large fonts via wrap). */
@Composable
internal fun SheetButtons(
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    loading: Boolean = false,
    showPrimary: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TpButton(secondary, onSecondary, variant = TpButtonVariant.Text)
        if (showPrimary) {
            TpButton(primary, onPrimary, enabled = primaryEnabled, loading = loading, leadingIcon = Icons.Filled.Notifications)
        }
    }
}

private const val PERCENT = 100.0

@Preview(name = "Fare sheet · light")
@Composable
private fun FareSheetPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        androidx.compose.material3.Surface {
            FareWatchSheetContent(
                FareWatchDialogState(WatchTrip("Berlin", "Lisbon", "2026-12-01", "2026-12-08", 2), price = 454.0, typicalLow = 420.0, typicalHigh = 610.0),
                null,
                {},
                {},
                {},
            )
        }
    }
}

@Preview(name = "Fare sheet · dark, done")
@Composable
private fun FareSheetDonePreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        androidx.compose.material3.Surface {
            FareWatchSheetContent(
                FareWatchDialogState(
                    WatchTrip("Berlin", "Lisbon", "2026-12-01", "2026-12-08", 2),
                    done = FareWatch(id = 1, baselinePrice = 454.0, numTravelers = 2, alreadyLow = true),
                ),
                null,
                {},
                {},
                {},
            )
        }
    }
}
