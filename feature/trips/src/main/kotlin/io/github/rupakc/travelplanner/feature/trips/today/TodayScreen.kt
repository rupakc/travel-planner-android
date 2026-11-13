package io.github.rupakc.travelplanner.feature.trips.today

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.ui.game.PassportStamp
import io.github.rupakc.travelplanner.core.ui.itinerary.ItineraryDayCard
import io.github.rupakc.travelplanner.core.ui.share.ShareEntryButton
import io.github.rupakc.travelplanner.feature.trips.R
import io.github.rupakc.travelplanner.feature.trips.trip.LocalTripTabSelector
import io.github.rupakc.travelplanner.feature.trips.trip.TripTab
import io.github.rupakc.travelplanner.feature.trips.trip.TripTabContent

/**
 * The Today tab for `TripTabContent` (`tripsGraph(tabContent = mapOf(TripTab.Today to todayTabContent(...)))`).
 * [onOpenEmergency] opens the offline pack's emergency card and [onAddExpense] the expense form, both with the
 * trip id; a button whose lambda is null is not shown (those screens come with the offline pack and expenses).
 */
fun todayTabContent(onOpenEmergency: ((Long) -> Unit)? = null, onAddExpense: ((Long) -> Unit)? = null): TripTabContent =
    { trip, modifier ->
        TodayRoute(trip, onOpenEmergency, onAddExpense, modifier)
    }

@Composable
fun TodayRoute(
    trip: Trip,
    onOpenEmergency: ((Long) -> Unit)?,
    onAddExpense: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Inside a trip the Money tab is one tap away: switch to it (a navigate to the route already on top does nothing).
    val selectTab = LocalTripTabSelector.current
    val addExpense: (() -> Unit)? =
        when {
            selectTab != null -> ({ selectTab(TripTab.Money) })
            onAddExpense != null -> ({ onAddExpense(trip.id) })
            else -> null
        }
    TodayScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onDirections = { slot ->
            TodayRules.directionsQuery(slot, state.city)?.let { q ->
                if (openMaps(context, q)) viewModel.onEvent(TodayEvent.DirectionsOpened)
            }
        },
        onOpenEmergency = onOpenEmergency?.let { f -> { f(trip.id) } },
        onAddExpense = addExpense,
        modifier = modifier,
    )
}

/** A maps intent for [query]; false when no app handles it. */
private fun openMaps(context: Context, query: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

@Composable
fun TodayScreen(
    state: TodayUiState,
    onEvent: (TodayEvent) -> Unit,
    onDirections: (ScheduledSlot) -> Unit,
    onOpenEmergency: (() -> Unit)?,
    onAddExpense: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    when {
        state.loading -> {
            SkeletonList(modifier, message = stringResource(R.string.trip_loading))
        }

        state.failed -> {
            ErrorState(null, modifier, title = stringResource(R.string.today_load_failed), onRetry = { onEvent(TodayEvent.Retry) }, tid = "trips.today_retry")
        }

        else -> {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(TravelTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            ) {
                item(key = "header", contentType = "header") { Header(state) }
                item(key = "checkin", contentType = "checkin") { CheckinCard(state, onEvent) }
                state.nextUp?.let { slot -> item(key = "next", contentType = "next") { NextUpCard(slot, onDirections) } }
                item(key = "quick", contentType = "quick") { QuickActions(onOpenEmergency, onAddExpense) }
                // "Day N in Place": only while the trip is on (the server gives a day index), and only when sharing is on.
                val trip = state.trip
                if (trip != null && state.dayNumber != null) {
                    item(key = "share", contentType = "share") {
                        ShareEntryButton(
                            ShareKind.STATUS,
                            trip.id,
                            Modifier.fillMaxWidth(),
                            tid = "today.share_status",
                            label = io.github.rupakc.travelplanner.core.ui.R.string.share_entry_status,
                        )
                    }
                }
                val day = state.day
                if (day == null || day.slots.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(stringResource(R.string.today_nothing), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
                    }
                } else {
                    item(key = "day", contentType = "day") { ItineraryDayCard(day, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun Header(state: TodayUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        state.dayNumber?.let {
            Text(
                stringResource(R.string.today_day, it),
                style = TravelTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        state.city?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.titleMedium) }
    }
}

@Composable
private fun CheckinCard(state: TodayUiState, onEvent: (TodayEvent) -> Unit) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TpButton(
                text = stringResource(if (state.checkedIn) R.string.today_checked_in else R.string.today_checkin),
                onClick = { onEvent(TodayEvent.CheckIn) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.checkedIn,
                loading = state.sending,
                tid = "today.checkin",
            )
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                state.streak?.takeIf { it.current > 0 }?.let {
                    Text(stringResource(R.string.today_streak, it.current), style = TravelTheme.typography.bodyMedium)
                }
                if (state.newCountry) {
                    val code = state.countryCode
                    if (state.gameVisible && code != null) {
                        PassportStamp(code, state.city)
                    } else {
                        Text(stringResource(R.string.today_new_country), style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.primary)
                    }
                }
                state.failure?.let { f ->
                    val text =
                        when (f.problem) {
                            TodayRules.CheckinProblem.OutOfTrip -> stringResource(R.string.today_err_out_of_trip)
                            TodayRules.CheckinProblem.DayInvalid -> stringResource(R.string.today_err_day_invalid)
                            TodayRules.CheckinProblem.Other -> f.message ?: stringResource(R.string.today_err_generic)
                        }
                    Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.error)
                }
            }
        }
    }
}

@Composable
private fun NextUpCard(slot: ScheduledSlot, onDirections: (ScheduledSlot) -> Unit) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(stringResource(R.string.today_next_up), style = TravelTheme.typography.labelMedium, modifier = Modifier.semantics { heading() })
            Text(slot.slot.activity ?: slot.slot.location.orEmpty(), style = TravelTheme.typography.titleMedium)
            slot.startTime?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            if (TodayRules.directionsQuery(slot, null) != null) {
                TpButton(
                    stringResource(R.string.today_directions),
                    { onDirections(slot) },
                    Modifier.fillMaxWidth(),
                    variant = TpButtonVariant.Secondary,
                    tid = "today.directions",
                )
            }
        }
    }
}

@Composable
private fun QuickActions(onOpenEmergency: (() -> Unit)?, onAddExpense: (() -> Unit)?) {
    if (onOpenEmergency == null && onAddExpense == null) return
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        onOpenEmergency?.let {
            TpButton(stringResource(R.string.today_emergency), it, Modifier.fillMaxWidth(), variant = TpButtonVariant.Secondary, tid = "today.emergency")
        }
        onAddExpense?.let {
            TpButton(stringResource(R.string.today_add_expense), it, Modifier.fillMaxWidth(), variant = TpButtonVariant.Secondary, tid = "today.add_expense")
        }
    }
}
