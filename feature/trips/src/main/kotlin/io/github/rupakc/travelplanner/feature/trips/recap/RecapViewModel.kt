package io.github.rupakc.travelplanner.feature.trips.recap

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.RecapRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.feature.trips.trip.TripViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why completing failed: [NotReady] is `409 recap_not_ready` (our copy), [Other] shows the backend [message]. */
@Immutable
data class CompleteFailure(val notReady: Boolean, val message: String? = null)

@Immutable
data class RecapUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val recap: Recap? = null,
    /** `plan_cost_usd` of the saved plan, computed here (the server always sends null). */
    val planCostUsd: Double? = null,
    val completing: Boolean = false,
    /** Countries this trip added, from the recap and from the completion. */
    val newCountries: Set<String> = emptySet(),
    val completeFailure: CompleteFailure? = null,
)

sealed interface RecapEvent {
    data object Retry : RecapEvent

    data object Complete : RecapEvent

    data object DismissFailure : RecapEvent

    /** The share sheet was opened. */
    data object Shared : RecapEvent

    /** "Where next" was tapped (the host opens Explore). */
    data object WhereNext : RecapEvent
}

/** Recap tab of a trip: stats, route, countries and "Mark recap complete". */
@HiltViewModel
class RecapViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val recaps: RecapRepository,
        private val plans: PlansRepository,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TripViewModel.TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(RecapUiState())
        val state: StateFlow<RecapUiState> = _state.asStateFlow()
        private var viewReported = false

        init {
            load()
        }

        fun onEvent(event: RecapEvent) {
            when (event) {
                RecapEvent.Retry -> load()
                RecapEvent.Complete -> complete()
                RecapEvent.DismissFailure -> _state.update { it.copy(completeFailure = null) }
                RecapEvent.Shared -> action("share")
                RecapEvent.WhereNext -> action("where_next")
            }
        }

        private fun action(name: String) = telemetry.emit(TelemetryEvents.RECAP_ACTION, mapOf("action" to name))

        private fun load() {
            _state.update { it.copy(loading = it.recap == null, failed = false) }
            viewModelScope.launch {
                val today = clock.today().toString()
                val (recapResult, plan) =
                    coroutineScope {
                        val r = async { recaps.recap(tripId, today) }
                        val p = async { plans.get(tripId) }
                        r.await() to p.await()
                    }
                val recap = (recapResult as? AppResult.Success)?.value
                if (recap == null) {
                    _state.update { it.copy(loading = false, failed = it.recap == null) }
                    return@launch
                }
                val cost =
                    (plan as? AppResult.Success)?.value?.let { computePlanCost(it.selections, recap.startDate, recap.endDate) }?.takeIf { it > 0 }
                _state.update {
                    it.copy(
                        loading = false,
                        failed = false,
                        recap = recap,
                        planCostUsd = cost,
                        newCountries = it.newCountries + recap.countries.filter { c -> c.new }.map { c -> c.code },
                    )
                }
                if (!viewReported) {
                    viewReported = true
                    action("view")
                }
            }
        }

        private fun complete() {
            val s = _state.value
            val recap = s.recap ?: return
            // Already completed or a request under way: nothing is sent (the server is idempotent, a repeat would only echo).
            if (recap.completedAt != null || s.completing) return
            _state.update { it.copy(completing = true, completeFailure = null) }
            viewModelScope.launch {
                when (val result = recaps.complete(tripId, clock.today().toString())) {
                    is AppResult.Success -> {
                        action("complete")
                        _state.update {
                            val current = it.recap ?: recap
                            it.copy(
                                completing = false,
                                // A repeat never moves the first completion time.
                                recap = current.copy(completedAt = current.completedAt ?: result.value.completedAt ?: "done"),
                                newCountries = it.newCountries + result.value.newCountries,
                            )
                        }
                    }

                    is AppResult.Failure -> {
                        val code = (result.error as? AppError.Client)?.code
                        val notReady = code == "recap_not_ready"
                        _state.update {
                            it.copy(completing = false, completeFailure = CompleteFailure(notReady, if (notReady) null else result.error.userFacingDetail()))
                        }
                    }
                }
            }
        }
    }
