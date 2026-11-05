package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * A plan's day-by-day itinerary for the read-only views (My Plan, plan detail, the shared-trip viewer): the
 * same days the .ics export uses (web `planItineraryDays`). The live itinerary when given, else the snapshot
 * saved with the plan — arranged by the user's layout and given estimated times — else the individually
 * picked slots grouped by day, which keep their stored times.
 */
object PlanItinerary {
    /** [live] is the current search's itinerary section JSON (My Plan); saved plans pass null. */
    fun days(selections: PlanSelections, live: JsonObject? = null): List<ScheduledDay> {
        val edits = SlotEdit.fromSelections(selections.itineraryEdits)
        val source = live?.takeIf(::hasDays) ?: selections.itinerarySnapshot?.takeIf(::hasDays)
        val itinerary = source?.let(LenientItinerary::decode)
        if (itinerary != null && itinerary.days.isNotEmpty()) {
            val arranged = ItineraryLayouts.arrange(itinerary, selections.itineraryLayout, edits)
            return ItinerarySchedule.scheduleDays(arranged.days, edits)
        }
        return selections.itinerarySlots
            .groupBy { it.dayNumber }
            .entries
            .sortedWith(compareBy(nullsLast()) { it.key })
            .map { (dayNumber, slots) ->
                val ordered = slots.sortedBy { ItineraryLayouts.TIMES.indexOf(it.timeOfDay) }
                val day = ItineraryDay(dayNumber = dayNumber, date = slots.first().date, slots = ordered)
                ScheduledDay(day, ordered.map { ScheduledSlot(it) }, emptyList(), scheduled = false)
            }
    }

    private fun hasDays(itinerary: JsonObject): Boolean = (itinerary["days"] as? JsonArray)?.isNotEmpty() == true
}
