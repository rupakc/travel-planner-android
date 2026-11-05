package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * One entry of `selections.itinerary_edits` (`{activity?, location?, cost?}`, web `handleSlotEdit`).
 * Display rules (web ItinerarySection): an empty [activity] keeps the original name,
 * an empty [location] is kept as written, a null [cost] falls back to the slot's estimate.
 */
data class SlotEdit(val activity: String? = null, val location: String? = null, val cost: Double? = null) {
    /** Web `edit.activity || edit.location`: the slot shows "Edited". */
    val isEdited: Boolean get() = !activity.isNullOrEmpty() || !location.isNullOrEmpty()

    /** The activity name to show: the edit when non-empty, else [original]. */
    fun activityOr(original: String?): String? = activity?.takeIf { it.isNotEmpty() } ?: original

    fun locationOr(original: String?): String? = location ?: original

    fun costOr(original: Double?): Double? = cost ?: original

    companion object {
        val NONE = SlotEdit()

        /** Reads one stored edit; values of the wrong type are ignored (never guessed). */
        fun fromJson(obj: JsonObject?): SlotEdit {
            if (obj == null) return NONE
            return SlotEdit(
                activity = obj["activity"].stringOrNull(),
                location = obj["location"].stringOrNull(),
                cost = (obj["cost"] as? JsonPrimitive)?.takeUnless { it is JsonNull || it.isString }?.doubleOrNull,
            )
        }

        /** All edits by slot key. */
        fun fromSelections(edits: Map<String, JsonObject>): Map<String, SlotEdit> = edits.mapValues { fromJson(it.value) }
    }
}

/** Field names of `itinerary_edits` entries (web `onSlotEdit(key, field, value)`). */
object SlotEditFields {
    const val ACTIVITY = "activity"
    const val LOCATION = "location"
    const val COST = "cost"
}

/**
 * `itinerary_edits` with one field of one slot set (web `handleSlotEdit`: `{...edits, [key]: {...edits[key], [field]: value}}`).
 * A null [value] is stored as JSON null (web: a cleared cost).
 */
fun Map<String, JsonObject>.withSlotEdit(key: String, field: String, value: JsonElement?): Map<String, JsonObject> {
    val current = this[key] ?: JsonObject(emptyMap())
    return this + (key to JsonObject(current + (field to (value ?: JsonNull))))
}

internal fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The slot's JSON: its original object when known (keeps unmodelled keys), else the typed encoding. */
internal fun ItinerarySlot.baseJson(): JsonObject =
    raw.takeIf { it.isNotEmpty() } ?: (TravelJson.encodeToJsonElement(ItinerarySlot.serializer(), this) as JsonObject)

/**
 * Returns the slot with [patch] applied to its JSON (null values remove a key) and the
 * typed fields re-read from it, so `raw` and the typed view never disagree.
 */
fun ItinerarySlot.patched(patch: Map<String, JsonElement?>): ItinerarySlot {
    val json = baseJson().toMutableMap()
    patch.forEach { (k, v) -> if (v == null) json.remove(k) else json[k] = v }
    val obj = JsonObject(json)
    return TravelJson.decodeFromJsonElement(ItinerarySlot.serializer(), obj).withRaw(obj)
}
