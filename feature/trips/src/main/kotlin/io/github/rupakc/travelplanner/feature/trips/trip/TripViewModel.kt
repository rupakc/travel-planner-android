package io.github.rupakc.travelplanner.feature.trips.trip

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.userFacingDetail
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.PrepRepository
import io.github.rupakc.travelplanner.core.data.repository.TripsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness
import io.github.rupakc.travelplanner.core.domain.journey.PrepRules
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** The trip could not be loaded: [message] is the backend text (shown as-is) or null for our own copy. */
@Immutable
data class TripError(val message: String?)

/** A failed edit: [message] is the backend text or null for our own copy. */
@Immutable
data class TripFailure(val message: String?)

/** State of the plan shown on the overview. */
enum class PlanStatus { Loading, Ready, Failed }

/** The dialog open over the trip screen. */
sealed interface TripDialog {
    data class Rename(val title: String, val invalid: Boolean = false, val saving: Boolean = false) : TripDialog

    data class Dates(val start: String, val end: String, val invalid: Boolean = false, val saving: Boolean = false) : TripDialog

    data object ConfirmDelete : TripDialog

    /** Opening the trip in the planner would replace an unsaved draft. */
    data object ConfirmReplaceDraft : TripDialog
}

@Immutable
data class TripUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val error: TripError? = null,
    val plan: Plan? = null,
    val planStatus: PlanStatus = PlanStatus.Loading,
    val completeness: PlanCompleteness.Result? = null,
    val requestedTab: TripTab? = null,
    val dialog: TripDialog? = null,
    val busy: Boolean = false,
    val failure: TripFailure? = null,
) {
    /** A dreaming trip with nothing searched yet: the overview offers "Plan it" instead of the planner. */
    val canPlanIt: Boolean get() = trip?.phase == Phase.DREAMING && !hasSearch(plan?.searchData)

    /** [next] replaces the trip. A phase change drops the requested tab so the tabs follow the new phase (its default opens). */
    internal fun withTrip(next: Trip): TripUiState {
        val before = trip?.phase
        val changed = before != null && before != next.phase
        return copy(trip = next, requestedTab = if (changed) null else requestedTab)
    }
}

sealed interface TripEvent {
    data object Refresh : TripEvent

    data object RetryPlan : TripEvent

    data class SelectTab(val tab: TripTab) : TripEvent

    /** The screen showed [tab]: one `trip_view` per tab per visit. */
    data class TabShown(val tab: TripTab) : TripEvent

    data object OpenRename : TripEvent

    data class EditTitle(val value: String) : TripEvent

    data object OpenDates : TripEvent

    data class EditStart(val value: String) : TripEvent

    data class EditEnd(val value: String) : TripEvent

    data object ClearDates : TripEvent

    /** Saves the open rename or dates dialog. */
    data object SubmitDialog : TripEvent

    /** [phase] null is "Follow my dates". */
    data class SetPhase(val phase: Phase?) : TripEvent

    data object AskDelete : TripEvent

    data object ConfirmDelete : TripEvent

    data object OpenPlanner : TripEvent

    data object ConfirmOpenPlanner : TripEvent

    data object PlanIt : TripEvent

    data object DismissDialog : TripEvent

    data object DismissFailure : TripEvent

    data object Back : TripEvent
}

sealed interface TripEffect {
    data object Back : TripEffect

    data object Deleted : TripEffect

    /** The plan is loaded into the planner (`CurrentPlanStore`): open Results. */
    data object OpenPlanner : TripEffect

    data class PlanIt(val destination: String?) : TripEffect
}

