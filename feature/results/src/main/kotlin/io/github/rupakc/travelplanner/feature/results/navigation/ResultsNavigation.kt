package io.github.rupakc.travelplanner.feature.results.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.core.ui.watch.FareWatchSheet
import io.github.rupakc.travelplanner.core.ui.watch.PriceWatchSheet
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.ResultsEvent
import io.github.rupakc.travelplanner.feature.results.ResultsMessage
import io.github.rupakc.travelplanner.feature.results.ResultsScreen
import io.github.rupakc.travelplanner.feature.results.ResultsUiState
import io.github.rupakc.travelplanner.feature.results.ResultsViewModel
import io.github.rupakc.travelplanner.feature.results.WatchSheet
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection
import io.github.rupakc.travelplanner.feature.results.sections.SectionActions
import io.github.rupakc.travelplanner.feature.results.sections.SelectionsSource
import io.github.rupakc.travelplanner.feature.results.sections.info.StressRecheckController
import kotlinx.coroutines.flow.StateFlow

/**
 * Streamed results + compare table.
 * [onOpenMyPlan] opens the My Plan sheet (Team D's route); [onOpenAlerts] opens Alerts after a watch.
 */
fun NavGraphBuilder.resultsGraph(
    onBack: () -> Unit,
    onOpenMyPlan: () -> Unit = {},
    onOpenAlerts: () -> Unit = {},
) {
    composable<ResultsRoute> { ResultsRoute(onBack = onBack, onOpenMyPlan = onOpenMyPlan, onOpenAlerts = onOpenAlerts) }
}

/** Wires [ResultsViewModel] to [ResultsScreen]: state, snackbar messages, Custom Tabs and section actions. */
@Composable
internal fun ResultsRoute(onBack: () -> Unit, onOpenMyPlan: () -> Unit, onOpenAlerts: () -> Unit, viewModel: ResultsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = rememberLinkOpener()
    val snackbar = remember { SnackbarHostState() }
    val latest by rememberUpdatedState(state)
    val actions = remember(viewModel, open) { ResultsSectionActions(viewModel, open) { latest } }
    val context = LocalContext.current
    val seeAlerts = stringResource(R.string.results_see_alerts)
    val undo = stringResource(R.string.results_undo)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        // Only while visible (M8): messages that arrive in the background wait in the channel.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.messages.collect { message ->
                val text = messageText(context, message)
                val action =
                    when (message) {
                        is ResultsMessage.FareWatched, is ResultsMessage.PriceWatched -> seeAlerts
                        ResultsMessage.NewPlanStarted -> undo
                        else -> null
                    }
                val result = snackbar.showSnackbar(text, actionLabel = action, withDismissAction = action == null, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    if (message == ResultsMessage.NewPlanStarted) viewModel.onEvent(ResultsEvent.UndoNewPlan) else onOpenAlerts()
                }
            }
        }
    }
    ResultsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        actions = actions,
        onBack = onBack,
        onOpenMyPlan = onOpenMyPlan,
        snackbarHostState = snackbar,
    )
    // Team G's watch sheets (web FareWatchDialog / PriceWatchDialog) instead of creating watches directly.
    val sheet by viewModel.watchSheet.collectAsStateWithLifecycle()
    val email by viewModel.userEmail.collectAsStateWithLifecycle()
    when (val current = sheet) {
        is WatchSheet.Fare -> {
            FareWatchSheet(
                controller = current.controller,
                userEmail = email,
                onDismiss = viewModel::dismissWatchSheet,
                onSeeAlerts = {
                    viewModel.dismissWatchSheet()
                    onOpenAlerts()
                },
                onWatched = viewModel::onFareWatched,
            )
        }

        is WatchSheet.Price -> {
            PriceWatchSheet(
                controller = current.controller,
                userEmail = email,
                onDismiss = viewModel::dismissWatchSheet,
                onSeeAlerts = {
                    viewModel.dismissWatchSheet()
                    onOpenAlerts()
                },
                onWatched = { viewModel.onPriceWatched(current.kind, current.itemName, it) },
                onEvent = viewModel::onPriceSheetEvent,
            )
        }

        null -> {
            Unit
        }
    }
}

