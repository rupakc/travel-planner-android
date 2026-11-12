package io.github.rupakc.travelplanner.feature.trips.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PrepSummary
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.feature.trips.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

/** Trips hub (top-level: the shell draws the app bar). Leaves via [onOpenTrip] (a trip id) and [onPlanTrip] (the planner). */
@Composable
fun TripsHubRoute(onOpenTrip: (Long) -> Unit, onPlanTrip: () -> Unit, modifier: Modifier = Modifier, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                is TripsEffect.OpenTrip -> onOpenTrip(it.id)
                TripsEffect.PlanTrip -> onPlanTrip()
            }
        }
    }
    TripsScreen(state, viewModel::onEvent, modifier)
}

/** Stateless hub. */
@Composable
fun TripsScreen(state: TripsUiState, onEvent: (TripsEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", contentType = "header") { Header(onNew = { onEvent(TripsEvent.OpenCreate) }) }
            if (state.offline) item(key = "offline", contentType = "notice") { OfflineNotice() }
            when {
                state.loading -> {
                    item(key = "loading", contentType = "loading") { SkeletonList(message = stringResource(R.string.trips_loading)) }
                }

                state.error != null -> {
                    item(key = "error", contentType = "error") {
                        ErrorState(
                            message = state.error.message,
                            title = stringResource(R.string.trips_load_failed),
                            onRetry = { onEvent(TripsEvent.Refresh) },
                        )
                    }
                }

                state.isEmpty -> {
                    item(key = "empty", contentType = "empty") {
                        EmptyState(
                            title = stringResource(R.string.trips_empty),
                            icon = Icons.Filled.Place,
                            actionLabel = stringResource(R.string.trips_empty_plan),
                            onAction = { onEvent(TripsEvent.PlanTrip) },
                        )
                        TpButton(
                            text = stringResource(R.string.trips_empty_idea),
                            onClick = { onEvent(TripsEvent.OpenCreate) },
                            variant = TpButtonVariant.Secondary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                else -> {
                    content(state, onEvent)
                }
            }
        }
    }
    state.sheet?.let { NewTripSheetContent(it, onEvent) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.content(state: TripsUiState, onEvent: (TripsEvent) -> Unit) {
    state.pinned?.let { trip ->
        item(key = "pinned", contentType = "pinned") {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                SectionTitle(stringResource(R.string.trips_next_heading))
                TripCard(trip, pinned = true, onClick = { onEvent(TripsEvent.OpenTrip(trip.id)) })
            }
        }
    }
    state.groups.forEach { group ->
        item(key = "h-${group.phase.wire}", contentType = "phase-heading") { SectionTitle(phaseLabel(group.phase)) }
        items(group.trips, key = { it.id }, contentType = { "trip" }) { trip ->
            TripCard(trip, pinned = false, onClick = { onEvent(TripsEvent.OpenTrip(trip.id)) })
        }
    }
}

@Composable
private fun Header(onNew: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.trips_title),
            style = TravelTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TpButton(stringResource(R.string.trips_new), onNew, variant = TpButtonVariant.Tonal, leadingIcon = Icons.Filled.Add, tid = "trips.new")
    }
}

@Composable
private fun OfflineNotice() {
    Surface(color = TravelTheme.extendedColors.warningContainer, shape = TravelShape.Input, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.trips_offline_notice),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.extendedColors.onWarningContainer,
            modifier = Modifier.padding(TravelTheme.spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun phaseLabel(phase: Phase): String =
    stringResource(
        when (phase) {
            Phase.DREAMING -> R.string.trips_phase_dreaming
            Phase.PLANNING -> R.string.trips_phase_planning
            Phase.READY -> R.string.trips_phase_ready
            Phase.ON_THE_ROAD -> R.string.trips_phase_on_the_road
            Phase.MEMORIES -> R.string.trips_phase_memories
        },
    )

/** One trip. The phase chip carries colour and text; [pinned] adds the "what now" line for the phase. */
@Composable
internal fun TripCard(trip: Trip, pinned: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val phase = trip.phase ?: Phase.PLANNING
    val label = phaseLabel(phase)
    val dates = dateRange(trip.startDate, trip.endDate) ?: stringResource(R.string.trips_no_dates)
    val title = trip.title.ifBlank { trip.destination.orEmpty() }
    TpCard(modifier.fillMaxWidth(), size = TpCardSize.Item, selected = pinned, onClick = onClick) {
        Column(
            Modifier.padding(TravelTheme.spacing.md).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = TravelTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                PhaseTag(phase, label)
            }
            trip.destination?.takeIf { it.isNotBlank() && it != title }?.let {
                Text(it, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
            }
            Text(dates, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            if (pinned) Text(nextLine(trip, phase), style = TravelTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun nextLine(trip: Trip, phase: Phase): String =
    when (phase) {
        Phase.DREAMING -> stringResource(R.string.trips_next_dreaming)
        Phase.PLANNING -> stringResource(R.string.trips_next_planning, trip.prep.percent)
        Phase.READY -> stringResource(R.string.trips_next_ready)
        Phase.ON_THE_ROAD -> stringResource(R.string.trips_next_on_the_road, trip.dayIndex ?: 1, trip.destination.orEmpty())
        Phase.MEMORIES -> stringResource(R.string.trips_next_memories)
    }

@Composable
private fun PhaseTag(phase: Phase, label: String) {
    val colors = TravelTheme.colors
    val extra = TravelTheme.extendedColors
    val (bg, fg) =
        when (phase) {
            Phase.DREAMING -> colors.surfaceVariant to colors.onSurfaceVariant
            Phase.PLANNING -> extra.infoContainer to extra.info
            Phase.READY -> extra.successContainer to extra.onSuccessContainer
            Phase.ON_THE_ROAD -> colors.primaryContainer to colors.onPrimaryContainer
            Phase.MEMORIES -> extra.warningContainer to extra.onWarningContainer
        }
    Surface(color = bg, contentColor = fg, shape = TravelShape.Input) {
        Text(
            label,
            style = TravelTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xs),
        )
    }
}

private fun dateRange(start: String?, end: String?): String? {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun fmt(value: String?): String? =
        try {
            value?.let { LocalDate.parse(it).format(format) }
        } catch (_: DateTimeParseException) {
            null
        }
    val s = fmt(start) ?: return null
    val e = fmt(end)
    return if (e == null || e == s) s else "$s – $e"
}

@Preview(showBackground = true)
@Composable
private fun TripsScreenPreview() {
    val trips =
        listOf(Trip(1, "Lisbon in spring", "Lisbon", startDate = "2027-04-01", endDate = "2027-04-08", phase = Phase.PLANNING, prep = PrepSummary(3, 9, 33)))
    TravelTheme(ThemeMode.LIGHT) { TripsScreen(TripsUiState(loading = false, groups = groupByPhase(trips, null)), {}) }
}

@Preview(showBackground = true)
@Composable
private fun TripsScreenDarkPreview() {
    TravelTheme(ThemeMode.DARK) { TripsScreen(TripsUiState(loading = false), {}) }
}
