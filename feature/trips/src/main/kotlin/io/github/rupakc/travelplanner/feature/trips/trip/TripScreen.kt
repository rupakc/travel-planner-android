package io.github.rupakc.travelplanner.feature.trips.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpDropdownMenuItem
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTab
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.PlanItinerary
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.ui.itinerary.ItineraryDayCard
import io.github.rupakc.travelplanner.core.ui.journey.CompletenessMeter
import io.github.rupakc.travelplanner.core.ui.journey.ProgressRing
import io.github.rupakc.travelplanner.feature.trips.R
import io.github.rupakc.travelplanner.feature.trips.hub.dateRange
import io.github.rupakc.travelplanner.feature.trips.hub.phaseLabel
import io.github.rupakc.travelplanner.feature.trips.prep.PrepRoute

/**
 * A trip: header, phase menu, tabs by phase and the tab content. Leaves via lambdas: [onBack] (up), [onDeleted],
 * [onOpenPlanner] (the plan is already in the shared draft: open Results) and [onPlanIt] (a destination to prefill).
 * [tabContent] is where the Today, Money, Journal, Recap and Offline tabs plug in; a tab without content is not shown.
 */
@Composable
fun TripRoute(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenPlanner: () -> Unit,
    onPlanIt: (destination: String?) -> Unit,
    modifier: Modifier = Modifier,
    tabContent: Map<TripTab, TripTabContent> = emptyMap(),
    viewModel: TripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                TripEffect.Back -> onBack()
                TripEffect.Deleted -> onDeleted()
                TripEffect.OpenPlanner -> onOpenPlanner()
                is TripEffect.PlanIt -> onPlanIt(it.destination)
            }
        }
    }
    TripScreen(state, viewModel::onEvent, tabContent, { PrepRoute(it) }, modifier)
}

/** Stateless trip screen. [prepContent] is the built-in Prep tab. */
@Composable
fun TripScreen(
    state: TripUiState,
    onEvent: (TripEvent) -> Unit,
    tabContent: Map<TripTab, TripTabContent>,
    prepContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip = state.trip
    Column(modifier.fillMaxSize()) {
        TpTopBar(
            title = trip?.title?.ifBlank { trip.destination.orEmpty() } ?: stringResource(R.string.trip_all_trips),
            onBack = { onEvent(TripEvent.Back) },
            actions = { if (trip != null) MoreMenu(onEvent) },
        )
        when {
            state.loading -> {
                SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), message = stringResource(R.string.trip_loading))
            }

            trip == null -> {
                ErrorState(state.error?.message, title = stringResource(R.string.trip_error_failed), onRetry = {
                    onEvent(TripEvent.Refresh)
                }, tid = "trips.trip_retry_1")
            }

            else -> {
                Loaded(state, trip, onEvent, tabContent, prepContent)
            }
        }
    }
    TripDialogHost(state.dialog, trip?.title.orEmpty(), onEvent)
}

@Composable
private fun Loaded(
    state: TripUiState,
    trip: Trip,
    onEvent: (TripEvent) -> Unit,
    tabContent: Map<TripTab, TripTabContent>,
    prepContent: @Composable (Modifier) -> Unit,
) {
    val available = remember(tabContent) { tabContent.keys + TripTab.Overview + TripTab.Prep }
    val tabs = TripTabs.resolve(trip.phase, state.requestedTab, available)
    LaunchedEffect(tabs.selected, trip.phase) { onEvent(TripEvent.TabShown(tabs.selected)) }
    Column(Modifier.fillMaxSize()) {
        Header(trip, state.busy, onEvent)
        state.failure?.let { failure ->
            Surface(color = TravelTheme.colors.errorContainer, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        failure.message ?: stringResource(R.string.trip_error_failed),
                        color = TravelTheme.colors.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TpButton(stringResource(R.string.trip_cancel), {
                        onEvent(TripEvent.DismissFailure)
                    }, variant = TpButtonVariant.Text, tid = "trip.failure.dismiss")
                }
            }
        }
        val label = stringResource(R.string.trip_tabs_label)
        // Keyed by the rendered list: when a requested tab is appended the row is rebuilt, so its indicator and scroll
        // state never index the previous (shorter) tab positions. The index is derived from the same list, and clamped.
        key(tabs.visible) {
            ScrollableTabRow(
                selectedTabIndex = tabs.visible.indexOf(tabs.selected).coerceIn(0, (tabs.visible.size - 1).coerceAtLeast(0)),
                edgePadding = TravelTheme.spacing.gutter,
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                tabs.visible.forEach { tab ->
                    TpTab(selected = tab == tabs.selected, onClick = {
                        onEvent(TripEvent.SelectTab(tab))
                    }, text = { Text(stringResource(tabLabel(tab))) }, tid = "trips.trip_screen_tab")
                }
            }
        }
        val selectTab = remember(onEvent) { { tab: TripTab -> onEvent(TripEvent.SelectTab(tab)) } }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalTripTabSelector provides selectTab) {
                when (tabs.selected) {
                    TripTab.Overview -> Overview(state, trip, onEvent)
                    TripTab.Prep -> prepContent(Modifier.fillMaxSize())
                    else -> tabContent[tabs.selected]?.invoke(trip, Modifier.fillMaxSize())
                }
            }
        }
    }
}

