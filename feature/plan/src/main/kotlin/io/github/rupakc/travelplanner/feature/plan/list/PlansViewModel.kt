package io.github.rupakc.travelplanner.feature.plan.list

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.di.ApplicationScope
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.feature.plan.model.TripFilter
import io.github.rupakc.travelplanner.feature.plan.model.TripQuery
import io.github.rupakc.travelplanner.feature.plan.model.TripSort
import io.github.rupakc.travelplanner.feature.plan.model.TripSummary
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Search / filter / sort controls of My trips. */
@Immutable
internal data class TripControls(
    val query: String = "",
    val filter: TripFilter = TripFilter.ALL,
    val sharedOnly: Boolean = false,
    val sort: TripSort = TripSort.TRIP_DATE,
)

/** One-off message for the snackbar. */
internal sealed interface PlansMessage {
    /** "Trip deleted" with Undo; [id] keeps two same-named trips' messages distinct. */
    data class Deleted(val id: Long, val name: String) : PlansMessage

    data class DeleteFailed(val error: AppError) : PlansMessage
}

@Immutable
internal data class PlansUiState(
    /** Nothing cached yet and the first refresh is running (skeleton). */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val controls: TripControls = TripControls(),
    val trips: List<TripSummary> = emptyList(),
    /** Saved plans before search/filter (to tell "no trips" from "no matches"). */
    val totalCount: Int = 0,
    /** Last refresh failure; with [totalCount] > 0 the cached copies are shown with a banner. */
    val error: AppError? = null,
    val message: PlansMessage? = null,
)

internal sealed interface PlansEvent {
    data class QueryChanged(val query: String) : PlansEvent

    data class FilterChanged(val filter: TripFilter) : PlansEvent

    data class SharedOnlyChanged(val sharedOnly: Boolean) : PlansEvent

    data class SortChanged(val sort: TripSort) : PlansEvent

    data object Refresh : PlansEvent

    /** Swipe: hide now, delete for real when the Undo snackbar goes away. */
    data class Delete(val id: Long) : PlansEvent

    data object UndoDelete : PlansEvent

    /** The Undo snackbar was dismissed or timed out. */
    data object CommitDelete : PlansEvent

    data object MessageShown : PlansEvent
}

/** "My trips": the offline cache of saved plans, refreshed from the server. */
@HiltViewModel
internal class PlansViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val plans: PlansRepository,
        private val clock: AppClock,
        private val dispatchers: AppDispatchers,
        @param:ApplicationScope private val appScope: CoroutineScope,
    ) : ViewModel() {
        private val controls = MutableStateFlow(TripControls())
        private val status = MutableStateFlow(Status())
        private var pending: TripSummary? = null

        private data class Status(
            val refreshing: Boolean = false,
            val firstRefreshDone: Boolean = false,
            val error: AppError? = null,
            val hidden: Set<Long> = emptySet(),
            val message: PlansMessage? = null,
        )

        private val summaries =
            plans.plans
                .map { list -> list.map { TripSummary.from(it, clock.today()) } }
                .flowOn(dispatchers.default)
                .onStart { refresh() }

        val state: StateFlow<PlansUiState> =
            combine(summaries, controls, status) { all, c, s ->
                val visible = all.filterNot { it.id in s.hidden }
                PlansUiState(
                    isLoading = visible.isEmpty() && !s.firstRefreshDone && s.error == null,
                    isRefreshing = s.refreshing,
                    controls = c,
                    trips = TripQuery.apply(visible, c.query, c.filter, c.sharedOnly, c.sort),
                    totalCount = visible.size,
                    error = s.error,
                    message = s.message,
                )
            }.flowOn(dispatchers.default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlansUiState())

        init {
            // A swipe-delete still pending when the process died: the user never undid it, so finish it.
            savedState.get<Long>(PENDING_DELETE_KEY)?.let { id ->
                savedState.remove<Long>(PENDING_DELETE_KEY)
                viewModelScope.launch { plans.delete(id) }
            }
        }

        fun onEvent(event: PlansEvent) {
            when (event) {
                is PlansEvent.QueryChanged -> controls.update { it.copy(query = event.query) }
                is PlansEvent.FilterChanged -> controls.update { it.copy(filter = event.filter) }
                is PlansEvent.SharedOnlyChanged -> controls.update { it.copy(sharedOnly = event.sharedOnly) }
                is PlansEvent.SortChanged -> controls.update { it.copy(sort = event.sort) }
                PlansEvent.Refresh -> refresh()
                is PlansEvent.Delete -> delete(event.id)
                PlansEvent.UndoDelete -> undo()
                PlansEvent.CommitDelete -> commit()
                PlansEvent.MessageShown -> status.update { it.copy(message = null) }
            }
        }

        private fun refresh() {
            if (status.value.refreshing) return
            status.update { it.copy(refreshing = true) }
            viewModelScope.launch {
                val result = plans.refresh()
                status.update {
                    it.copy(refreshing = false, firstRefreshDone = true, error = (result as? AppResult.Failure)?.error)
                }
            }
        }

        private fun delete(id: Long) {
            commit() // one pending delete at a time
            val trip = state.value.trips.firstOrNull { it.id == id } ?: return
            pending = trip
            savedState[PENDING_DELETE_KEY] = id
            status.update { it.copy(hidden = it.hidden + id, message = PlansMessage.Deleted(trip.id, trip.name)) }
        }

        private fun undo() {
            val trip = pending ?: return
            pending = null
            savedState.remove<Long>(PENDING_DELETE_KEY)
            status.update { it.copy(hidden = it.hidden - trip.id, message = null) }
        }

        private fun commit() {
            val trip = pending ?: return
            pending = null
            savedState.remove<Long>(PENDING_DELETE_KEY)
            viewModelScope.launch {
                val result = plans.delete(trip.id)
                status.update {
                    when (result) {
                        is AppResult.Success -> it.copy(hidden = it.hidden - trip.id)
                        is AppResult.Failure -> it.copy(hidden = it.hidden - trip.id, message = PlansMessage.DeleteFailed(result.error))
                    }
                }
            }
        }

        override fun onCleared() {
            // Leaving with a pending swipe-delete: the user did not undo, so it goes through.
            pending?.let { trip -> appScope.launch { plans.delete(trip.id) } }
            super.onCleared()
        }

        companion object {
            private const val STOP_TIMEOUT_MS = 5_000L

            /** SavedStateHandle key of a swipe-delete waiting for its Undo window. */
            const val PENDING_DELETE_KEY = "pendingDeleteId"
        }
    }
