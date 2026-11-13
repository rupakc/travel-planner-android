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
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.ui.StreamingStatusAnnouncer
import io.github.rupakc.travelplanner.core.ui.journey.CompletenessMeter
import io.github.rupakc.travelplanner.feature.results.sections.CommerceRow
import io.github.rupakc.travelplanner.feature.results.sections.CommerceRowContent
import io.github.rupakc.travelplanner.feature.results.sections.LocalSelections
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSectionCard
import io.github.rupakc.travelplanner.feature.results.sections.SectionActions
import io.github.rupakc.travelplanner.feature.results.sections.commerce.CommerceContext
import io.github.rupakc.travelplanner.feature.results.sections.commerce.LocalCommerceContext
import io.github.rupakc.travelplanner.feature.results.sections.commerceRows
import io.github.rupakc.travelplanner.feature.results.ui.ActivityFilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.BestValueCard
import io.github.rupakc.travelplanner.feature.results.ui.BudgetPlannerCard
import io.github.rupakc.travelplanner.feature.results.ui.FilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.FlightFilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.HotelFilterSheet
import io.github.rupakc.travelplanner.feature.results.ui.LayoverSheet
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Width from which the screen switches to list | detail (Material expanded window class). */
private val EXPANDED_WIDTH = 840.dp
private val LIST_PANE_WIDTH = 380.dp

/** Number of lazy items above the first section (summary, banner, remix ×2, best value, budget, ease, search). */
private const val HEADER_ITEMS = 9

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
        topBar = { TpTopBar(state.request?.destinationName(state.placeNames) ?: stringResource(R.string.results_title), onBack = onBack) },
        bottomBar = { if (state.request != null) PlanBar(state.plan, onOpen = onOpenMyPlan) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        StreamingStatusAnnouncer(state.sectionStatuses)
        val selections = rememberUpdatedState(state.selections)
        CompositionLocalProvider(LocalCommerceContext provides commerce, LocalSelections provides selections) {
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
        tid = "results.results_empty_action",
    )
}

@Composable
private fun CompactLayout(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, onOpenFilters: (FilterSheet) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val rows = rememberRows(state)
    val ids = remember(state.sections) { state.sections.map { it.id } }
    val index = remember(ids, rows) { ResultsListIndex(ids, rows.mapValues { it.value.size }, HEADER_ITEMS) }
    // A tapped chip stays selected until the list scrolls for another reason (drag, fling, TalkBack scroll):
    // the last sections are too short to reach the top, so the spy alone would never pick them.
    val jumpTarget = remember { mutableStateOf<SectionId?>(null) }
    val jumps = remember { JumpTracker() }
    LaunchedEffect(listState) {
        // Scrolls that are not a jump (TalkBack scroll actions, programmatic scrolls) start a new scroll.
        snapshotFlow { listState.isScrollInProgress }.collect { if (it && !jumps.running) jumpTarget.value = null }
    }
    // A drag or fling is seen directly, even when it takes over from a running jump in the same frame.
    val userScroll = remember { UserScrollConnection { jumpTarget.value = null } }
    val spied by remember(listState, index) { derivedStateOf { spiedSection(listState.layoutInfo, index) } }
    val scrolled by remember(listState) { derivedStateOf { listState.canScrollBackward } }
    Column(Modifier.fillMaxSize()) {
        PinnedJumpRow(state.sections, active = activeSection(jumpTarget.value, spied, ids), divider = scrolled, onJump = { id ->
            index.indexOf(id)?.let { target ->
                jumpTarget.value = id
                val jump = jumps.start()
                scope.launch {
                    try {
                        if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
                    } catch (e: CancellationException) {
                        // Cut short by the user (not by a newer jump): the spy takes over again.
                        if (jumps.isLatest(jump)) jumpTarget.value = null
                        throw e
                    } finally {
                        jumps.finish()
                    }
                }
            }
        })
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(userScroll).testTag("results_list"),
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            headerItems(state, onEvent, actions)
            state.sections.forEach { section -> sectionItems(section, rows[section.id].orEmpty(), state, onEvent, actions, onOpenFilters) }
        }
    }
}