private fun tabLabel(tab: TripTab): Int =
    when (tab) {
        TripTab.Overview -> R.string.trip_tab_overview
        TripTab.Prep -> R.string.trip_tab_prep
        TripTab.Today -> R.string.trip_tab_today
        TripTab.Money -> R.string.trip_tab_money
        TripTab.Journal -> R.string.trip_tab_journal
        TripTab.Recap -> R.string.trip_tab_recap
        TripTab.Offline -> R.string.trip_tab_offline
    }

@Composable
private fun MoreMenu(onEvent: (TripEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TpIconButton(onClick = {
        open = true
    }, tid = "trips.trip_more_options") { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.trip_more_options)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        TpDropdownMenuItem(text = { Text(stringResource(R.string.trip_rename)) }, onClick = {
            open = false
            onEvent(TripEvent.OpenRename)
        }, tid = "trips.trip_rename")
        TpDropdownMenuItem(text = { Text(stringResource(R.string.trip_edit_dates)) }, onClick = {
            open = false
            onEvent(TripEvent.OpenDates)
        }, tid = "trips.trip_edit_dates")
        TpDropdownMenuItem(text = { Text(stringResource(R.string.trip_delete)) }, onClick = {
            open = false
            onEvent(TripEvent.AskDelete)
        }, tid = "trips.trip_delete")
    }
}

@Composable
private fun Header(trip: Trip, busy: Boolean, onEvent: (TripEvent) -> Unit) {
    val phase = trip.phase ?: Phase.PLANNING
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PhaseMenu(trip, phase, busy, onEvent)
            if (trip.phaseSource == "manual") {
                Text(stringResource(R.string.trip_phase_manual), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
            }
        }
        val dates = dateRange(trip.startDate, trip.endDate)
        if (dates == null) {
            TpButton(stringResource(R.string.trip_add_dates), { onEvent(TripEvent.OpenDates) }, variant = TpButtonVariant.Text, tid = "trip.add_dates")
        } else {
            Text(dates, style = TravelTheme.typography.bodyMedium)
        }
        timeLine(trip)?.let { Text(it, style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.primary) }
    }
}

/** "in 12 days" before the trip, "Day 2 of 8" on the road. */
@Composable
private fun timeLine(trip: Trip): String? {
    // Only on the road: under a stage override (Memories, Ready, ...) the dates' day number would be stale.
    if (trip.phase == Phase.ON_THE_ROAD) trip.dayIndex?.let { day -> return stringResource(R.string.trip_day_of, day, tripLengthDays(trip) ?: day) }
    return trip.daysToStart?.takeIf { it >= 0 }?.let { pluralStringResource(R.plurals.trip_countdown, it, it) }
}

