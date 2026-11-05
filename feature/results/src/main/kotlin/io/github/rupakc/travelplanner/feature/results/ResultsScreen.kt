package io.github.rupakc.travelplanner.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.ui.StreamingStatusAnnouncer
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSectionCard
import io.github.rupakc.travelplanner.feature.results.sections.SectionActions
import io.github.rupakc.travelplanner.feature.results.sections.commerce.CommerceContext
import io.github.rupakc.travelplanner.feature.results.sections.commerce.LocalCommerceContext
import io.github.rupakc.travelplanner.feature.results.ui.ActivityFilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.BestValueCard
import io.github.rupakc.travelplanner.feature.results.ui.BudgetPlannerCard
import io.github.rupakc.travelplanner.feature.results.ui.FilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.FlightFilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.HotelFilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.LayoverSheet
import io.github.rupakc.travelplanner.feature.results.ui.NoLivePriceSheet
import io.github.rupakc.travelplanner.feature.results.ui.PlanBar
import io.github.rupakc.travelplanner.feature.results.ui.QuickJumpRow
import io.github.rupakc.travelplanner.feature.results.ui.RemixBar
import io.github.rupakc.travelplanner.feature.results.ui.RemixComparisonBanner
import io.github.rupakc.travelplanner.feature.results.ui.ResultsSearchField
import io.github.rupakc.travelplanner.feature.results.ui.SectionNavRow
import io.github.rupakc.travelplanner.feature.results.ui.StreamBanner
import io.github.rupakc.travelplanner.feature.results.ui.TravelEaseBanner
import io.github.rupakc.travelplanner.feature.results.ui.TripSummary
import io.github.rupakc.travelplanner.feature.results.ui.VerificationSheet
import io.github.rupakc.travelplanner.feature.results.ui.destinationName
import io.github.rupakc.travelplanner.feature.results.watch.WatchFareAction
import kotlinx.coroutines.launch

/** Width from which the screen switches to list | detail (Material expanded window class). */
private val EXPANDED_WIDTH = 840.dp
private val LIST_PANE_WIDTH = 380.dp

/** Number of lazy items above the first section card (summary, banner, tools, search, jump row). */
private const val HEADER_ITEMS = 8

/**
 * Stateless results screen. Compact: one scrolling column (summary → banners → tools →
 * sections). Expanded: section list | selected section detail. A sticky My Plan bar
 * sits at the bottom; filter, layover, verification and no-live-price sheets float above.
 */
@Composable
fun ResultsScreen(
    state: ResultsUiState,
    onEvent: (ResultsEvent) -> Unit,
    actions: SectionActions,
    onBack: () -> Unit,
    onOpenMyPlan: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var sheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }
    val commerce =
        CommerceContext(
            request = state.request,
            pricingAdvisor = state.pricingAdvisor,
            daysUntilDeparture = state.daysUntilDeparture,
            budget = state.budget,
            weather = state.sections.firstOrNull { it.id == SectionId.WEATHER }?.data as? WeatherData,
            fareCalendar = state.fareCalendar,
            watches = state.watches,
            onEvent = onEvent,
        )
    Scaffold(
        modifier = modifier,
        topBar = { TpTopBar(state.request?.destinationName() ?: stringResource(R.string.results_title), onBack = onBack) },
        bottomBar = { if (state.request != null) PlanBar(state.plan, onOpen = onOpenMyPlan) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        StreamingStatusAnnouncer(state.sectionStatuses)
        CompositionLocalProvider(LocalCommerceContext provides commerce) {
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                when {
                    state.request == null -> NoSearch(onBack)
                    maxWidth >= EXPANDED_WIDTH -> ExpandedLayout(state, onEvent, actions, onOpenFilters = { sheet = it })
                    else -> CompactLayout(state, onEvent, actions, onOpenFilters = { sheet = it })
                }
            }
        }
    }
    Sheets(state, onEvent, actions, sheet, onCloseFilters = { sheet = null })
}

@Composable
private fun NoSearch(onBack: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.results_empty_title),
        message = stringResource(R.string.results_empty_message),
        actionLabel = stringResource(R.string.results_new_search),
        onAction = onBack,
    )
}

@Composable
private fun CompactLayout(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, onOpenFilters: (FilterSheet) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("results_list"),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        headerItems(state, onEvent, actions, onJump = { id ->
            val index = state.sections.indexOfFirst { it.id == id }
            if (index >= 0) scope.launch { listState.animateScrollToItem(HEADER_ITEMS + index) }
        })
        items(state.sections, key = { it.id.name }, contentType = { "section" }) { section ->
            ResultsSectionCard(
                section = section,
                actions = actions,
                onToggleExpanded = { onEvent(ResultsEvent.ToggleSection(section.id)) },
                onShowVerification = { onEvent(ResultsEvent.ShowVerification(section.id)) },
                headerActions = { SectionHeaderActions(section.id, state, onEvent, onOpenFilters) },
            )
        }
    }
}

