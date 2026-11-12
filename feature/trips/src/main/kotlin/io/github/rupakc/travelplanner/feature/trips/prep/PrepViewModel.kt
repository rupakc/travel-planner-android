package io.github.rupakc.travelplanner.feature.trips.prep

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.repository.PrepRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.journey.PrepRules
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import io.github.rupakc.travelplanner.core.model.trips.PrepSummary
import io.github.rupakc.travelplanner.feature.trips.trip.TripViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last tick-off did not stick. [Backend] carries the server's text (shown as-is). */
sealed interface PrepNotice {
    data object ToggleFailed : PrepNotice

    /** `409 prep_item_auto`: the offline pack item ticks itself. */
    data object Auto : PrepNotice

    /** `409 prep_item_na`: the item is not needed for this trip. */
    data object NotNeeded : PrepNotice

    /** `404 prep_item_unknown`. */
    data object Unknown : PrepNotice

    data class Backend(val message: String) : PrepNotice
}

@Immutable
data class PrepUiState(
    val loading: Boolean = true,
    val items: List<PrepItem> = emptyList(),
    val summary: PrepSummary = PrepSummary(),
    /** The list could not be loaded: [errorMessage] is the backend text or null for our own copy. */
    val failed: Boolean = false,
    val errorMessage: String? = null,
    val busyId: String? = null,
    val notice: PrepNotice? = null,
    /** The list just became complete in this session: the "Ready to go" burst. */
    val celebrate: Boolean = false,
)

sealed interface PrepEvent {
    data object Retry : PrepEvent

    data class Toggle(val itemId: String, val done: Boolean) : PrepEvent

    data object DismissNotice : PrepEvent
}

/** Prep checklist of a trip: optimistic toggles with rollback, 409/404 handling (Trips-API "Prep checklist"). */
@HiltViewModel
class PrepViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PrepRepository,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TripViewModel.TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(PrepUiState())
        val state: StateFlow<PrepUiState> = _state.asStateFlow()

        init {
            load()
        }

        fun onEvent(event: PrepEvent) {
            when (event) {
                PrepEvent.Retry -> {
                    _state.update { it.copy(loading = it.items.isEmpty(), failed = false) }
                    load()
                }

                is PrepEvent.Toggle -> {
                    toggle(event.itemId, event.done)
                }

                PrepEvent.DismissNotice -> {
                    _state.update { it.copy(notice = null) }
                }
            }
        }

        private fun load() {
            viewModelScope.launch {
                when (val result = repository.prep(tripId, clock.today().toString())) {
                    is AppResult.Success -> {
                        _state.update {
                            it.copy(loading = false, failed = false, items = result.value.items, summary = PrepRules.summarize(result.value.items))
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(loading = false, failed = it.items.isEmpty(), errorMessage = result.error.userFacingDetail()) }
                    }
                }
            }
        }

        private fun toggle(itemId: String, done: Boolean) {
            val before = _state.value
            val item = before.items.firstOrNull { it.id == itemId } ?: return
            if (before.busyId != null || !PrepRules.canToggle(item)) return
            val optimistic = PrepRules.applyToggle(before.items, itemId, done)
            _state.update { it.copy(items = optimistic, summary = PrepRules.summarize(optimistic), busyId = itemId, notice = null) }
            viewModelScope.launch {
                when (val result = repository.toggle(tripId, itemId, done)) {
                    is AppResult.Success -> {
                        val res = result.value
                        val summary = PrepSummary(res.done, res.total, res.percent, res.complete)
                        val finished = res.complete && !before.summary.complete
                        _state.update { it.copy(items = res.items, summary = summary, busyId = null, celebrate = it.celebrate || finished) }
                        telemetry.emit(TelemetryEvents.PREP_TOGGLE, mapOf("item" to itemId, "done" to done))
                        if (finished) telemetry.emit(TelemetryEvents.PREP_COMPLETE, mapOf("items" to res.total))
                    }

                    is AppResult.Failure -> {
                        val code = (result.error as? AppError.Client)?.code ?: (result.error as? AppError.NotFound)?.code
                        val notice =
                            when (code) {
                                "prep_item_auto" -> PrepNotice.Auto
                                "prep_item_na" -> PrepNotice.NotNeeded
                                "prep_item_unknown" -> PrepNotice.Unknown
                                else -> result.error.userFacingDetail()?.let { PrepNotice.Backend(it) } ?: PrepNotice.ToggleFailed
                            }
                        _state.update { before.copy(busyId = null, notice = notice, celebrate = it.celebrate) }
                        // A conflict means our copy is stale: reload it.
                        if (code == "prep_item_auto" || code == "prep_item_na" || code == "prep_item_unknown") load()
                    }
                }
            }
        }
    }
