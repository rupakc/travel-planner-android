package io.github.rupakc.travelplanner.feature.plan.myplan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.offline.PlanJourney
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.domain.export.PlanExportInput
import io.github.rupakc.travelplanner.core.domain.export.PlanIcs
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness
import io.github.rupakc.travelplanner.core.domain.place.placeName
import io.github.rupakc.travelplanner.core.domain.place.placeRoute
import io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus
import io.github.rupakc.travelplanner.core.domain.plan.CostPart
import io.github.rupakc.travelplanner.core.domain.plan.computePlanCostBreakdown
import io.github.rupakc.travelplanner.core.domain.plan.generatePlanName
import io.github.rupakc.travelplanner.core.domain.plan.getBudgetStatus
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.feature.plan.export.ExportRequest
import io.github.rupakc.travelplanner.feature.plan.export.ShareTextInput
import io.github.rupakc.travelplanner.feature.plan.model.PlanItineraryUi
import io.github.rupakc.travelplanner.feature.plan.model.Removal
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroup
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroups
import java.time.LocalDate
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** The draft prepared for display. */
@Immutable
internal data class MyPlanContent(
    val planId: Long?,
    val name: String,
    val dirty: Boolean,
    val destination: String?,
    val budgetUsd: Double?,
    val groups: List<SelectionGroup>,
    val itinerary: PlanItineraryUi,
    val costParts: List<CostPart>,
    val totalUsd: Double,
    val budgetStatus: BudgetStatus?,
    val hasTransport: Boolean,
    val itemCount: Int,
    /** Web `selectedCount` also counts non-empty itinerary notes. */
    val hasNotes: Boolean = false,
    /** Null unless `features.trips` is on (older backend: no meter). */
    val completeness: PlanCompleteness.Result? = null,
) {
    val isEmpty: Boolean get() = itemCount == 0 && itinerary.isEmpty() && !hasNotes
}

internal sealed interface MyPlanMessage {
    data object Saved : MyPlanMessage

    data object Updated : MyPlanMessage

    /** 401: the web's "Please log in to save plans". */
    data object LoginToSave : MyPlanMessage

    /** Backend detail when present, else "Save failed". */
    data class SaveFailed(val error: AppError) : MyPlanMessage

    /** Removed one item; Undo restores. */
    data class Removed(val title: String?) : MyPlanMessage

    data object NothingToExport : MyPlanMessage
}

@Immutable
internal data class MyPlanUiState(
    val content: MyPlanContent? = null,
    val isSaving: Boolean = false,
    val confirmNewPlan: Boolean = false,
    val message: MyPlanMessage? = null,
)

internal sealed interface MyPlanEvent {
    data class NameChanged(val name: String) : MyPlanEvent

    data class Remove(val removal: Removal, val title: String?) : MyPlanEvent

    data object UndoRemove : MyPlanEvent

    data object Save : MyPlanEvent

    data object SaveAsNew : MyPlanEvent

    data object NewPlan : MyPlanEvent

    data object ConfirmNewPlan : MyPlanEvent

    data object DismissNewPlan : MyPlanEvent

    data object ExportCalendar : MyPlanEvent

    data object ShareAsText : MyPlanEvent

    data object MessageShown : MyPlanEvent
}

