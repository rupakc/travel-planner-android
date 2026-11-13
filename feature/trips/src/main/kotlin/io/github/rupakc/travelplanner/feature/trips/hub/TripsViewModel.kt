package io.github.rupakc.travelplanner.feature.trips.hub

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.game.Delight
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.CreateTripRequest
import io.github.rupakc.travelplanner.core.model.trips.Trip
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A failed load: [message] is the backend text (shown as-is) or null for our own copy. */
@Immutable
data class HubError(val message: String?)

/** The new-trip sheet. [error] blames a field; [failed] is a request failure with no field ([message]: backend text). */
@Immutable
data class NewTripSheet(
    val title: String = "",
    val destination: String = "",
    val start: String = "",
    val end: String = "",
    val saving: Boolean = false,
    val error: NewTripError? = null,
    val failed: Boolean = false,
    val message: String? = null,
)

@Immutable
data class TripsUiState(
    val loading: Boolean = true,
    val groups: List<PhaseGroup> = emptyList(),
    val pinned: Trip? = null,
    val offline: Boolean = false,
    val error: HubError? = null,
    val sheet: NewTripSheet? = null,
    /** Countries visited against the bucket-list goal (null: no goal, game hidden, or stats unavailable). */
    val bucket: Delight.Bucket? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && pinned == null && groups.isEmpty()
}

sealed interface TripsEvent {
    data object Refresh : TripsEvent

    /** The hub came back to the foreground (Back from a trip, app resume): the list is refetched silently. */
    data object Resumed : TripsEvent

    data object OpenCreate : TripsEvent

    data object DismissCreate : TripsEvent

    data class SetTitle(val value: String) : TripsEvent

    data class SetDestination(val value: String) : TripsEvent

    data class SetStart(val value: String) : TripsEvent

    data class SetEnd(val value: String) : TripsEvent

    data object SubmitCreate : TripsEvent

    data class OpenTrip(val id: Long) : TripsEvent

    /** "Plan a trip": leaves for the planner (Search). */
    data object PlanTrip : TripsEvent
}

sealed interface TripsEffect {
    data class OpenTrip(val id: Long) : TripsEffect

    data object PlanTrip : TripsEffect
}

/** Trips hub: phase groups, the pinned "Next up" card, offline fallback, new-trip sheet (Trips-API, Phase 2 hub). */
@HiltViewModel
class TripsViewModel
    @Inject
    constructor(
        private val repository: TripsRepository,
        private val telemetry: Telemetry,
        private val clock: AppClock,
        private val gameEvents: GameEvents,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TripsUiState())
        val state: StateFlow<TripsUiState> = _state.asStateFlow()

        private val _effects = Channel<TripsEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        private var viewed = false
        private var firstResume = true

        init {
            load()
        }

        fun onEvent(event: TripsEvent) {
            when (event) {
                TripsEvent.Refresh -> {
                    load()
                }

                TripsEvent.Resumed -> {
                    // The first resume is the screen appearing, whose load is already running.
                    if (firstResume) firstResume = false else load(silent = true)
                }

                TripsEvent.OpenCreate -> {
                    _state.update { it.copy(sheet = NewTripSheet()) }
                }

                TripsEvent.DismissCreate -> {
                    _state.update { it.copy(sheet = null) }
                }

                is TripsEvent.SetTitle -> {
                    editSheet { copy(title = event.value, error = null, failed = false) }
                }

                is TripsEvent.SetDestination -> {
                    editSheet { copy(destination = event.value, failed = false) }
                }

                is TripsEvent.SetStart -> {
                    editSheet { copy(start = event.value, error = null, failed = false) }
                }

                is TripsEvent.SetEnd -> {
                    editSheet { copy(end = event.value, error = null, failed = false) }
                }

                TripsEvent.SubmitCreate -> {
                    create()
                }

                is TripsEvent.OpenTrip -> {
                    _effects.trySend(TripsEffect.OpenTrip(event.id))
                }

                TripsEvent.PlanTrip -> {
                    telemetry.emit(TelemetryEvents.TRIP_ACTION, mapOf("action" to "open_planner"))
                    _effects.trySend(TripsEffect.PlanTrip)
                }
            }
        }

        private fun editSheet(change: NewTripSheet.() -> NewTripSheet) = _state.update { s -> s.sheet?.let { s.copy(sheet = it.change()) } ?: s }

        /** [silent]: keep what is shown while refetching, and keep it when the refetch fails. */
        private fun load(silent: Boolean = false) {
            _state.update { it.copy(loading = it.groups.isEmpty() && it.pinned == null, error = if (silent) it.error else null) }
            viewModelScope.launch {
                when (val result = repository.list(clock.today().toString())) {
                    is AppResult.Success -> {
                        val response = result.value
                        val pinned = response.trips.firstOrNull { it.id == response.pinnedId }
                        _state.update {
                            it.copy(
                                loading = false,
                                groups = groupByPhase(response.trips, response.pinnedId),
                                pinned = pinned,
                                offline = repository.isShowingCachedList(),
                                error = null,
                            )
                        }
                        view(pinned?.phase?.wire ?: "none")
                        loadBucket()
                    }

                    is AppResult.Failure -> {
                        val keep = silent && (_state.value.groups.isNotEmpty() || _state.value.pinned != null)
                        _state.update { if (keep) it.copy(loading = false) else it.copy(loading = false, error = HubError(result.error.userFacingDetail())) }
                        view("none")
                    }
                }
            }
        }

        /** `GET /trips/stats` for the bucket ring; a failure just leaves the ring out. */
        private fun loadBucket() {
            viewModelScope.launch {
                val stats = (repository.stats() as? AppResult.Success)?.value
                val bucket = stats?.let { Delight.bucket(it.countries.size, it.bucketGoal, gameEvents.visible.value) }
                _state.update { it.copy(bucket = bucket) }
            }
        }

        /** One `trip_view` per hub visit, with the pinned trip's phase (enum) or `none`. */
        private fun view(phase: String) {
            if (viewed) return
            viewed = true
            telemetry.emit(TelemetryEvents.TRIP_VIEW, mapOf("surface" to "hub", "phase" to phase))
        }

        private fun create() {
            val sheet = _state.value.sheet ?: return
            if (sheet.saving) return
            validateNewTrip(sheet.title, sheet.start, sheet.end)?.let { error ->
                editSheet { copy(error = error) }
                return
            }
            editSheet { copy(saving = true, error = null, failed = false, message = null) }
            val request =
                CreateTripRequest(
                    title = sheet.title.trim(),
                    destination = sheet.destination.trim().ifEmpty { null },
                    startDate = sheet.start.trim().ifEmpty { null },
                    endDate = sheet.end.trim().ifEmpty { null },
                )
            viewModelScope.launch {
                when (val result = repository.create(request)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.TRIP_ACTION, mapOf("action" to "create"))
                        _state.update { it.copy(sheet = null) }
                        _effects.send(TripsEffect.OpenTrip(result.value.id))
                    }

                    is AppResult.Failure -> {
                        val field = (result.error as? AppError.Client)?.let { newTripErrorForCode(it.code) }
                        editSheet { copy(saving = false, error = field, failed = field == null, message = result.error.userFacingDetail()) }
                    }
                }
            }
        }
    }
