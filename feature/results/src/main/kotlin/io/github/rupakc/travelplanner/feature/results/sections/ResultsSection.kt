package io.github.rupakc.travelplanner.feature.results.sections

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.SearchResultsState
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.feature.results.sections.info.StressRecheckController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The 16 results sections in web `AGENT_ORDER` (api-contract §3.2). */
enum class ResultsSection(val id: SectionId) {
    Flights(SectionId.FLIGHTS),
    Weather(SectionId.WEATHER),
    Hotels(SectionId.HOTELS),
    Activities(SectionId.ACTIVITIES),
    PlacesToSee(SectionId.PLACES_TO_SEE),
    WalkingTours(SectionId.WALKING_TOURS),
    Events(SectionId.EVENTS),
    Visa(SectionId.VISA),
    Sim(SectionId.SIM),
    Tips(SectionId.TIPS),
    EmergencyCard(SectionId.EMERGENCY_CARD),
    GettingAround(SectionId.GETTING_AROUND),
    Forex(SectionId.FOREX),
    Itinerary(SectionId.ITINERARY),
    StressTest(SectionId.STRESS_TEST),
    PackingList(SectionId.PACKING_LIST),
    ;

    companion object {
        fun of(id: SectionId): ResultsSection = entries.first { it.id == id }
    }
}

/**
 * Everything a section composable may do outside itself. Implemented once by the
 * results screen (backed by CurrentPlanStore, Custom Tabs and the watch dialogs).
 */
interface SectionActions {
    /** Add/remove from My Plan (single-choice categories replace). */
    fun onToggle(item: PlanItem)

    fun isSelected(item: PlanItem): Boolean

    /** Open a provider/info page in a Custom Tab. */
    fun onOpenLink(url: String)

    /** Start a fare watch for this flight (shown only when `/health.fare_watch_available`). */
    fun onWatchFare(flight: Flight)

    fun onWatchHotel(hotel: Hotel)

    fun onWatchActivity(activity: Activity)

    /** Re-run this section via its single-agent route. */
    fun onRetry(section: ResultsSection)

    // --- Info sections (Team E). Defaults keep previews/tests and older implementations working. ---

    /**
     * The My Plan draft's selections, observed: itinerary layout/edits/notes/slots and the packing list
     * read from here. Implement as `currentPlan.draft.map { it.selections }.stateIn(…)`.
     */
    val selections: StateFlow<PlanSelections> get() = NoSelections

    /**
     * Read-modify-write of the draft's selections (`currentPlan.setSelections(transform(draft.value.selections))`).
     * Used for itinerary edits (`itinerary_layout`, `itinerary_edits`, `itinerary_notes`, `itinerary_slots`) and
     * `packing_list`, stored exactly as the web stores them.
     * Apply [transform] synchronously to the latest draft (the itinerary editor computes moves inside it).
     */
    fun onUpdateSelections(transform: (PlanSelections) -> PlanSelections) = Unit

    /** The running/finished search (`SearchRepository.current`): other sections' data and raw JSON, the request. */
    val results: StateFlow<SearchResultsState> get() = NoResults

    /**
     * True while a saved plan is open in My Plan: the itinerary is read-only, as on the web
     * ("Editing is off while a saved plan is open…").
     */
    val itineraryEditingLocked: Boolean get() = false

    /** `POST /api/stress-test` (`SearchRepository.stressTest`) for "Re-check my edited plan". */
    suspend fun recheckStressTest(payload: StressTestPayload): AppResult<StressTestData> = AppResult.Failure(AppError.NotConfigured())

    /**
     * Retained re-check owner (create once in the Results ViewModel:
     * `StressRecheckController(viewModelScope, searchRepository::stressTest)`). Null → the section falls back
     * to a composition-scoped controller over [recheckStressTest], which does not survive scrolling away.
     */
    val stressRecheck: StressRecheckController? get() = null
}

private val NoSelections: StateFlow<PlanSelections> = MutableStateFlow(PlanSelections.EMPTY).asStateFlow()

private val NoResults: StateFlow<SearchResultsState> = MutableStateFlow(SearchResultsState()).asStateFlow()

/** No-op actions for previews and tests. */
object NoOpSectionActions : SectionActions {
    override fun onToggle(item: PlanItem) = Unit

    override fun isSelected(item: PlanItem): Boolean = false

    override fun onOpenLink(url: String) = Unit

    override fun onWatchFare(flight: Flight) = Unit

    override fun onWatchHotel(hotel: Hotel) = Unit

    override fun onWatchActivity(activity: Activity) = Unit

    override fun onRetry(section: ResultsSection) = Unit
}