/** My Plan: the shared draft (CurrentPlanStore) with save, save as new, new plan, removal with undo and export. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class MyPlanViewModel
    @Inject
    constructor(
        private val store: CurrentPlanStore,
        private val clock: AppClock,
        dispatchers: AppDispatchers,
        private val placeNames: PlaceNameLookup,
        private val journey: PlanJourney,
    ) : ViewModel() {
        private var meterReported = false
        private val status = MutableStateFlow(MyPlanUiState())
        private val exports = Channel<ExportRequest>(Channel.BUFFERED)
        private var undoSnapshot: PlanDraft? = null

        /** Generated once per unnamed draft, like the web's auto name; never saved until the user saves. */
        private var generatedName: Pair<Any?, String>? = null

        val exportRequests: Flow<ExportRequest> = exports.receiveAsFlow()

        private val content =
            store.draft
                .flatMapLatest { draft ->
                    val request = draft.searchRequest
                    val places = listOfNotNull(request?.origin, request?.destination) + request?.destinations.orEmpty()
                    placeNames.namesFor(places).map { names -> contentOf(draft, names).withMeter(draft) }
                }.flowOn(dispatchers.default)

        val state: StateFlow<MyPlanUiState> =
            combine(content, status) { c, s -> s.copy(content = c) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MyPlanUiState())

        fun onEvent(event: MyPlanEvent) {
            when (event) {
                is MyPlanEvent.NameChanged -> store.rename(event.name)
                is MyPlanEvent.Remove -> remove(event.removal, event.title)
                MyPlanEvent.UndoRemove -> undoSnapshot?.let { store.restore(it) }.also { undoSnapshot = null }
                MyPlanEvent.Save -> save(asNew = false)
                MyPlanEvent.SaveAsNew -> save(asNew = true)
                MyPlanEvent.NewPlan -> if (store.draft.value.dirty) status.update { it.copy(confirmNewPlan = true) } else newPlan()
                MyPlanEvent.ConfirmNewPlan -> newPlan()
                MyPlanEvent.DismissNewPlan -> status.update { it.copy(confirmNewPlan = false) }
                MyPlanEvent.ExportCalendar -> exportIcs()
                MyPlanEvent.ShareAsText -> shareText()
                MyPlanEvent.MessageShown -> status.update { it.copy(message = null) }
            }
        }

        /** Synchronised: content is built on the default dispatcher, save/export run on main. */
        @Synchronized
        private fun displayName(draft: PlanDraft, names: Map<String, String> = placeNames.names.value): String {
            draft.name?.let { return it }
            val request = draft.searchRequest ?: return PlanDraft.DEFAULT_NAME
            // "The Sunny Lisbon Getaway", not "… LIS …": the auto name uses the city of an airport code.
            val destination = placeName(request.destination, names)
            val seed = destination to request.departureDate
            generatedName?.takeIf { it.first == seed }?.let { return it.second }
            return generatePlanName(destination, request.interests, request.departureDate, Random(clock.now().toEpochMilli()))
                .also { generatedName = seed to it }
        }

        private fun contentOf(draft: PlanDraft, names: Map<String, String>): MyPlanContent {
            val request = draft.searchRequest
            val parts = computePlanCostBreakdown(draft.selections, request?.departureDate, request?.returnDate)
            val total = parts.sumOf { it.amount }
            return MyPlanContent(
                planId = draft.planId,
                name = displayName(draft, names),
                dirty = draft.dirty,
                destination = request?.destination?.let { placeName(it, names) },
                budgetUsd = request?.budgetUsd,
                groups = SelectionGroups.of(draft.selections),
                itinerary = PlanItineraryUi.of(draft.selections, liveItinerary(draft)),
                costParts = parts,
                totalUsd = total,
                budgetStatus = getBudgetStatus(total, request?.budgetUsd),
                hasTransport = draft.selections.gettingAround.isNotEmpty(),
                itemCount = draft.selections.count,
                hasNotes = draft.selections.itineraryNotes.values.any(String::isNotBlank),
            )
        }

        private suspend fun MyPlanContent.withMeter(draft: PlanDraft): MyPlanContent {
            if (isEmpty) return this
            val result = journey.completeness(draft) ?: return this
            if (!meterReported) {
                meterReported = true
                journey.meterViewed("drawer", result.percent)
            }
            return copy(completeness = result)
        }

        private fun liveItinerary(draft: PlanDraft): JsonObject? = draft.searchData?.get("itinerary") as? JsonObject

        private fun remove(removal: Removal, title: String?) {
            val draft = store.snapshot()
            undoSnapshot = draft
            store.setSelections(SelectionGroups.remove(draft.selections, removal))
            status.update { it.copy(message = MyPlanMessage.Removed(title)) }
        }

        private fun newPlan() {
            store.newPlan()
            generatedName = null
            status.update { it.copy(confirmNewPlan = false) }
        }

        private fun save(asNew: Boolean) {
            if (status.value.isSaving) return
            val before = store.snapshot()
            val name = state.value.content?.name ?: displayName(before)
            val updating = before.planId != null && !asNew
            if (asNew) store.restore(before.copy(planId = null))
            status.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val result = store.save(name)
                if (result is AppResult.Success) journey.onPlanSaved(result.value.id, store.draft.value.searchData)
                // Only the plan id goes back: edits made while the request was in flight stay.
                if (result is AppResult.Failure && asNew) store.restore(store.snapshot().copy(planId = before.planId))
                status.update {
                    it.copy(
                        isSaving = false,
                        message =
                            when {
                                result is AppResult.Success -> if (updating) MyPlanMessage.Updated else MyPlanMessage.Saved
                                (result as AppResult.Failure).error is AppError.Unauthorized -> MyPlanMessage.LoginToSave
                                else -> MyPlanMessage.SaveFailed(result.error)
                            },
                    )
                }
            }
        }

        private fun exportInput(draft: PlanDraft): PlanExportInput =
            PlanExportInput(
                planName = displayName(draft),
                searchData = draft.searchRequest?.let { TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), it) as JsonObject },
                selections = PlanSelectionsSerializer.toJson(draft.selections),
                itinerary = liveItinerary(draft),
                planId = draft.planId,
                now = clock.now(),
            )

        private fun exportIcs() {
            val draft = store.draft.value
            val input = exportInput(draft)
            if (PlanIcs.events(input).isEmpty()) {
                status.update { it.copy(message = MyPlanMessage.NothingToExport) }
                return
            }
            exports.trySend(ExportRequest.Ics(PlanIcs.fileName(input.planName, "ics"), PlanIcs.build(input)))
        }

        private fun shareText() {
            val draft = store.draft.value
            val request = draft.searchRequest
            val input = exportInput(draft)
            exports.trySend(
                ExportRequest.Text(
                    ShareTextInput(
                        planName = input.planName.orEmpty(),
                        origin = request?.origin?.let { placeName(it, placeNames.names.value) },
                        destination =
                            request?.destinations?.takeIf { it.size > 1 }?.let { placeRoute(it, placeNames.names.value) }
                                ?: request?.destination?.let { placeName(it, placeNames.names.value) },
                        startDate = request?.departureDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        endDate = request?.returnDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                        travellers = request?.numTravelers,
                        selections = draft.selections,
                        itineraryDays = PlanIcs.planItineraryDays(input.selections, input.itinerary),
                        totalCostUsd = state.value.content?.totalUsd ?: 0.0,
                    ),
                ),
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