/** Trip screen: header edits, phase override, overview data, completeness (Trips-API, Phase 2 T9). */
@HiltViewModel
class TripViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val trips: TripsRepository,
        private val plans: PlansRepository,
        private val prep: PrepRepository,
        private val store: CurrentPlanStore,
        private val telemetry: Telemetry,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tripId: Long = savedStateHandle.get<Long>(TRIP_ID_ARG) ?: -1L
        private val _state = MutableStateFlow(TripUiState(requestedTab = TripTab.fromId(savedStateHandle.get<String>(TAB_ARG))))
        val state: StateFlow<TripUiState> = _state.asStateFlow()

        private val _effects = Channel<TripEffect>(Channel.BUFFERED)
        val effects = _effects.receiveAsFlow()

        private val shownTabs = mutableSetOf<TripTab>()
        private var meterReported = false
        private var visaChecked = false

        init {
            load(silent = false)
        }

        fun onEvent(event: TripEvent) {
            when (event) {
                TripEvent.Refresh -> load(silent = false)
                TripEvent.RetryPlan -> loadPlan()
                is TripEvent.SelectTab -> selectTab(event.tab)
                is TripEvent.TabShown -> tabShown(event.tab)
                is TripEvent.SetPhase -> setPhase(event.phase)
                TripEvent.AskDelete -> _state.update { it.copy(dialog = TripDialog.ConfirmDelete, failure = null) }
                TripEvent.ConfirmDelete -> delete()
                TripEvent.OpenPlanner -> openPlanner(confirmed = false)
                TripEvent.ConfirmOpenPlanner -> openPlanner(confirmed = true)
                TripEvent.PlanIt -> planIt()
                TripEvent.DismissDialog -> _state.update { it.copy(dialog = null) }
                TripEvent.DismissFailure -> _state.update { it.copy(failure = null) }
                TripEvent.Back -> _effects.trySend(TripEffect.Back)
                else -> onDialogEvent(event)
            }
        }

        /** Events of the rename and dates dialogs. */
        private fun onDialogEvent(event: TripEvent) {
            when (event) {
                TripEvent.OpenRename -> {
                    _state.update { s -> s.copy(dialog = TripDialog.Rename(s.trip?.title.orEmpty()), failure = null) }
                }

                is TripEvent.EditTitle -> {
                    edit<TripDialog.Rename> { copy(title = event.value, invalid = false) }
                }

                TripEvent.OpenDates -> {
                    _state.update { s -> s.copy(dialog = TripDialog.Dates(s.trip?.startDate.orEmpty(), s.trip?.endDate.orEmpty()), failure = null) }
                }

                is TripEvent.EditStart -> {
                    edit<TripDialog.Dates> { copy(start = event.value, invalid = false) }
                }

                is TripEvent.EditEnd -> {
                    edit<TripDialog.Dates> { copy(end = event.value, invalid = false) }
                }

                TripEvent.ClearDates -> {
                    edit<TripDialog.Dates> { copy(start = "", end = "", invalid = false) }
                }

                TripEvent.SubmitDialog -> {
                    submitDialog()
                }

                else -> {
                    Unit
                }
            }
        }

        private inline fun <reified D : TripDialog> edit(change: D.() -> D) =
            _state.update { s -> (s.dialog as? D)?.let { s.copy(dialog = it.change()) } ?: s }

        private fun selectTab(tab: TripTab) {
            _state.update { it.copy(requestedTab = tab) }
            // Coming back to the overview after the prep tab: the ring and the visa part follow what was ticked.
            if (tab == TripTab.Overview) load(silent = true)
        }

        private fun tabShown(tab: TripTab) {
            if (!shownTabs.add(tab)) return
            telemetry.emit(TelemetryEvents.TRIP_VIEW, mapOf("surface" to tab.id, "phase" to (_state.value.trip?.phase?.wire ?: "none")))
        }

        private fun load(silent: Boolean) {
            if (!silent) _state.update { it.copy(loading = it.trip == null, error = null) }
            viewModelScope.launch {
                when (val result = trips.get(tripId, clock.today().toString())) {
                    is AppResult.Success -> {
                        _state.update { it.withTrip(result.value).copy(loading = false, error = null) }
                        loadPlan()
                    }

                    is AppResult.Failure -> {
                        if (!silent || _state.value.trip == null) {
                            _state.update { it.copy(loading = false, error = TripError(result.error.userFacingDetail())) }
                        }
                    }
                }
            }
        }

        private fun loadPlan() {
            val trip = _state.value.trip ?: return
            _state.update { if (it.plan == null) it.copy(planStatus = PlanStatus.Loading) else it }
            viewModelScope.launch {
                val (planResult, visa) =
                    coroutineScope {
                        val p = async { plans.get(tripId) }
                        val v = async { if (trip.phase == Phase.PLANNING) loadVisa() else null }
                        p.await() to v.await()
                    }
                visa?.let { visaChecked = it }
                when (planResult) {
                    is AppResult.Success -> {
                        val plan = planResult.value
                        val completeness = PlanCompleteness.of(plan.selections, plan.searchData, visaChecked)
                        _state.update { it.copy(plan = plan, planStatus = PlanStatus.Ready, completeness = completeness) }
                        if (trip.phase == Phase.PLANNING && !meterReported) {
                            meterReported = true
                            telemetry.emit(TelemetryEvents.COMPLETENESS_VIEW, mapOf("surface" to "trip", "percent" to completeness.percent))
                        }
                    }

                    is AppResult.Failure -> {
                        _state.update { if (it.plan == null) it.copy(planStatus = PlanStatus.Failed) else it }
                    }
                }
            }
        }

        private suspend fun loadVisa(): Boolean? =
            (prep.prep(tripId, clock.today().toString()) as? AppResult.Success)?.let { PrepRules.visaChecked(it.value.items) }

        private fun submitDialog() {
            when (val dialog = _state.value.dialog) {
                is TripDialog.Rename -> rename(dialog)
                is TripDialog.Dates -> setDates(dialog)
                else -> Unit
            }
        }

        private fun rename(dialog: TripDialog.Rename) {
            if (dialog.saving) return
            if (!isValidTripTitle(dialog.title)) {
                edit<TripDialog.Rename> { copy(invalid = true) }
                return
            }
            edit<TripDialog.Rename> { copy(saving = true) }
            patch(renamePatch(dialog.title), "rename") { code ->
                if (code ==
                    "trip_title_invalid"
                ) {
                    edit<TripDialog.Rename> { copy(invalid = true, saving = false) }
                }
            }
        }

        private fun setDates(dialog: TripDialog.Dates) {
            if (dialog.saving) return
            if (!areValidTripDates(dialog.start, dialog.end)) {
                edit<TripDialog.Dates> { copy(invalid = true) }
                return
            }
            edit<TripDialog.Dates> { copy(saving = true) }
            patch(datesPatch(dialog.start, dialog.end), "dates") { code ->
                if (code ==
                    "trip_dates_invalid"
                ) {
                    edit<TripDialog.Dates> { copy(invalid = true, saving = false) }
                }
            }
        }

        private fun setPhase(phase: Phase?) {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, failure = null) }
            patch(phasePatch(phase), if (phase == null) "phase_clear" else "phase_override") {}
        }

        /** PATCHes the trip; on success the answer replaces it and the plan data is reloaded (dates move the phase). */
        private fun patch(body: JsonObject, action: String, onFieldError: (String?) -> Unit) {
            viewModelScope.launch {
                when (val result = trips.update(tripId, body)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.TRIP_ACTION, mapOf("action" to action))
                        _state.update { it.withTrip(result.value).copy(dialog = null, busy = false) }
                        if (action == "dates" || action.startsWith("phase")) loadPlan()
                    }

                    is AppResult.Failure -> {
                        val code = (result.error as? AppError.Client)?.code
                        onFieldError(code)
                        val fieldCode = code == "trip_title_invalid" || code == "trip_dates_invalid"
                        _state.update {
                            it.copy(
                                busy = false,
                                dialog = if (fieldCode) it.dialog else it.dialog?.let { d -> stopSaving(d) },
                                failure = if (fieldCode) null else TripFailure(result.error.userFacingDetail()),
                            )
                        }
                    }
                }
            }
        }

        private fun stopSaving(dialog: TripDialog): TripDialog =
            when (dialog) {
                is TripDialog.Rename -> dialog.copy(saving = false)
                is TripDialog.Dates -> dialog.copy(saving = false)
                else -> dialog
            }

        private fun delete() {
            if (_state.value.busy) return
            _state.update { it.copy(busy = true, dialog = null, failure = null) }
            viewModelScope.launch {
                when (val result = trips.delete(tripId)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.TRIP_ACTION, mapOf("action" to "delete"))
                        _state.update { it.copy(busy = false) }
                        _effects.send(TripEffect.Deleted)
                    }

                    is AppResult.Failure -> {
                        _state.update { it.copy(busy = false, failure = TripFailure(result.error.userFacingDetail())) }
                    }
                }
            }
        }

        /** Hands the saved plan to the planner (the shared draft) and opens Results; asks first when an unsaved draft would be lost. */
        private fun openPlanner(confirmed: Boolean) {
            val plan = _state.value.plan ?: return
            val draft = store.draft.value
            if (!confirmed && wouldReplaceUnsavedDraft(draft, plan.id)) {
                _state.update { it.copy(dialog = TripDialog.ConfirmReplaceDraft) }
                return
            }
            _state.update { it.copy(dialog = null) }
            store.load(plan)
            telemetry.emit(TelemetryEvents.TRIP_ACTION, mapOf("action" to "open_planner"))
            _effects.trySend(TripEffect.OpenPlanner)
        }

        /** An unsaved draft with selections that belongs to another plan would be lost. */
        private fun wouldReplaceUnsavedDraft(draft: PlanDraft, planId: Long): Boolean =
            draft.dirty && !draft.selections.isEmpty && draft.planId != planId

        private fun planIt() {
            val s = _state.value
            telemetry.emit(TelemetryEvents.TRIP_ACTION, mapOf("action" to "plan_it"))
            val destination = s.trip?.destination?.takeIf { it.isNotBlank() } ?: s.trip?.destinations?.firstOrNull()
            _effects.trySend(TripEffect.PlanIt(destination))
        }

        companion object {
            /** Type-safe navigation stores `TripRoute` properties under their names. */
            const val TRIP_ID_ARG = "tripId"
            const val TAB_ARG = "tab"
        }
    }
