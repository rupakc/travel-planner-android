package io.github.rupakc.travelplanner.feature.results.sections.info

import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.domain.itinerary.withSlotEdit
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Itinerary editing on My Plan selections, exactly as the web ResultsPage stores it:
 * `itinerary_layout` (overlay), `itinerary_edits`, `itinerary_notes` and picked `itinerary_slots`
 * (kept at their new position and time after every layout change). Pure.
 */
internal object ItineraryEdits {
    /** Max undo depth (web keeps the last 20 layouts). */
    const val UNDO_LIMIT = 20

    /** Web `commitLayout`: sets the layout and moves/re-times picked slots. */
    fun commitLayout(itinerary: ItineraryData, selections: PlanSelections, next: ItineraryLayout?): PlanSelections {
        val edits = SlotEdit.fromSelections(selections.itineraryEdits)
        val arranged = ItineraryLayouts.arrange(itinerary, next, edits)
        val moved = ItineraryLayouts.syncPlannedSlots(selections.itinerarySlots, arranged)
        val timed = ItinerarySchedule.syncPlannedSlotTimes(moved, ItinerarySchedule.scheduleDays(arranged.days, edits))
        return selections.copy(itineraryLayout = next, itinerarySlots = timed)
    }

    /** Web `handleSlotEdit`: one field of one slot (`activity`/`location` text, `cost` number or null). */
    fun setEdit(selections: PlanSelections, key: String, field: String, value: JsonElement?): PlanSelections =
        selections.copy(itineraryEdits = selections.itineraryEdits.withSlotEdit(key, field, value))

    /** Web `handleNoteChange`. */
    fun setNote(selections: PlanSelections, key: String, text: String): PlanSelections =
        selections.copy(
            itineraryNotes =
                selections.itineraryNotes + (key to text),
        )

    /** The cost field's text → stored value: blank or not a number → null (web `Number('')`/NaN → null). */
    fun costValue(text: String): JsonElement = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.let(::JsonPrimitive) ?: JsonNull

    /**
     * The entry the web stores when a slot is added to the plan:
     * `{key, day_number, time_of_day, activity, location, estimated_cost_usd, ...pickedTimes}` (edits applied).
     */
    fun plannedEntry(dayNumber: Int?, slot: ScheduledSlot, edit: SlotEdit): ItinerarySlot {
        val s = slot.slot
        val json =
            buildJsonObject {
                put("key", JsonPrimitive(s.key))
                put("day_number", JsonPrimitive(dayNumber))
                put("time_of_day", JsonPrimitive(s.timeOfDay))
                edit.activityOr(s.activity)?.let { put("activity", JsonPrimitive(it)) }
                edit.locationOr(s.location)?.let { put("location", JsonPrimitive(it)) }
                edit.costOr(s.estimatedCostUsd)?.let { put("estimated_cost_usd", JsonPrimitive(it)) }
                ItinerarySchedule.pickedTimes(slot).forEach { (k, v) -> put(k, v) }
            }
        return TravelJson.decodeFromJsonElement(ItinerarySlot.serializer(), json).withRaw(json)
    }

    /** Undo stack entries survive configuration changes as text ("null" = no layout). */
    fun encodeLayout(layout: ItineraryLayout?): String = layout?.let { TravelJson.encodeToString(ItineraryLayout.serializer(), it) } ?: "null"

    fun decodeLayout(text: String): ItineraryLayout? =
        if (text ==
            "null"
        ) {
            null
        } else {
            runCatching { TravelJson.decodeFromJsonElement(ItineraryLayout.serializer(), TravelJson.parseToJsonElement(text) as JsonObject) }.getOrNull()
        }
}
