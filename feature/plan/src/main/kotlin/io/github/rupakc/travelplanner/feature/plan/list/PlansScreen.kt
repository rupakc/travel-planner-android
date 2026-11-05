package io.github.rupakc.travelplanner.feature.plan.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.TripFilter
import io.github.rupakc.travelplanner.feature.plan.model.TripSort
import io.github.rupakc.travelplanner.feature.plan.model.TripSummary
import io.github.rupakc.travelplanner.feature.plan.model.TripTiming
import io.github.rupakc.travelplanner.feature.plan.ui.DateBadge
import io.github.rupakc.travelplanner.feature.plan.ui.displayName
import io.github.rupakc.travelplanner.feature.plan.ui.timingLabel
import io.github.rupakc.travelplanner.feature.plan.ui.travellersLabel
import io.github.rupakc.travelplanner.feature.plan.ui.tripDates
import java.time.LocalDate

private const val CONTENT_TRIP = "trip"
private const val SKELETON_ROWS = 4

/**
 * My trips: search, filter chips, sort, pull-to-refresh and the saved plans (swipe left to
 * delete; TalkBack gets a "Delete" action instead). Top-level screen: no top bar of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlansScreen(
    state: PlansUiState,
    onEvent: (PlansEvent) -> Unit,
    onOpenTrip: (Long) -> Unit,
    onPlanTrip: () -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    val title = stringResource(R.string.plan_title)
    Column(modifier.fillMaxSize().semantics { paneTitle = title }) {
        SearchField(state.controls.query, onEvent)
        FilterRow(state.controls, onEvent)
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && !state.isLoading,
            onRefresh = { onEvent(PlansEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading -> {
                    TripSkeletons()
                }

                state.totalCount == 0 && state.error != null -> {
                    ErrorState(
                        message = state.error.message ?: stringResource(R.string.plan_load_error),
                        onRetry = { onEvent(PlansEvent.Refresh) },
                    )
                }

                state.totalCount == 0 -> {
                    EmptyState(
                        title = stringResource(R.string.plan_empty_title),
                        message = stringResource(R.string.plan_empty_message),
                        actionLabel = stringResource(R.string.plan_empty_action),
                        onAction = onPlanTrip,
                    )
                }

                else -> {
                    TripList(state, onEvent, onOpenTrip, selectedId)
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onEvent: (PlansEvent) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = { onEvent(PlansEvent.QueryChanged(it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
        placeholder = { Text(stringResource(R.string.plan_search_hint)) },
        label = { Text(stringResource(R.string.plan_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onEvent(PlansEvent.QueryChanged("")) }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.plan_search_clear))
                }
            }
        },
        singleLine = true,
        shape = TravelShape.Input,
    )
}

@Composable
private fun FilterRow(controls: TripControls, onEvent: (PlansEvent) -> Unit) {
    val group = stringResource(R.string.plan_filter_group)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = TravelTheme.spacing.gutter)
            .semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            TripFilter.UPCOMING to R.string.plan_filter_upcoming,
            TripFilter.PAST to R.string.plan_filter_past,
            TripFilter.ALL to R.string.plan_filter_all,
        ).forEach { (filter, label) ->
            TpChip(
                label = stringResource(label),
                selected = controls.filter == filter,
                onClick = { onEvent(PlansEvent.FilterChanged(filter)) },
                modifier = Modifier.semantics { selected = controls.filter == filter },
            )
        }
        TpChip(
            label = stringResource(R.string.plan_filter_shared),
            selected = controls.sharedOnly,
            onClick = { onEvent(PlansEvent.SharedOnlyChanged(!controls.sharedOnly)) },
            leadingIcon = Icons.Filled.Share,
        )
        SortChip(controls.sort, onEvent)
    }
}

@Composable
private fun SortChip(sort: TripSort, onEvent: (PlansEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = stringResource(if (sort == TripSort.TRIP_DATE) R.string.plan_sort_trip_date else R.string.plan_sort_updated)
    Box {
        TpChip(label = stringResource(R.string.plan_sort_by, current), selected = false, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(TripSort.TRIP_DATE to R.string.plan_sort_trip_date, TripSort.UPDATED to R.string.plan_sort_updated).forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        open = false
                        onEvent(PlansEvent.SortChanged(option))
                    },
                    modifier = Modifier.semantics { selected = option == sort },
                )
            }
        }
    }
}

@Composable
private fun TripList(state: PlansUiState, onEvent: (PlansEvent) -> Unit, onOpenTrip: (Long) -> Unit, selectedId: Long?) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        state.error?.let { error ->
            item(key = "banner", contentType = "banner") { StaleBanner(error) }
        }
        item(key = "count", contentType = "count") {
            Text(
                pluralStringResource(R.plurals.plan_trip_count, state.trips.size, state.trips.size),
                style = TravelTheme.typography.labelMedium,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.trips.isEmpty()) {
            item(key = "no-match", contentType = "empty") {
                EmptyState(
                    title = stringResource(R.string.plan_no_match_title),
                    message = stringResource(R.string.plan_no_match_message),
                    icon = Icons.Filled.Search,
                    actionLabel = stringResource(R.string.plan_no_match_action),
                    onAction = {
                        onEvent(PlansEvent.QueryChanged(""))
                        onEvent(PlansEvent.SharedOnlyChanged(false))
                        onEvent(PlansEvent.FilterChanged(TripFilter.ALL))
                    },
                )
            }
        }
        items(state.trips, key = { it.id }, contentType = { CONTENT_TRIP }) { trip ->
            SwipeableTripRow(trip, selected = trip.id == selectedId, onOpen = { onOpenTrip(trip.id) }, onDelete = { onEvent(PlansEvent.Delete(trip.id)) })
        }
    }
}

@Composable
private fun StaleBanner(error: AppError) {
    Surface(color = TravelTheme.extendedColors.warningContainer, contentColor = TravelTheme.extendedColors.onWarningContainer, shape = TravelShape.ItemCard) {
        Text(
            stringResource(if (error is AppError.Network) R.string.plan_offline_banner else R.string.plan_stale_banner),
            style = TravelTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(TravelTheme.spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTripRow(trip: TripSummary, selected: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val dismiss = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    val deleteLabel = stringResource(R.string.plan_delete_action)
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(TravelTheme.colors.errorContainer, TravelShape.ItemCard).padding(horizontal = TravelTheme.spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = TravelTheme.colors.onErrorContainer)
            }
        },
    ) {
        TripRow(
            trip,
            selected,
            onOpen,
            Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete().let { true } })
            },
        )
    }
}

@Composable
internal fun TripRow(trip: TripSummary, selected: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    TpCard(modifier.fillMaxWidth(), selected = selected, onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            DateBadge(trip.startDate, trip.timing)
            Spacer(Modifier.width(TravelTheme.spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
                Text(trip.displayName(), style = TravelTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    trip.destination ?: stringResource(R.string.plan_destination_unknown),
                    style = TravelTheme.typography.bodyMedium,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
                tripDates(trip.startDate, trip.endDate)?.let {
                    Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(timingLabel(trip.timing), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.primary)
                    Text(pluralStringResource(R.plurals.plan_items, trip.itemCount, trip.itemCount), style = TravelTheme.typography.labelMedium)
                    if (trip.totalCostUsd > 0) {
                        Text(stringResource(R.string.plan_planned_spend, formatPrice(trip.totalCostUsd)), style = TravelTheme.typography.labelMedium)
                    }
                    travellersLabel(trip.travellers)?.let { Text(it, style = TravelTheme.typography.labelMedium) }
                }
                if (trip.isShared) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                        Icon(Icons.Filled.Share, contentDescription = null, tint = TravelTheme.colors.secondary)
                        Text(stringResource(R.string.plan_trip_shared), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TripSkeletons() {
    val loading = stringResource(io.github.rupakc.travelplanner.core.designsystem.R.string.ds_loading)
    Column(
        Modifier.fillMaxWidth().padding(TravelTheme.spacing.gutter).semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        repeat(SKELETON_ROWS) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.width(TravelTheme.spacing.xxl + TravelTheme.spacing.xl), height = TravelTheme.spacing.xxl + TravelTheme.spacing.xl)
                Spacer(Modifier.width(TravelTheme.spacing.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    SkeletonBlock(Modifier.fillMaxWidth(SKELETON_TITLE))
                    SkeletonBlock(Modifier.fillMaxWidth(SKELETON_LINE))
                }
            }
        }
    }
}

private const val SKELETON_TITLE = 0.7f
private const val SKELETON_LINE = 0.45f

// --- previews --------------------------------------------------------------------------------

internal object PlansPreviewData {
    val trips =
        listOf(
            TripSummary(
                1,
                "The Foodie Tokyo Journey",
                "Tokyo, Japan",
                "New York",
                LocalDate.of(2027, 4, 1),
                LocalDate.of(2027, 4, 8),
                2,
                3000.0,
                listOf("food"),
                "tok",
                null,
                6,
                2480.0,
                TripTiming.UPCOMING,
            ),
            TripSummary(
                2,
                "Lisbon long weekend",
                "Lisbon, Portugal",
                null,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 4),
                1,
                null,
                emptyList(),
                null,
                null,
                3,
                0.0,
                TripTiming.PAST,
            ),
        )
}

@Preview(name = "My trips — light")
@Composable
private fun PlansLightPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        Surface { PlansScreen(PlansUiState(isLoading = false, trips = PlansPreviewData.trips, totalCount = 2), {}, {}, {}) }
    }
}

@Preview(name = "My trips — dark, offline")
@Composable
private fun PlansDarkPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        Surface {
            PlansScreen(PlansUiState(isLoading = false, trips = PlansPreviewData.trips, totalCount = 2, error = AppError.Network()), {}, {}, {})
        }
    }
}

@Preview(name = "My trips — empty")
@Composable
private fun PlansEmptyPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        Surface { PlansScreen(PlansUiState(isLoading = false), {}, {}, {}) }
    }
}

@Preview(name = "My trips — loading")
@Composable
private fun PlansLoadingPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        Surface { PlansScreen(PlansUiState(), {}, {}, {}) }
    }
}
