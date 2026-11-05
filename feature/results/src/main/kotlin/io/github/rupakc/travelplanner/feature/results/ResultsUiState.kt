package io.github.rupakc.travelplanner.feature.results

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus
import io.github.rupakc.travelplanner.core.domain.results.BestValue
import io.github.rupakc.travelplanner.core.domain.results.BudgetAllocation
import io.github.rupakc.travelplanner.core.domain.results.BudgetCategory
import io.github.rupakc.travelplanner.core.domain.results.BudgetFit
import io.github.rupakc.travelplanner.core.domain.results.MetricDiff
import io.github.rupakc.travelplanner.core.domain.results.PackageConflict
import io.github.rupakc.travelplanner.core.domain.results.RemixPreset
import io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.ActivityFilters
import io.github.rupakc.travelplanner.core.model.search.FareCalendarDate
import io.github.rupakc.travelplanner.core.model.search.FlightFilters
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.LayoverPlan
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.FlightLayover
import io.github.rupakc.travelplanner.core.model.section.PricingAdvisorData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.Verification
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection

/** Everything the results screen renders (immutable; derived off the main thread). */
@Immutable
data class ResultsUiState(
    val request: TravelSearchRequest? = null,
    val phase: StreamPhase = StreamPhase.IDLE,
    /** Stream failed before results (e.g. 429): backend message or null → our copy. */
    val error: AppError? = null,
    /** Results restored from the plan draft (process death / saved plan), not streamed now. */
    val fromSnapshot: Boolean = false,
    /** A saved plan's search without results: offer to run it (no automatic re-stream). */
    val canRunSavedSearch: Boolean = false,
    val sections: List<SectionUi> = emptyList(),
    val readyCount: Int = 0,
    val confidence: ConfidenceData? = null,
    val pricingAdvisor: PricingAdvisorData? = null,
    val query: String = "",
    /** Sections with no match for [query] (hidden while filtering). */
    val hiddenByQuery: Int = 0,
    val selections: PlanSelections = PlanSelections(),
    val plan: PlanSummary = PlanSummary(),
    val bestValue: BestValueUi? = null,
    val budget: BudgetUi? = null,
    val remix: RemixUi? = null,
    val fareCalendar: FareCalendarUi = FareCalendarUi(),
    val layover: LayoverUi? = null,
    val watches: WatchesUi = WatchesUi(),
    val verificationDetail: VerificationDetail? = null,
    val selectedSection: SectionId? = null,
    /** Every section's stream status (for the "<Section> ready" announcer). */
    val sectionStatuses: Map<SectionId, SectionStatus> = emptyMap(),
    /** Days from today to departure (pricing advisor "Now" marker). */
    val daysUntilDeparture: Long = 0,
    val flightFilters: FlightFilters? = null,
    val hotelFilters: HotelFilters? = null,
    val activityFilters: ActivityFilters? = null,
    val source: ResultsSource = ResultsSource.NONE,
) {
    val isDone: Boolean get() = phase == StreamPhase.DONE
    val isStreaming: Boolean get() = phase == StreamPhase.STREAMING
    val hasResults: Boolean get() = sections.any { it.data != null }
}

/** One section card. [data] is already ordered by city, narrowed by the text filter and with filtered overrides. */
@Immutable
data class SectionUi(
    val section: ResultsSection,
    val status: SectionStatus,
    val data: SectionData?,
    /** Type-relevant (spec §6.3); others start collapsed. */
    val relevant: Boolean,
    val expanded: Boolean,
    val matchCount: Int = Int.MAX_VALUE,
    val filterLoading: Boolean = false,
    /** Message from a failed filter/retry call (backend detail or null → our copy). */
    val filterError: FilterError? = null,
    val activeFilterCount: Int = 0,
    /** Stored/saved results without this section: "not loaded" with Run search, never "No results". */
    val notLoaded: Boolean = false,
) {
    val id: SectionId get() = section.id
    val verification: Verification? get() = data?.verification
}

/** A failed filter/retry; [message] is the backend detail when it sent one. */
data class FilterError(val message: String?)

data class PlanSummary(val count: Int = 0, val total: Double = 0.0, val budget: BudgetStatus? = null, val savedPlanOpen: Boolean = false)

data class BestValueUi(
    val pkg: BestValue,
    val applied: Boolean,
    val conflicts: List<PackageConflict>,
    val canUndo: Boolean,
    /** A saved plan is open: applying is disabled (web copy). */
    val disabled: Boolean,
    /** My Plan total when it differs from the package total after applying. */
    val planTotal: Double?,
)

data class BudgetUi(
    val allocation: BudgetAllocation,
    val fit: BudgetFit,
    val showOnlyFits: Boolean,
    /** Cards react once the planner is touched or "show only what fits" is on. */
    val cardsReact: Boolean,
    val findingCheaperHotels: Boolean,
) {
    val maxNightly: Long get() = if (fit.nights > 0) (allocation.stay / fit.nights).toLong() else 0

    /** Web: offer when fewer than 3 hotels fit and at least $1/night is available. */
    val offerCheaperHotels: Boolean get() = fit.hotels.count < MIN_HOTELS_FITTING && maxNightly >= 1

    companion object {
        const val MIN_HOTELS_FITTING = 3
    }
}

