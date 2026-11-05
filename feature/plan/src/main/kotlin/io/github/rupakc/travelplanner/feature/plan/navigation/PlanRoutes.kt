package io.github.rupakc.travelplanner.feature.plan.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.detail.DetailNavigation
import io.github.rupakc.travelplanner.feature.plan.detail.PlanDetailScreen
import io.github.rupakc.travelplanner.feature.plan.detail.PlanDetailViewModel
import io.github.rupakc.travelplanner.feature.plan.export.ExportEffects
import io.github.rupakc.travelplanner.feature.plan.list.PlansEvent
import io.github.rupakc.travelplanner.feature.plan.list.PlansMessage
import io.github.rupakc.travelplanner.feature.plan.list.PlansScreen
import io.github.rupakc.travelplanner.feature.plan.list.PlansViewModel
import io.github.rupakc.travelplanner.feature.plan.myplan.MyPlanScreen
import io.github.rupakc.travelplanner.feature.plan.myplan.MyPlanViewModel
import io.github.rupakc.travelplanner.feature.plan.ui.PanePlaceholder
import kotlinx.coroutines.flow.Flow

/** Width from which My trips shows list | detail side by side (window size class "expanded"). */
private val ListDetailMinWidth = 840.dp

/** Share of the width the list takes in list-detail. */
private const val LIST_PANE_WEIGHT = 0.42f
private const val DETAIL_PANE_WEIGHT = 1f - LIST_PANE_WEIGHT

/** My trips; on expanded widths the selected trip opens beside the list. */
@Composable
internal fun PlansRoute(onOpenPlan: (Long) -> Unit, onPlanTrip: () -> Unit, onOpenResults: () -> Unit) {
    val viewModel: PlansViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    PlansMessages(state.message, snackbar, viewModel::onEvent)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= ListDetailMinWidth) {
            Row(Modifier.fillMaxSize()) {
                PlansScreen(state, viewModel::onEvent, onOpenTrip = {
                    selected = it
                }, onPlanTrip = onPlanTrip, modifier = Modifier.weight(LIST_PANE_WEIGHT), selectedId = selected)
                VerticalDivider()
                Box(Modifier.weight(DETAIL_PANE_WEIGHT).fillMaxHeight()) {
                    val id = selected
                    if (id == null) {
                        PanePlaceholder(stringResource(R.string.plan_select_trip), Modifier.align(Alignment.Center))
                    } else {
                        PlanDetailPane(id, onOpenResults = onOpenResults, onClosed = { selected = null })
                    }
                }
            }
        } else {
            // Expanded → compact (fold, rotation) with a trip open beside the list: keep it open full screen.
            LaunchedEffect(selected) {
                selected?.let {
                    selected = null
                    onOpenPlan(it)
                }
            }
            PlansScreen(state, viewModel::onEvent, onOpenTrip = onOpenPlan, onPlanTrip = onPlanTrip)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PlansMessages(message: PlansMessage?, snackbar: SnackbarHostState, onEvent: (PlansEvent) -> Unit) {
    val undo = stringResource(R.string.plan_undo)
    val text =
        when (message) {
            null -> null
            is PlansMessage.Deleted -> stringResource(R.string.plan_trip_deleted, message.name)
            is PlansMessage.DeleteFailed -> message.error.message ?: stringResource(R.string.plan_delete_failed)
        }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        if (message is PlansMessage.Deleted) {
            val result = snackbar.showSnackbar(text, actionLabel = undo, duration = SnackbarDuration.Long)
            onEvent(if (result == SnackbarResult.ActionPerformed) PlansEvent.UndoDelete else PlansEvent.CommitDelete)
        } else {
            snackbar.showSnackbar(text)
        }
        onEvent(PlansEvent.MessageShown)
    }
}

/** Full-screen trip detail (compact widths, deep link `travelplanner://plan/{planId}`). */
@Composable
internal fun PlanDetailRoute(onBack: () -> Unit, onOpenResults: () -> Unit) {
    val viewModel: PlanDetailViewModel = hiltViewModel()
    DetailContent(viewModel, onBack = onBack, onOpenResults = onOpenResults, onDeleted = onBack)
}

/** Trip detail as the right pane of list-detail; one ViewModel re-targeted with [PlanDetailViewModel.select]. */
@Composable
private fun PlanDetailPane(planId: Long, onOpenResults: () -> Unit, onClosed: () -> Unit) {
    val viewModel: PlanDetailViewModel = hiltViewModel(key = "plan-detail-pane")
    LaunchedEffect(planId) { viewModel.select(planId) }
    DetailContent(viewModel, onBack = null, onOpenResults = onOpenResults, onDeleted = onClosed)
}

@Composable
private fun DetailContent(viewModel: PlanDetailViewModel, onBack: (() -> Unit)?, onOpenResults: () -> Unit, onDeleted: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val openLink = rememberLinkOpener()
    ExportEffects(viewModel.exportRequests, snackbar)
    NavigationEffects(viewModel.navigationRequests, onOpenResults, onDeleted)
    PlanDetailScreen(state, viewModel::onEvent, onOpenLink = openLink, onBack = onBack, snackbar = snackbar)
}

@Composable
private fun NavigationEffects(requests: Flow<DetailNavigation>, onOpenResults: () -> Unit, onDeleted: () -> Unit) {
    val openResults by rememberUpdatedState(onOpenResults)
    val deleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(requests) {
        requests.collect {
            when (it) {
                DetailNavigation.OpenResults -> openResults()
                DetailNavigation.Deleted -> deleted()
            }
        }
    }
}

/** My Plan for the shared draft (opened from Results and Chat). */
@Composable
internal fun MyPlanRoute(onBack: () -> Unit, onOpenTrips: () -> Unit) {
    val viewModel: MyPlanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val openLink = rememberLinkOpener()
    ExportEffects(viewModel.exportRequests, snackbar)
    MyPlanScreen(state, viewModel::onEvent, onOpenLink = openLink, onBack = onBack, onOpenTrips = onOpenTrips, snackbar = snackbar)
}