/** The fixed items above the sections; always exactly [HEADER_ITEMS] (empty ones render nothing). */
private fun LazyListScope.headerItems(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, onJump: (SectionId) -> Unit) {
    item(key = "summary", contentType = "summary") { TripSummary(state) }
    item(key = "banner", contentType = "banner") {
        StreamBanner(state, onRetry = { onEvent(ResultsEvent.RetryStream) }, onRunSaved = { onEvent(ResultsEvent.RunSavedSearch) })
    }
    item(key = "remix_diff", contentType = "remix_diff") { state.remix?.let { RemixComparisonBanner(it, onDismiss = { onEvent(ResultsEvent.DismissRemix) }) } }
    item(key = "remix", contentType = "remix") { if (state.isDone) state.request?.let { RemixBar(it, onEvent) } }
    item(key = "best_value", contentType = "best_value") { state.bestValue?.let { BestValueCard(it, onEvent, onOpenLink = actions::onOpenLink) } }
    item(key = "budget", contentType = "budget") { state.budget?.let { BudgetPlannerCard(it, onEvent) } }
    item(key = "ease", contentType = "ease") { state.confidence?.let { TravelEaseBanner(it) } }
    item(key = "search", contentType = "search") {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            ResultsSearchField(state.query, state.hiddenByQuery, onQuery = { onEvent(ResultsEvent.Query(it)) })
            QuickJumpRow(state.sections, onJump = onJump)
        }
    }
}

@Composable
private fun ExpandedLayout(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, onOpenFilters: (FilterSheet) -> Unit) {
    val selectedId = state.selectedSection ?: state.sections.firstOrNull()?.id
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(LIST_PANE_WIDTH).fillMaxHeight().testTag("results_list_pane"),
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            headerItems(state, onEvent, actions, onJump = { onEvent(ResultsEvent.SelectSection(it)) })
            items(state.sections, key = { "nav_" + it.id.name }, contentType = { "nav" }) { section ->
                SectionNavRow(section, selected = section.id == selectedId, onClick = { onEvent(ResultsEvent.SelectSection(section.id)) })
            }
        }
        val section = state.sections.firstOrNull { it.id == selectedId }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight().testTag("results_detail_pane"),
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        ) {
            if (section != null) {
                item(key = section.id.name, contentType = "section") {
                    ResultsSectionCard(
                        section = section.copy(expanded = true),
                        actions = actions,
                        onToggleExpanded = {},
                        onShowVerification = { onEvent(ResultsEvent.ShowVerification(section.id)) },
                        headerActions = { SectionHeaderActions(section.id, state, onEvent, onOpenFilters) },
                    )
                }
            }
        }
    }
}

/** Header buttons: "Fare calendar" (pressed state) + "Watch fare" + "Filters" for flights; "Filters" for hotels/activities. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SectionHeaderActions(
    id: SectionId,
    state: ResultsUiState,
    onEvent: (ResultsEvent) -> Unit,
    onOpenFilters: (FilterSheet) -> Unit,
) {
    val filters = stringResource(R.string.results_filters)
    when (id) {
        SectionId.FLIGHTS -> {
            if (state.fareCalendar.available) {
                val pressed = stringResource(if (state.fareCalendar.open) R.string.results_state_on else R.string.results_state_off)
                TpButton(
                    stringResource(R.string.results_fare_calendar),
                    onClick = { onEvent(ResultsEvent.ToggleFareCalendar) },
                    variant = if (state.fareCalendar.open) TpButtonVariant.Tonal else TpButtonVariant.Text,
                    modifier = Modifier.semantics { stateDescription = pressed },
                )
            }
            WatchFareAction(state.watches.fareAvailable, state.watches.fare, onWatch = { onEvent(ResultsEvent.WatchFare) })
            TpButton(filters, onClick = { onOpenFilters(FilterSheet.Flights) }, variant = TpButtonVariant.Text)
        }

        SectionId.HOTELS -> TpButton(filters, onClick = { onOpenFilters(FilterSheet.Hotels) }, variant = TpButtonVariant.Text)
        SectionId.ACTIVITIES -> TpButton(filters, onClick = { onOpenFilters(FilterSheet.Activities) }, variant = TpButtonVariant.Text)
        else -> Unit
    }
}

@Composable
private fun Sheets(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, sheet: FilterSheet?, onCloseFilters: () -> Unit) {
    fun dataOf(id: SectionId) = state.sections.firstOrNull { it.id == id }?.data
    when (sheet) {
        FilterSheet.Flights -> FlightFilterSheet(state.flightFilters, dataOf(SectionId.FLIGHTS) as? FlightsData, onApply = {
            onCloseFilters()
            onEvent(ResultsEvent.ApplyFlightFilters(it))
        }, onDismiss = onCloseFilters)
        FilterSheet.Hotels -> HotelFilterSheet(state.hotelFilters, dataOf(SectionId.HOTELS) as? HotelsData, onApply = {
            onCloseFilters()
            onEvent(ResultsEvent.ApplyHotelFilters(it))
        }, onDismiss = onCloseFilters)
        FilterSheet.Activities -> ActivityFilterSheet(state.activityFilters, dataOf(SectionId.ACTIVITIES) as? ActivitiesData, state.request, onApply = {
            onCloseFilters()
            onEvent(ResultsEvent.ApplyActivityFilters(it))
        }, onDismiss = onCloseFilters)
        null -> Unit
    }
    state.layover?.let { LayoverSheet(it, onDismiss = { onEvent(ResultsEvent.CloseLayover) }) }
    state.verificationDetail?.let { VerificationSheet(it, onOpenLink = actions::onOpenLink, onDismiss = { onEvent(ResultsEvent.DismissVerification) }) }
    state.noLivePrice?.let {
        NoLivePriceSheet(it, onWatch = { s -> onEvent(ResultsEvent.WatchSuggestion(s)) }, onOpenLink = actions::onOpenLink, onDismiss = { onEvent(ResultsEvent.DismissNoLivePrice) })
    }
}