data class RemixUi(val preset: RemixPreset, val rows: List<MetricDiff>?)

enum class LoadStatus { IDLE, LOADING, DONE, ERROR }

data class FareCalendarUi(
    /** Capability: serpapi configured and a single-city trip. */
    val available: Boolean = false,
    val open: Boolean = false,
    val status: LoadStatus = LoadStatus.IDLE,
    val dates: List<FareCalendarDate> = emptyList(),
    val error: AppError? = null,
    /** Date the user tapped, awaiting "Re-plan" confirmation. */
    val pending: FareCalendarDate? = null,
)

data class LayoverUi(val layover: FlightLayover, val status: LoadStatus, val plan: LayoverPlan? = null, val error: AppError? = null)

/** Watch button state per kind (keys: hotel name, activity name). Values = latest known price label data. */
data class WatchesUi(
    /** Fare watch: live fares on the server and a single-city trip departing after today. */
    val fareAvailable: Boolean = false,
    /** Hotel/activity price watches: live prices on the server (`fare_watch_available`). */
    val priceAvailable: Boolean = false,
    val fare: WatchState? = null,
    val hotels: Map<String, WatchState> = emptyMap(),
    val activities: Map<String, WatchState> = emptyMap(),
)

/** A watch being created ([busy]) or created (with its price/currency when known). */
data class WatchState(val busy: Boolean = false, val watchId: Long? = null, val price: Double? = null, val currency: String? = null)

data class VerificationDetail(val section: SectionId, val verification: Verification)

/** One-shot messages (snackbar). */
sealed interface ResultsMessage {
    data class FareWatched(val price: Double?, val travellers: Int, val alreadyLow: Boolean, val alreadyWatching: Boolean) : ResultsMessage

    data class PriceWatched(val price: Double?, val currency: String?, val alreadyWatching: Boolean) : ResultsMessage

    /** [kind] fare | hotel | activity; [message] backend detail or null. */
    data class WatchFailed(val kind: String, val message: String?) : ResultsMessage

    data object WatchUnavailable : ResultsMessage

    data object NewPlanStarted : ResultsMessage

    data class RetryFailed(val message: String?) : ResultsMessage
}

/** Everything the user can do on the results screen. */
sealed interface ResultsEvent {
    data class Toggle(val item: PlanItem) : ResultsEvent

    data class ToggleSection(val id: SectionId) : ResultsEvent

    data class SelectSection(val id: SectionId?) : ResultsEvent

    data class Query(val text: String) : ResultsEvent

    data object RetryStream : ResultsEvent

    data class RetrySection(val id: SectionId) : ResultsEvent

    data object RunSavedSearch : ResultsEvent

    data object ApplyPackage : ResultsEvent

    data object UndoPackage : ResultsEvent

    data class BudgetShare(val category: BudgetCategory, val value: Double) : ResultsEvent

    data class BudgetTotal(val total: Double) : ResultsEvent

    data object BudgetReset : ResultsEvent

    data class ShowOnlyFits(val on: Boolean) : ResultsEvent

    data object FindCheaperHotels : ResultsEvent

    data class Remix(val preset: RemixPreset) : ResultsEvent

    data object DismissRemix : ResultsEvent

    data object UndoNewPlan : ResultsEvent

    data class ApplyFlightFilters(val filters: FlightFilters) : ResultsEvent

    data class ApplyHotelFilters(val filters: HotelFilters) : ResultsEvent

    data class ApplyActivityFilters(val filters: ActivityFilters) : ResultsEvent

    data object ToggleFareCalendar : ResultsEvent

    data object RetryFareCalendar : ResultsEvent

    data class PickFareDate(val date: FareCalendarDate?) : ResultsEvent

    data object ConfirmFareReplan : ResultsEvent

    data class OpenLayover(val layover: FlightLayover) : ResultsEvent

    data object CloseLayover : ResultsEvent

    data object WatchFare : ResultsEvent

    data class WatchHotel(val hotel: io.github.rupakc.travelplanner.core.model.section.Hotel) : ResultsEvent

    data class WatchActivity(val activity: io.github.rupakc.travelplanner.core.model.section.Activity) : ResultsEvent

    data class ShowVerification(val section: SectionId) : ResultsEvent

    data object DismissVerification : ResultsEvent
}

/** The watch sheet Results hosts (Team G's sheets over their dialog controllers). */
sealed interface WatchSheet {
    data class Fare(val controller: io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogController) : WatchSheet

    data class Price(
        val controller: io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogController,
        val kind: String,
        val itemName: String,
    ) : WatchSheet
}
