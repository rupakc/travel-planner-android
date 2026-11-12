package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.LenientItinerary
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Plan completeness (port of the web's `utils/planCompleteness.js`): five parts of 20 points each. Pure and
 * tolerant of missing keys. [of] reads the plan the way the app shows it (the itinerary arranged by the user's layout).
 */
object PlanCompleteness {
    /** Points per part. */
    const val WEIGHT = 20

    /** The five parts, in display order. [wire] is the web id. */
    enum class PartId(val wire: String) {
        Flights("flights"),
        Stay("stay"),
        Days("days"),
        Visa("visa"),
        Packing("packing"),
    }

    /** One part: [fraction] 0..1; [done] when it is 1. */
    data class Part(val id: PartId, val fraction: Double) {
        val done: Boolean get() = fraction >= 1.0
    }

    /** [percent] 0..100 (JavaScript `Math.round` semantics) and the [parts]. */
    data class Result(val percent: Int, val parts: List<Part>)

    /**
     * [selections] is the plan's selections, [searchData] its `search_data` (nights come from `destination_nights`,
     * else the two dates, else 1) and [visaChecked] whether the visa prep item is done or not needed.
     */
    fun of(selections: PlanSelections?, searchData: JsonObject?, visaChecked: Boolean = false): Result {
        val s = selections ?: PlanSelections()
        val parts =
            listOf(
                Part(PartId.Flights, if (s.flight != null || s.flights.isNotEmpty()) 1.0 else 0.0),
                Part(PartId.Stay, if (s.hotel != null) 1.0 else 0.0),
                Part(PartId.Days, daysFraction(s, searchData)),
                Part(PartId.Visa, if (visaChecked) 1.0 else 0.0),
                Part(PartId.Packing, if (hasPacking(s.packingList)) 1.0 else 0.0),
            )
        val total = parts.sumOf { WEIGHT * it.fraction }
        return Result(Math.floor(total + HALF).toInt(), parts)
    }

    private fun number(element: kotlinx.serialization.json.JsonElement?): Double =
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            is JsonObject -> number(element["nights"])
            else -> 0.0
        }

    private fun day(value: kotlinx.serialization.json.JsonElement?): LocalDate? =
        try {
            (value as? JsonPrimitive)?.contentOrNull?.take(ISO_DAY)?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun nightsOf(searchData: JsonObject?): Int {
        (searchData?.get("destination_nights") as? JsonArray)?.let { stops ->
            val sum = stops.sumOf { number(it) }
            if (sum >= 1) return sum.toInt()
        }
        val a = day(searchData?.get("departure_date"))
        val b = day(searchData?.get("return_date"))
        if (a != null && b != null && b.isAfter(a)) return maxOf(1, ChronoUnit.DAYS.between(a, b).toInt())
        return 1
    }

    private fun daysFraction(selections: PlanSelections, searchData: JsonObject?): Double {
        val snapshot = selections.itinerarySnapshot ?: return 0.0
        if ((snapshot["days"] as? JsonArray) == null) return 0.0
        val itinerary = LenientItinerary.decode(snapshot)
        val arranged = ItineraryLayouts.arrange(itinerary, selections.itineraryLayout, SlotEdit.fromSelections(selections.itineraryEdits))
        val filled = arranged.days.count { it.slots.isNotEmpty() }
        return minOf(1.0, filled.toDouble() / nightsOf(searchData))
    }

    private fun hasPacking(packing: JsonObject?): Boolean {
        if (packing == null) return false
        return number(packing["total_items"]) > 0 || ((packing["categories"] as? JsonArray)?.isNotEmpty() == true)
    }

    private const val ISO_DAY = 10
    private const val HALF = 0.5
}
