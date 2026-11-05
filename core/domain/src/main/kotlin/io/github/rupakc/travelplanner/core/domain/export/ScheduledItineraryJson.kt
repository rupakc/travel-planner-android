package io.github.rupakc.travelplanner.core.domain.export

import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.json.decodeRawBackedList
import io.github.rupakc.travelplanner.core.model.json.toJson
import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The export's bridge to the shared typed itinerary port (`core/domain/itinerary`: web `itineraryLayout.js` +
 * `schedule.js`). The export reads raw JSON with JavaScript semantics (planExport.js), so the arranged,
 * scheduled days come back as the original day objects with their slots replaced: each slot's original JSON
 * plus `key`, its new `time_of_day` when moved, and — for timed slots — `start_time`/`end_time`, the day
 * offsets and `transit_from_prev`, exactly the fields the web's `scheduleDays` adds.
 */
internal object ScheduledItineraryJson {
    /** [source]'s days arranged by [layout] (when it belongs to them) and given estimated times. */
    fun scheduledDays(source: JsonObject, layout: JsonObject?, edits: JsonObject?): List<JsonObject> {
        val rawDays = source["days"].objects()
        val itinerary = ItineraryData(days = rawDays.map(::typedDay))
        val slotEdits = SlotEdit.fromSelections(edits.orEmpty().mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }.toMap())
        val arranged = ItineraryLayouts.arrange(itinerary, typedLayout(layout), slotEdits).days
        return ItinerarySchedule.scheduleDays(arranged, slotEdits).mapIndexed { i, day -> dayJson(rawDays[i], day) }
    }

    /** Web `activeLayout` only reads layouts whose `positions` is an object; anything else is ignored. */
    private fun typedLayout(layout: JsonObject?): ItineraryLayout? =
        layout?.takeIf { it["positions"] is JsonObject }
            ?.let { runCatching { TravelJson.decodeFromJsonElement(ItineraryLayout.serializer(), it) }.getOrNull() }

    /** A day that does not decode as a whole still keeps its number, city and (raw-backed) slots. */
    private fun typedDay(day: JsonObject): ItineraryDay =
        runCatching { TravelJson.decodeFromJsonElement(ItineraryDay.serializer(), day) }.getOrElse {
            ItineraryDay(
                dayNumber = day["day_number"].looseNumber()?.takeIf { it % 1.0 == 0.0 }?.toInt(),
                city = day["city"].str(),
                slots = decodeRawBackedList(ItinerarySlot.serializer(), day["slots"]),
            )
        }

    private fun dayJson(original: JsonObject, day: ScheduledDay): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>(original)
        fields["slots"] = JsonArray(day.slots.map(::slotJson))
        if (day.scheduled) fields["schedule_warnings"] = JsonArray(day.warnings.map { JsonPrimitive(it.english) })
        return JsonObject(fields)
    }

    private fun slotJson(scheduled: ScheduledSlot): JsonObject {
        val slot = scheduled.slot
        val fields = LinkedHashMap<String, JsonElement>(slot.toJson(ItinerarySlot.serializer()))
        slot.key?.let { fields["key"] = JsonPrimitive(it) }
        // A moved slot takes its new time of day (unmoved slots keep their value as sent).
        if (slot.timeOfDay != null && fields["time_of_day"].str() != slot.timeOfDay) fields["time_of_day"] = JsonPrimitive(slot.timeOfDay)
        if (scheduled.timed) {
            fields["start_time"] = JsonPrimitive(scheduled.startTime)
            fields["end_time"] = JsonPrimitive(scheduled.endTime)
            fields["start_day_offset"] = JsonPrimitive(scheduled.startDayOffset)
            fields["end_day_offset"] = JsonPrimitive(scheduled.endDayOffset)
            fields["transit_from_prev"] =
                scheduled.transitFromPrev?.let { t ->
                    buildJsonObject {
                        put("minutes", t.minutes)
                        put("mode", t.mode.wire)
                        put("km", t.km)
                    }
                } ?: JsonNull
        }
        return JsonObject(fields)
    }
}
