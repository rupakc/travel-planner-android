package io.github.rupakc.travelplanner.feature.results.sections

import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.SectionId

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
}

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