/** Counts running tap-to-jump scrolls; [isLatest] tells a jump cut short by the user from one replaced by a newer jump. */
private class JumpTracker {
    private val runningCount = AtomicInteger(0)
    private val latest = AtomicInteger(0)

    val running: Boolean get() = runningCount.get() > 0

    fun start(): Int {
        runningCount.incrementAndGet()
        return latest.incrementAndGet()
    }

    fun isLatest(jump: Int): Boolean = latest.get() == jump

    fun finish() {
        runningCount.decrementAndGet()
    }
}

/** Calls [onUserScroll] for every drag or fling step the list receives (never for programmatic scrolls). */
private class UserScrollConnection(private val onUserScroll: () -> Unit) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) onUserScroll()
        return Offset.Zero
    }
}

/** Scroll-spy: the section in view near the top of the list (null while only the header items are there). */
private fun spiedSection(info: LazyListLayoutInfo, index: ResultsListIndex): SectionId? {
    val spans = info.visibleItemsInfo.map { ItemSpan(it.index, it.offset, it.offset + it.size) }
    return firstVisibleIndex(spans, spyLine(info.viewportStartOffset, info.viewportEndOffset))?.let(index::sectionAt)
}

/**
 * The section-jump chips, pinned under the top app bar outside the scrolling list (web sticky badge strip),
 * on the screen background with a divider once the list has scrolled under it.
 */
@Composable
private fun PinnedJumpRow(sections: List<SectionUi>, active: SectionId?, divider: Boolean, onJump: (SectionId) -> Unit) {
    if (sections.isEmpty()) return
    Surface(color = TravelTheme.colors.background, modifier = Modifier.fillMaxWidth().testTag("results_section_nav")) {
        Column {
            QuickJumpRow(
                sections,
                active = active,
                onJump = onJump,
                modifier = Modifier.padding(vertical = TravelTheme.spacing.xs),
                contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter),
            )
            if (divider) HorizontalDivider()
        }
    }
}

/** Card rows per section, recomputed only when data, budget or selections change. */
@Composable
private fun rememberRows(state: ResultsUiState, expandAll: Boolean = false): Map<SectionId, List<CommerceRow>> =
    remember(state.sections, state.budget, state.selections, expandAll) {
        state.sections.associate { s ->
            val show = (s.expanded || expandAll) && !s.filterLoading && !s.notLoaded
            s.id to if (show) commerceRows(s.id, s.data, state.budget, state.selections) else emptyList()
        }
    }

/** A section header item followed by one lazy item per card (stable keys, content type per card kind). */
private fun LazyListScope.sectionItems(
    section: SectionUi,
    rows: List<CommerceRow>,
    state: ResultsUiState,
    onEvent: (ResultsEvent) -> Unit,
    actions: SectionActions,
    onOpenFilters: (FilterSheet) -> Unit,
    expandedPane: Boolean = false,
) {
    item(key = section.id.name, contentType = "section") {
        ResultsSectionCard(
            section = if (expandedPane) section.copy(expanded = true) else section,
            actions = actions,
            onToggleExpanded = { if (!expandedPane) onEvent(ResultsEvent.ToggleSection(section.id)) },
            onShowVerification = { onEvent(ResultsEvent.ShowVerification(section.id)) },
            onRunSearch = { onEvent(ResultsEvent.RetryStream) },
            cardsOutside = true,
            headerActions = { SectionHeaderActions(section.id, state, onEvent, onOpenFilters) },
        )
    }
    items(rows, key = { it.key }, contentType = { it.contentType }) { row ->
        CommerceRowContent(row, actions, Modifier.padding(start = TravelTheme.spacing.md))
    }
}