/** The one [SectionActions] implementation: plan toggles, Custom Tabs, watches and retries go to the ViewModel. */
internal class ResultsSectionActions(
    private val viewModel: ResultsViewModel,
    private val openLink: (String) -> Unit,
    private val state: () -> ResultsUiState,
) : SectionActions, SelectionsSource {
    private val onEvent: (ResultsEvent) -> Unit = viewModel::onEvent

    // Team E's members: observed selections, synchronous updates, the results on screen, the editing lock
    // and the retained health re-check.
    override val selections: StateFlow<PlanSelections> get() = viewModel.selections

    override fun onUpdateSelections(transform: (PlanSelections) -> PlanSelections) = viewModel.updateSelections(transform)

    override val results: StateFlow<SearchResultsState> get() = viewModel.results

    override val itineraryEditingLocked: Boolean get() = viewModel.itineraryEditingLocked

    override suspend fun recheckStressTest(payload: StressTestPayload): AppResult<StressTestData> = viewModel.recheckStressTest(payload)

    override val stressRecheck: StressRecheckController get() = viewModel.stressRecheck

    override val selectionsNow: io.github.rupakc.travelplanner.core.model.plan.PlanSelections get() = state().selections

    override fun onToggle(item: PlanItem) = onEvent(ResultsEvent.Toggle(item))

    override fun isSelected(item: PlanItem): Boolean = io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules.isSelected(state().selections, item)

    override fun onOpenLink(url: String) {
        if (url.startsWith("https://") || url.startsWith("http://")) openLink(url)
    }

    override fun onWatchFare(flight: Flight) = onEvent(ResultsEvent.WatchFare)

    override fun onWatchHotel(hotel: Hotel) = onEvent(ResultsEvent.WatchHotel(hotel))

    override fun onWatchActivity(activity: Activity) = onEvent(ResultsEvent.WatchActivity(activity))

    override fun onRetry(section: ResultsSection) = onEvent(ResultsEvent.RetrySection(section.id))
}

/** Snackbar copy (web watch dialog / results messages). */
internal fun messageText(context: android.content.Context, message: ResultsMessage): String {
    val locale = context.resources.configuration.locales[0]
    return when (message) {
        is ResultsMessage.FareWatched -> {
            val base =
                if (message.price != null) {
                    context.getString(
                        R.string.results_fare_watched,
                        formatPrice(message.price, locale = locale),
                        context.resources.getQuantityString(R.plurals.results_fare_travellers, message.travellers, message.travellers),
                    )
                } else {
                    context.getString(R.string.results_fare_watched_pending)
                }
            listOfNotNull(
                if (message.alreadyWatching) context.getString(R.string.results_already_watching) else null,
                base,
                if (message.alreadyLow) context.getString(R.string.results_fare_already_low) else null,
            ).joinToString(" ")
        }

        is ResultsMessage.PriceWatched -> {
            val price = message.price?.let { formatPrice(it, message.currency ?: "USD", locale = locale) }?.takeIf(String::isNotEmpty)
            listOfNotNull(
                if (message.alreadyWatching) context.getString(R.string.results_already_watching) else null,
                price?.let { context.getString(R.string.results_price_watched, it) } ?: context.getString(R.string.results_price_watched_pending),
            ).joinToString(" ")
        }

        is ResultsMessage.WatchFailed -> {
            message.message ?: context.getString(if (message.kind == "fare") R.string.results_watch_fare_failed else R.string.results_watch_price_failed)
        }

        ResultsMessage.WatchUnavailable -> {
            context.getString(R.string.results_watch_unavailable)
        }

        ResultsMessage.NewPlanStarted -> {
            context.getString(R.string.results_new_plan_started)
        }

        is ResultsMessage.RetryFailed -> {
            message.message ?: context.getString(R.string.results_retry_failed)
        }
    }
}