@Composable
private fun PhaseMenu(trip: Trip, phase: Phase, busy: Boolean, onEvent: (TripEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = phaseLabel(phase)
    val description = stringResource(R.string.trip_phase_change, label)
    Surface(
        onClick = { open = true },
        enabled = !busy,
        color = TravelTheme.colors.secondaryContainer,
        contentColor = TravelTheme.colors.onSecondaryContainer,
        shape = TravelShape.Input,
        modifier =
            Modifier.semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Text(
            label,
            style = TravelTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm),
        )
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        Text(
            stringResource(R.string.trip_phase_menu_title),
            style = TravelTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.xs).semantics { heading() },
        )
        Phase.entries.forEach { p ->
            TpDropdownMenuItem(
                text = { Text(phaseLabel(p) + if (p == trip.phase && trip.phaseSource == "manual") " ✓" else "") },
                onClick = {
                    open = false
                    onEvent(TripEvent.SetPhase(p))
                },
                tid = "trips.trip_screen_dropdown_menu_item",
            )
        }
        TpDropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(R.string.trip_phase_follow))
                    Text(stringResource(R.string.trip_phase_follow_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            },
            onClick = {
                open = false
                onEvent(TripEvent.SetPhase(null))
            },
            tid = "trips.trip_phase_follow",
        )
    }
}

@Composable
private fun Overview(state: TripUiState, trip: Trip, onEvent: (TripEvent) -> Unit) {
    val plan = state.plan
    val days = remember(plan) { plan?.let { PlanItinerary.days(it.selections) }.orEmpty() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        val where = trip.destinations.ifEmpty { listOfNotNull(trip.destination) }.joinToString(" · ")
        if (where.isNotBlank()) item(key = "where", contentType = "where") { Text(where, style = TravelTheme.typography.titleMedium) }
        if (trip.prep.total > 0 && trip.phase != Phase.DREAMING) {
            item(key = "prep", contentType = "prep") { PrepCard(trip, onClick = { onEvent(TripEvent.SelectTab(TripTab.Prep)) }) }
        }
        state.completeness?.takeIf { trip.phase == Phase.PLANNING && state.planStatus == PlanStatus.Ready }?.let { meter ->
            item(key = "meter", contentType = "meter") { CompletenessMeter(meter, Modifier.fillMaxWidth()) }
        }
        when (state.planStatus) {
            PlanStatus.Loading -> {
                item(key = "plan-loading", contentType = "loading") { SkeletonList(message = stringResource(R.string.trip_loading)) }
            }

            PlanStatus.Failed -> {
                item(key = "plan-failed", contentType = "failed") {
                    ErrorState(null, title = stringResource(R.string.trip_plan_load_failed), onRetry = {
                        onEvent(TripEvent.RetryPlan)
                    }, tid = "trips.trip_retry_2")
                }
            }

            PlanStatus.Ready -> {
                if (state.canPlanIt) {
                    item(key = "plan-it", contentType = "action") { PlanItCard(trip, onEvent) }
                } else {
                    itemsIndexed(days, key = { i, d -> d.day.dayNumber ?: -(i + 1) }, contentType = { _, _ -> "day" }) { _, day ->
                        ItineraryDayCard(day, Modifier.fillMaxWidth())
                    }
                    item(key = "open-planner", contentType = "action") { OpenPlannerCard(onEvent) }
                }
            }
        }
    }
}

@Composable
private fun PrepCard(trip: Trip, onClick: () -> Unit) {
    val ready = stringResource(R.string.prep_ready, trip.prep.percent)
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item, onClick = onClick) {
        Row(
            Modifier.padding(TravelTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(trip.prep.percent, "${trip.prep.percent}%", ready)
            Column {
                Text(stringResource(R.string.prep_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(
                    stringResource(R.string.prep_progress, trip.prep.done, trip.prep.total),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OpenPlannerCard(onEvent: (TripEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.trip_open_planner_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        TpButton(stringResource(R.string.trip_open_planner), { onEvent(TripEvent.OpenPlanner) }, Modifier.fillMaxWidth(), tid = "trip.open_planner")
    }
}

@Composable
private fun PlanItCard(trip: Trip, onEvent: (TripEvent) -> Unit) {
    val destination = trip.destination?.takeIf { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(
            if (destination != null) stringResource(R.string.trip_plan_it_hint, destination) else stringResource(R.string.trip_plan_it_hint_no_place),
            style = TravelTheme.typography.bodyMedium,
        )
        TpButton(stringResource(R.string.trip_plan_it), { onEvent(TripEvent.PlanIt) }, Modifier.fillMaxWidth(), tid = "trip.plan_it")
    }
}
