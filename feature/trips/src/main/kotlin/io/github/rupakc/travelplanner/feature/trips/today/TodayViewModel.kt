package io.github.rupakc.travelplanner.feature.trips.today

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
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.itinerary.PlanItinerary
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.CheckinResponse
import io.github.rupakc.travelplanner.core.model.trips.Streak
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.feature.trips.trip.TripViewModel
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A failed check-in: [problem] picks our copy, [message] is the backend text for anything else. */
@Immutable
data class CheckinFailure(val problem: TodayRules.CheckinProblem, val message: String? = null)

@Immutable
data class TodayUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val trip: Trip? = null,
    /** The itinerary day of today (null: no itinerary, or no day matches). */
    val day: ScheduledDay? = null,
    val city: String? = null,
    val nextUp: ScheduledSlot? = null,
    val checkedIn: Boolean = false,
    val sending: Boolean = false,
    val streak: Streak? = null,
    val newCountry: Boolean = false,
    val failure: CheckinFailure? = null,
) {
    val dayNumber: Int? get() = trip?.dayIndex
}

sealed interface TodayEvent {
    data object Retry : TodayEvent

    data object CheckIn : TodayEvent

    data object DismissBanner : TodayEvent

    /** The directions button was used (the screen opens the maps app). */
    data object DirectionsOpened : TodayEvent
}

/** Today tab of an on-the-road trip: "Day N", today's slots, the next one up and the daily check-in. */
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val trips: TripsRepository,
        private val plans: PlansRepository,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TripViewModel.TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(TodayUiState())
        val state: StateFlow<TodayUiState> = _state.asStateFlow()
        private var nextUpReported = false

        init {
            load()
        }

        fun onEvent(event: TodayEvent) {
            when (event) {
                TodayEvent.Retry -> load()
                TodayEvent.CheckIn -> checkIn()
                TodayEvent.DismissBanner -> _state.update { it.copy(newCountry = false, failure = null) }
                TodayEvent.DirectionsOpened -> telemetry.emit(TelemetryEvents.TODAY_ACTION, mapOf("action" to "directions"))
            }
        }

        private fun load() {
            _state.update { it.copy(loading = it.trip == null, failed = false) }
            viewModelScope.launch {
                val today = clock.today()
                val (tripResult, plan, checkins) =
                    coroutineScope {
                        val t = async { trips.get(tripId, today.toString()) }
                        val p = async { plans.get(tripId) }
                        val c = async { trips.checkins(tripId) }
                        Triple(t.await(), p.await(), c.await())
                    }
                val trip = (tripResult as? AppResult.Success)?.value
                if (trip == null) {
                    _state.update { it.copy(loading = false, failed = it.trip == null) }
                    return@launch
                }
                val days = (plan as? AppResult.Success)?.value?.let { PlanItinerary.days(it.selections) }.orEmpty()
                val day = TodayRules.dayFor(days, today, trip.dayIndex)
                val now = clock.now().atZone(clock.zone()).toLocalTime()
                val next = day?.let { TodayRules.nextUp(it.slots, now) }
                val done = (checkins as? AppResult.Success)?.value
                _state.update {
                    it.copy(
                        loading = false,
                        failed = false,
                        trip = trip,
                        day = day,
                        city = day?.day?.city ?: it.city ?: trip.destination ?: trip.destinations.firstOrNull(),
                        nextUp = next,
                        checkedIn = today.toString() in done?.days.orEmpty() || it.checkedIn,
                        streak = done?.streak ?: it.streak,
                    )
                }
                if (next != null && !nextUpReported) {
                    nextUpReported = true
                    telemetry.emit(TelemetryEvents.TODAY_ACTION, mapOf("action" to "next_up"))
                }
            }
        }

        private fun checkIn() {
            val s = _state.value
            // A second tap is a no-op: nothing is sent once today is checked in or a request is under way.
            if (s.checkedIn || s.sending) return
            _state.update { it.copy(sending = true, failure = null) }
            viewModelScope.launch {
                // The device's local day, never the UTC one (a Tokyo morning is still yesterday in UTC).
                when (val result = trips.checkin(tripId, clock.today().toString())) {
                    is AppResult.Success -> {
                        onCheckedIn(result.value)
                    }

                    is AppResult.Failure -> {
                        val code = (result.error as? AppError.Client)?.code
                        val problem = TodayRules.problemOf(code)
                        val message = if (problem == TodayRules.CheckinProblem.Other) result.error.userFacingDetail() else null
                        _state.update { it.copy(sending = false, failure = CheckinFailure(problem, message)) }
                    }
                }
            }
        }

        private fun onCheckedIn(r: CheckinResponse) {
            telemetry.emit(TelemetryEvents.TODAY_ACTION, mapOf("action" to "checkin"))
            _state.update {
                it.copy(
                    sending = false,
                    checkedIn = true,
                    newCountry = r.newCountry,
                    streak = r.streak ?: it.streak,
                    city = r.city?.takeIf { c -> c.isNotBlank() } ?: it.city,
                )
            }
        }
    }
