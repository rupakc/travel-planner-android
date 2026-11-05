package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Port of the web's `utils/stressTestPayload.js`: the `POST /api/stress-test` body for
 * re-checking the itinerary the user now sees (arranged, removed activities left out, edits
 * applied, estimated times added), plus the fingerprint that tells a re-check result is stale.
 */
object StressTestPayloads {
    /**
     * Body for re-checking the edited itinerary. [flights]/[visa]/[weather] are the sections' raw JSON
     * (verification is stripped); null when the section never arrived.
     */
    fun build(
        request: TravelSearchRequest,
        itinerary: ItineraryData,
        selections: PlanSelections,
        flights: JsonObject? = null,
        visa: JsonObject? = null,
        weather: JsonObject? = null,
    ): StressTestPayload =
        StressTestPayload(
            request = request.copy(returnDate = request.returnDate?.ifEmpty { null }),
            itinerary = editedItinerary(itinerary, selections),
            flights = flights?.withoutVerification(),
            visa = visa?.withoutVerification(),
            weather = weather?.withoutVerification(),
        )

    /** Identifies the edited itinerary a re-check ran against (layout + edits). */
    fun recheckFingerprint(itinerary: ItineraryData, selections: PlanSelections): String = editedItinerary(itinerary, selections)["days"].toString()

    /**
     * The itinerary as the user sees it: each day's slots listed in timing order (so every
     * `transit_from_prev` is from the slot just before), edits applied, the client-only `key` dropped.
     */
    fun editedItinerary(itinerary: ItineraryData, selections: PlanSelections): JsonObject {
        val edits = SlotEdit.fromSelections(selections.itineraryEdits)
        val arranged = ItineraryLayouts.arrange(itinerary, selections.itineraryLayout, edits)
        val days =
            ItinerarySchedule.scheduleDays(arranged.days, edits).map { scheduled ->
                val day = scheduled.day
                val ordered = ItinerarySchedule.scheduleOrder(day).map { scheduled.slots[it] }
                buildJsonObject {
                    day.dayNumber?.let { put("day_number", JsonPrimitive(it)) }
                    day.date?.let { put("date", JsonPrimitive(it)) }
                    day.theme?.let { put("theme", JsonPrimitive(it)) }
                    day.city?.let { put("city", JsonPrimitive(it)) }
                    put("slots", JsonArray(ordered.map { slotJson(it, edits[it.key] ?: SlotEdit.NONE) }))
                    day.dailyEstimatedCostUsd?.let { put("daily_estimated_cost_usd", JsonPrimitive(it)) }
                    if (scheduled.scheduled) put("schedule_warnings", JsonArray(scheduled.warnings.map { JsonPrimitive(it.english) }))
                }
            }
        return buildJsonObject {
            put("days", JsonArray(days))
            arranged.totalEstimatedCostUsd?.let { put("total_estimated_cost_usd", JsonPrimitive(it)) }
            itinerary.source?.let { put("source", JsonPrimitive(it)) }
        }
    }

    private fun slotJson(scheduled: ScheduledSlot, edit: SlotEdit): JsonObject {
        val slot = scheduled.slot
        val base = slot.baseJson().toMutableMap()
        base.remove("key")
        val notes = base.remove("notes")
        // the original description no longer matches an edited activity
        if (edit.activity.isNullOrEmpty() && notes != null) base["notes"] = notes
        base["time_of_day"] = slot.timeOfDay?.let(::JsonPrimitive) ?: JsonNull
        edit.activityOr(slot.activity)?.let { base["activity"] = JsonPrimitive(it) }
        val location = edit.locationOr(slot.location)
        if (location != null) base["location"] = JsonPrimitive(location) else base.remove("location")
        edit.cost?.let { base["estimated_cost_usd"] = JsonPrimitive(it) }
        if (scheduled.timed) {
            base["start_time"] = JsonPrimitive(scheduled.startTime)
            base["end_time"] = JsonPrimitive(scheduled.endTime)
            base["start_day_offset"] = JsonPrimitive(scheduled.startDayOffset)
            base["end_day_offset"] = JsonPrimitive(scheduled.endDayOffset)
            base["transit_from_prev"] =
                scheduled.transitFromPrev?.let {
                    buildJsonObject {
                        put("minutes", JsonPrimitive(it.minutes))
                        put("mode", JsonPrimitive(it.mode.wire))
                        put("km", JsonPrimitive(it.km))
                    }
                } ?: JsonNull
        }
        return JsonObject(base)
    }

    private fun JsonObject.withoutVerification(): JsonObject = JsonObject(this - "verification")
}
