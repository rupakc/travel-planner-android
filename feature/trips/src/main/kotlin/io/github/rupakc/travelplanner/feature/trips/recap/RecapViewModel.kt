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
import io.github.rupakc.travelplanner.core.data.repository.DiscoverRepository
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.PreferencesRepository
import io.github.rupakc.travelplanner.core.data.repository.RecapRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.domain.search.whereNextPicks
import io.github.rupakc.travelplanner.core.domain.search.whereNextRequest
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.trips.Phase
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

/** "Where next" suggestions (`POST /api/discover`), asked for only when the traveller taps the button. */
@Immutable
sealed interface WhereNextState {
    data object Idle : WhereNextState

    data object Loading : WhereNextState

    data class Ready(val picks: List<DiscoveredDestination>) : WhereNextState

    /** No origin or nationality known: nothing to send, the host's Explore is the way on. */
    data object NeedsProfile : WhereNextState

    /** `503` (the AI limit or billing is reached): our copy, Explore stays available. */
    data object AiLimit : WhereNextState

    /** Any other failure (network, 429, 5xx, a backend without the route): retry or Explore. */
    data object Failed : WhereNextState
}

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
    val whereNext: WhereNextState = WhereNextState.Idle,
)

sealed interface RecapEvent {
    data object Retry : RecapEvent

    /** The trip is in [phase] now: the first call records it, a later change reloads the recap. */
    data class PhaseShown(val phase: Phase?) : RecapEvent

    data object Complete : RecapEvent

    data object DismissFailure : RecapEvent

    /** The share sheet was opened. */
    data object Shared : RecapEvent

    /** "Where next" was tapped: suggestions are loaded (a repeat tap while loading or after a failure asks again). */
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
        private val discover: DiscoverRepository,
        private val preferences: PreferencesRepository,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TripViewModel.TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(RecapUiState())
        val state: StateFlow<RecapUiState> = _state.asStateFlow()
        private var viewReported = false
        private var phase: Phase? = null
        private var phaseKnown = false
        private var plan: Plan? = null
        private var whereNextJob: kotlinx.coroutines.Job? = null

        init {
            load()
        }

        fun onEvent(event: RecapEvent) {
            when (event) {
                RecapEvent.Retry -> load()
                is RecapEvent.PhaseShown -> phaseShown(event.phase)
                RecapEvent.Complete -> complete()
                RecapEvent.DismissFailure -> _state.update { it.copy(completeFailure = null) }
                RecapEvent.Shared -> action("share")
                RecapEvent.WhereNext -> whereNext()
            }
        }

        private fun phaseShown(next: Phase?) {
            val changed = phaseKnown && next != phase
            phase = next
            phaseKnown = true
            if (changed) load()
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
                this@RecapViewModel.plan = (plan as? AppResult.Success)?.value
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

        private fun whereNext() {
            action("where_next")
            if (_state.value.whereNext == WhereNextState.Loading) return
            val recap = _state.value.recap
            val trip = plan?.searchRequest
            _state.update { it.copy(whereNext = WhereNextState.Loading) }
            whereNextJob?.cancel()
            whereNextJob =
                viewModelScope.launch {
                    val prefs = (preferences.get() as? AppResult.Success)?.value
                    val request = whereNextRequest(trip, prefs, recap?.days, clock.today())
                    if (request == null) {
                        _state.update { it.copy(whereNext = WhereNextState.NeedsProfile) }
                        return@launch
                    }
                    val next =
                        when (val result = discover.discover(request)) {
                            is AppResult.Success -> {
                                // Not where the traveller just was: the trip's destination and its countries.
                                val visited = listOfNotNull(trip?.destination, recap?.title) + recap?.countries.orEmpty().mapNotNull { c -> c.name }
                                WhereNextState.Ready(whereNextPicks(result.value.destinations, visited))
                            }

                            is AppResult.Failure -> {
                                if ((result.error as? AppError.Server)?.status == AI_LIMIT_STATUS) WhereNextState.AiLimit else WhereNextState.Failed
                            }
                        }
                    _state.update { it.copy(whereNext = next) }
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

        private companion object {
            const val AI_LIMIT_STATUS = 503
        }
    }
