package io.github.rupakc.travelplanner.feature.plan.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppDispatchers
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.domain.export.PlanCalendarEvent
import io.github.rupakc.travelplanner.core.domain.export.PlanExportInput
import io.github.rupakc.travelplanner.core.domain.export.PlanIcs
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.feature.plan.export.ExportRequest
import io.github.rupakc.travelplanner.feature.plan.export.ShareTextInput
import io.github.rupakc.travelplanner.feature.plan.model.BudgetStatus
import io.github.rupakc.travelplanner.feature.plan.model.CostPart
import io.github.rupakc.travelplanner.feature.plan.model.ItineraryDayUi
import io.github.rupakc.travelplanner.feature.plan.model.ItineraryUi
import io.github.rupakc.travelplanner.feature.plan.model.PlanCost
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroup
import io.github.rupakc.travelplanner.feature.plan.model.SelectionGroups
import io.github.rupakc.travelplanner.feature.plan.model.TripSummary
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A saved plan prepared for display (built off the main thread from the cached [Plan]). */
@Immutable
internal data class PlanDetailContent(
    val summary: TripSummary,
    val groups: List<SelectionGroup>,
    val itinerary: List<ItineraryDayUi>,
    val costParts: List<CostPart>,
    val budgetStatus: BudgetStatus?,
    val hasTransport: Boolean,
)

/** Confirmation dialogs of the detail screen. */
internal enum class DetailConfirm { DELETE, REVOKE_SHARE, DISCARD_DRAFT }

internal sealed interface DetailMessage {
    data object LinkRevoked : DetailMessage

    data object NothingToExport : DetailMessage

    data class Failed(val error: AppError) : DetailMessage
}

@Immutable
internal data class PlanDetailUiState(
    val isLoading: Boolean = true,
    val content: PlanDetailContent? = null,
    /** Load failed and nothing is cached. */
    val error: AppError? = null,
    val shareBusy: Boolean = false,
    val busy: Boolean = false,
    val confirm: DetailConfirm? = null,
    /** Non-null while the "Add to calendar" sheet is open. */
    val calendarEvents: List<PlanCalendarEvent>? = null,
    val message: DetailMessage? = null,
)

internal sealed interface PlanDetailEvent {
    data object Retry : PlanDetailEvent

    data object OpenInResults : PlanDetailEvent

    data object ShareLink : PlanDetailEvent

    data object RevokeShare : PlanDetailEvent

    data object ExportCalendar : PlanDetailEvent

    data object ShowCalendarEvents : PlanDetailEvent

    data class AddEventToCalendar(val event: PlanCalendarEvent) : PlanDetailEvent

    data object HideCalendarEvents : PlanDetailEvent

    data object ShareAsText : PlanDetailEvent

    data object Delete : PlanDetailEvent

    data object Confirm : PlanDetailEvent

    data object DismissConfirm : PlanDetailEvent

    data object MessageShown : PlanDetailEvent
}

/** Navigation the detail screen asks for. */
internal sealed interface DetailNavigation {
    data object OpenResults : DetailNavigation

    data object Deleted : DetailNavigation
}