/** The fixed items above the sections; always exactly [HEADER_ITEMS] (empty ones render nothing). */
private fun LazyListScope.headerItems(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions) {
    item(key = "summary", contentType = "summary") { TripSummary(state) }
    item(key = "banner", contentType = "banner") {
        StreamBanner(state, onRetry = { onEvent(ResultsEvent.RetryStream) }, onRunSaved = { onEvent(ResultsEvent.RunSavedSearch) })
    }
    item(key = "remix_diff", contentType = "remix_diff") { state.remix?.let { RemixComparisonBanner(it, onDismiss = { onEvent(ResultsEvent.DismissRemix) }) } }
    item(key = "remix", contentType = "remix") { if (state.isDone) state.request?.let { RemixBar(it, onEvent) } }
    item(key = "best_value", contentType = "best_value") { state.bestValue?.let { BestValueCard(it, onEvent, onOpenLink = actions::onOpenLink) } }
    item(key = "completeness", contentType = "completeness") {
        val meter = state.completeness
        if (meter != null && state.hasResults) CompletenessMeter(meter)
    }
    item(key = "budget", contentType = "budget") { state.budget?.let { BudgetPlannerCard(it, onEvent) } }
    item(key = "ease", contentType = "ease") { state.confidence?.let { TravelEaseBanner(it) } }
    item(key = "search", contentType = "search") {
        ResultsSearchField(state.query, state.hiddenByQuery, onQuery = { onEvent(ResultsEvent.Query(it)) })
    }
}

@Composable
private fun ExpandedLayout(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, onOpenFilters: (FilterSheet) -> Unit) {
    val selectedId = state.selectedSection ?: state.sections.firstOrNull()?.id
    val rows = rememberRows(state, expandAll = true)
    Column(Modifier.fillMaxSize()) {
        PinnedJumpRow(state.sections, active = selectedId, divider = true, onJump = { onEvent(ResultsEvent.SelectSection(it)) })
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.width(LIST_PANE_WIDTH).fillMaxHeight().testTag("results_list_pane"),
                contentPadding = PaddingValues(TravelTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
            ) {
                headerItems(state, onEvent, actions)
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
                    sectionItems(section, rows[section.id].orEmpty(), state, onEvent, actions, onOpenFilters, expandedPane = true)
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
                    tid = "results.results_fare_calendar",
                )
            }
            WatchFareAction(state.watches.fareAvailable, state.watches.fare, onWatch = { onEvent(ResultsEvent.WatchFare) })
            TpButton(filters, onClick = { onOpenFilters(FilterSheet.Flights) }, variant = TpButtonVariant.Text, tid = "results.results_screen_tp_button")
        }

        SectionId.HOTELS -> {
            TpButton(filters, onClick = { onOpenFilters(FilterSheet.Hotels) }, variant = TpButtonVariant.Text, tid = "results.results_screen_tp_button_2")
        }

        SectionId.ACTIVITIES -> {
            TpButton(filters, onClick = { onOpenFilters(FilterSheet.Activities) }, variant = TpButtonVariant.Text, tid = "results.results_screen_tp_button_3")
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun Sheets(state: ResultsUiState, onEvent: (ResultsEvent) -> Unit, actions: SectionActions, sheet: FilterSheet?, onCloseFilters: () -> Unit) {
    fun dataOf(id: SectionId) = state.sections.firstOrNull { it.id == id }?.data
    when (sheet) {
        FilterSheet.Flights -> {
            FlightFilterSheet(state.flightFilters, dataOf(SectionId.FLIGHTS) as? FlightsData, onApply = {
                onCloseFilters()
                onEvent(ResultsEvent.ApplyFlightFilters(it))
            }, onDismiss = onCloseFilters)
        }

        FilterSheet.Hotels -> {
            HotelFilterSheet(state.hotelFilters, dataOf(SectionId.HOTELS) as? HotelsData, onApply = {
                onCloseFilters()
                onEvent(ResultsEvent.ApplyHotelFilters(it))
            }, onDismiss = onCloseFilters)
        }

        FilterSheet.Activities -> {
            ActivityFilterSheet(state.activityFilters, dataOf(SectionId.ACTIVITIES) as? ActivitiesData, state.request, onApply = {
                onCloseFilters()
                onEvent(ResultsEvent.ApplyActivityFilters(it))
            }, onDismiss = onCloseFilters)
        }

        null -> {
            Unit
        }
    }
    state.layover?.let { LayoverSheet(it, onDismiss = { onEvent(ResultsEvent.CloseLayover) }) }
    state.verificationDetail?.let { VerificationSheet(it, onOpenLink = actions::onOpenLink, onDismiss = { onEvent(ResultsEvent.DismissVerification) }) }
}
