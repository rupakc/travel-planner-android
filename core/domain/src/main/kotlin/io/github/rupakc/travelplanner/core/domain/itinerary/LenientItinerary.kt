package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.json.decodeRawBackedList
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Day-by-day lenient decoding of an itinerary JSON (`{days: [...]}`), shared by the read-only views
 * ([PlanItinerary]) and the .ics export so both see the same days: non-object entries are skipped (web
 * `days.filter(isObject)` semantics), and a day that does not decode as a whole still keeps its number,
 * city and raw-backed slots instead of failing the whole itinerary.
 */
internal object LenientItinerary {
    /** The day objects of [source], in order. */
    fun dayObjects(source: JsonObject): List<JsonObject> = (source["days"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    /** [source] as typed data, one lenient day per entry of [dayObjects]. */
    fun decode(source: JsonObject): ItineraryData = ItineraryData(days = dayObjects(source).map(::day))

    fun day(day: JsonObject): ItineraryDay =
        runCatching { TravelJson.decodeFromJsonElement(ItineraryDay.serializer(), day) }.getOrElse {
            val number = (day["day_number"] as? JsonPrimitive)?.contentOrNull?.trim()?.toDoubleOrNull()
            ItineraryDay(
                dayNumber = number?.takeIf { it % 1.0 == 0.0 }?.toInt(),
                city = (day["city"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                slots = decodeRawBackedList(ItinerarySlot.serializer(), day["slots"]),
            )
        }
}