/**
 * One saved plan: read from the offline cache, refreshed from the server; share link,
 * calendar export, share as text, open in Results and delete.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class PlanDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val plans: PlansRepository,
        private val store: CurrentPlanStore,
        private val settings: SettingsRepository,
        private val clock: AppClock,
        private val dispatchers: AppDispatchers,
    ) : ViewModel() {
        private val planId = MutableStateFlow(savedStateHandle.get<Long>(PLAN_ID_ARG))
        private val status = MutableStateFlow(Status())
        private val exports = Channel<ExportRequest>(Channel.BUFFERED)
        private val navigation = Channel<DetailNavigation>(Channel.BUFFERED)

        /** Platform actions to run once (share sheet, calendar, file). */
        val exportRequests: Flow<ExportRequest> = exports.receiveAsFlow()

        val navigationRequests: Flow<DetailNavigation> = navigation.receiveAsFlow()

        private data class Status(
            val fetching: Boolean = false,
            val fetchError: AppError? = null,
            val shareBusy: Boolean = false,
            val busy: Boolean = false,
            val confirm: DetailConfirm? = null,
            val calendarEvents: List<PlanCalendarEvent>? = null,
            val message: DetailMessage? = null,
        )

        private val cached: StateFlow<Plan?> =
            planId.flatMapLatest { id -> if (id == null) flowOf(null) else plans.plan(id) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, null)

        private val content: Flow<PlanDetailContent?> = cached.map { plan -> plan?.let(::contentOf) }.flowOn(dispatchers.default)

        val state: StateFlow<PlanDetailUiState> =
            combine(content, status) { c, s ->
                PlanDetailUiState(
                    isLoading = c == null && s.fetching,
                    content = c,
                    error = if (c == null) s.fetchError else null,
                    shareBusy = s.shareBusy,
                    busy = s.busy,
                    confirm = s.confirm,
                    calendarEvents = s.calendarEvents,
                    message = s.message,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlanDetailUiState())

        init {
            fetch()
        }

        /** Shows [id] (list-detail on wide screens re-uses one ViewModel). */
        fun select(id: Long) {
            if (planId.value == id) return
            planId.value = id
            status.value = Status()
            fetch()
        }

        fun onEvent(event: PlanDetailEvent) {
            when (event) {
                PlanDetailEvent.Retry -> fetch()
                PlanDetailEvent.OpenInResults -> openInResults(confirmed = false)
                PlanDetailEvent.ShareLink -> shareLink()
                PlanDetailEvent.RevokeShare -> status.update { it.copy(confirm = DetailConfirm.REVOKE_SHARE) }
                PlanDetailEvent.ExportCalendar -> exportIcs()
                PlanDetailEvent.ShowCalendarEvents -> showCalendarEvents()
                is PlanDetailEvent.AddEventToCalendar -> exports.trySend(ExportRequest.CalendarInsert(event.event))
                PlanDetailEvent.HideCalendarEvents -> status.update { it.copy(calendarEvents = null) }
                PlanDetailEvent.ShareAsText -> shareText()
                PlanDetailEvent.Delete -> status.update { it.copy(confirm = DetailConfirm.DELETE) }
                PlanDetailEvent.Confirm -> confirm()
                PlanDetailEvent.DismissConfirm -> status.update { it.copy(confirm = null) }
                PlanDetailEvent.MessageShown -> status.update { it.copy(message = null) }
            }
        }

        private fun fetch() {
            val id = planId.value ?: return
            status.update { it.copy(fetching = true, fetchError = null) }
            viewModelScope.launch {
                val result = plans.get(id)
                status.update { it.copy(fetching = false, fetchError = (result as? AppResult.Failure)?.error) }
            }
        }

        private fun contentOf(plan: Plan): PlanDetailContent {
            val summary = TripSummary.from(plan, clock.today())
            val sd = plan.searchData
            val parts = PlanCost.breakdown(plan.selections, sd.str("departure_date"), sd.str("return_date"))
            return PlanDetailContent(
                summary = summary,
                groups = SelectionGroups.of(plan.selections),
                itinerary = ItineraryUi.of(plan.selections),
                costParts = parts,
                budgetStatus = PlanCost.budgetStatus(parts.sumOf { it.amount }, summary.budgetUsd),
                hasTransport = plan.selections.gettingAround.isNotEmpty(),
            )
        }

        private fun confirm() {
            val which = status.value.confirm ?: return
            status.update { it.copy(confirm = null) }
            when (which) {
                DetailConfirm.DELETE -> delete()
                DetailConfirm.REVOKE_SHARE -> revoke()
                DetailConfirm.DISCARD_DRAFT -> openInResults(confirmed = true)
            }
        }

        /**
         * Loads the saved plan into the shared draft from the cached copy (works offline, no
         * re-stream) with its itinerary snapshot as the itinerary section, then opens Results.
         */
        private fun openInResults(confirmed: Boolean) {
            val plan = cached.value ?: return
            val draft = store.draft.value
            val wouldLoseChanges = draft.dirty && draft.planId != plan.id && !draft.selections.isEmpty
            if (!confirmed && wouldLoseChanges) {
                status.update { it.copy(confirm = DetailConfirm.DISCARD_DRAFT) }
                return
            }
            store.restore(PlanDraft(planId = plan.id, name = plan.name, searchRequest = plan.searchRequest, selections = plan.selections))
            val request = plan.searchRequest
            val snapshot = plan.selections.itinerarySnapshot
            if (request != null) store.attachSearch(request, snapshot?.let { JsonObject(mapOf("itinerary" to it)) })
            navigation.trySend(DetailNavigation.OpenResults)
        }

        private fun shareLink() {
            val plan = cached.value ?: return
            plan.shareToken?.let { token ->
                launchLink(plan.name, token)
                return
            }
            status.update { it.copy(shareBusy = true) }
            viewModelScope.launch {
                when (val result = plans.share(plan.id)) {
                    is AppResult.Success -> {
                        status.update { it.copy(shareBusy = false) }
                        launchLink(plan.name, result.value)
                    }

                    is AppResult.Failure -> {
                        status.update { it.copy(shareBusy = false, message = DetailMessage.Failed(result.error)) }
                    }
                }
            }
        }

        private fun launchLink(name: String, token: String) {
            viewModelScope.launch { exports.send(ExportRequest.Link(name, shareUrl(settings.effectiveBaseUrl.first(), token))) }
        }

        private fun revoke() {
            val plan = cached.value ?: return
            status.update { it.copy(shareBusy = true) }
            viewModelScope.launch {
                val result = plans.unshare(plan.id)
                status.update {
                    it.copy(
                        shareBusy = false,
                        message = if (result is AppResult.Failure) DetailMessage.Failed(result.error) else DetailMessage.LinkRevoked,
                    )
                }
            }
        }

        private fun exportInput(plan: Plan) = PlanExportInput.of(plan, clock.now())

        private fun exportIcs() {
            val plan = cached.value ?: return
            val input = exportInput(plan)
            if (PlanIcs.events(input).isEmpty()) {
                status.update { it.copy(message = DetailMessage.NothingToExport) }
                return
            }
            exports.trySend(ExportRequest.Ics(PlanIcs.fileName(plan.name, "ics"), PlanIcs.build(input)))
        }

        private fun showCalendarEvents() {
            val plan = cached.value ?: return
            val events = PlanIcs.events(exportInput(plan))
            status.update {
                if (events.isEmpty()) it.copy(message = DetailMessage.NothingToExport) else it.copy(calendarEvents = events)
            }
        }

        private fun shareText() {
            val plan = cached.value ?: return
            val c = state.value.content ?: contentOf(plan)
            exports.trySend(
                ExportRequest.Text(
                    ShareTextInput(
                        planName = plan.name,
                        origin = c.summary.origin,
                        destination = c.summary.destination,
                        startDate = c.summary.startDate,
                        endDate = c.summary.endDate,
                        travellers = c.summary.travellers,
                        selections = plan.selections,
                        itineraryDays = PlanIcs.planItineraryDays(PlanSelectionsSerializer.toJson(plan.selections), null),
                        totalCostUsd = c.summary.totalCostUsd,
                    ),
                ),
            )
        }

        private fun delete() {
            val plan = cached.value ?: return
            status.update { it.copy(busy = true) }
            viewModelScope.launch {
                when (val result = plans.delete(plan.id)) {
                    is AppResult.Success -> {
                        status.update { it.copy(busy = false) }
                        navigation.send(DetailNavigation.Deleted)
                    }

                    is AppResult.Failure -> {
                        status.update { it.copy(busy = false, message = DetailMessage.Failed(result.error)) }
                    }
                }
            }
        }

        companion object {
            /** Type-safe navigation stores `PlanDetailRoute.planId` under its property name. */
            const val PLAN_ID_ARG = "planId"
            private const val STOP_TIMEOUT_MS = 5_000L

            /**
             * Public link of a shared plan. The web serves `/share/{token}` on its own origin; the app only
             * knows the server URL, so it uses that (same origin in a combined deployment) and falls back to
             * the app link when no server is configured.
             */
            fun shareUrl(baseUrl: String, token: String): String =
                baseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)?.let { "$it/share/$token" }
                    ?: DeepLinks.SHARE_PATTERN.replace("{token}", token)
        }
    }

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
