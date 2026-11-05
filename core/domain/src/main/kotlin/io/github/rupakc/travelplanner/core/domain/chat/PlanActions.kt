package io.github.rupakc.travelplanner.core.domain.chat

import io.github.rupakc.travelplanner.core.model.json.decodeRawBacked
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import io.github.rupakc.travelplanner.core.model.section.Tip
import io.github.rupakc.travelplanner.core.model.section.TransportOption
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.PlanActionField
import io.github.rupakc.travelplanner.core.model.stream.PlanActionType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Port of web `applyPlanAction` (ChatPage.jsx, api-contract §4.2): mutates My Plan
 * selections for one `plan_action` event. Items keep the exact JSON the server sent.
 *
 * - `clear` + `all` → empty selections; `clear` on a list field → `[]`, else `null`.
 * - `set` → the field becomes `data` (single fields); `add` → appended, deduped by
 *   `title` (tips), `key` (itinerary_slots) or `name`; `remove` → filtered by that key
 *   (single fields become `null`).
 *
 * Deviations, all safer than the web: unknown actions/fields and undecodable `data`
 * leave the selections unchanged; `set` on a list field adds (the web would store an
 * object where an array belongs).
 */
fun applyPlanAction(selections: PlanSelections, event: ChatEvent.PlanAction): PlanSelections {
    val field = event.fieldKind ?: return selections
    return when (event.action) {
        PlanActionType.CLEAR -> clear(selections, field)
        PlanActionType.SET -> if (field.isList) add(selections, field, event.data) else set(selections, field, event.data)
        PlanActionType.ADD -> if (field.isList) add(selections, field, event.data) else set(selections, field, event.data)
        PlanActionType.REMOVE -> if (field.isList) remove(selections, field, event.data) else clear(selections, field)
        null -> selections
    }
}

private fun clear(s: PlanSelections, field: PlanActionField): PlanSelections =
    when (field) {
        PlanActionField.ALL -> PlanSelections.EMPTY
        PlanActionField.FLIGHT -> s.copy(flight = null)
        PlanActionField.HOTEL -> s.copy(hotel = null)
        PlanActionField.SIM -> s.copy(sim = null)
        PlanActionField.ACTIVITIES -> s.copy(activities = emptyList())
        PlanActionField.TIPS -> s.copy(tips = emptyList())
        PlanActionField.GETTING_AROUND -> s.copy(gettingAround = emptyList())
        PlanActionField.ITINERARY_SLOTS -> s.copy(itinerarySlots = emptyList())
    }

private fun set(s: PlanSelections, field: PlanActionField, data: JsonObject?): PlanSelections {
    if (data == null) return clear(s, field)
    return when (field) {
        PlanActionField.FLIGHT -> decodeRawBacked(Flight.serializer(), data)?.let { s.copy(flight = it) } ?: s
        PlanActionField.HOTEL -> decodeRawBacked(Hotel.serializer(), data)?.let { s.copy(hotel = it) } ?: s
        PlanActionField.SIM -> decodeRawBacked(SimPlan.serializer(), data)?.let { s.copy(sim = it) } ?: s
        else -> s
    }
}

private fun add(s: PlanSelections, field: PlanActionField, data: JsonObject?): PlanSelections {
    if (data == null) return s
    return when (field) {
        PlanActionField.ACTIVITIES -> {
            decodeRawBacked(Activity.serializer(), data)?.let { s.copy(activities = s.activities.addUnique(it) { a -> a.name }) } ?: s
        }

        PlanActionField.TIPS -> {
            decodeRawBacked(Tip.serializer(), data)?.let { s.copy(tips = s.tips.addUnique(it) { t -> t.title }) } ?: s
        }

        PlanActionField.GETTING_AROUND -> {
            decodeRawBacked(TransportOption.serializer(), data)?.let { s.copy(gettingAround = s.gettingAround.addUnique(it) { o -> o.name }) } ?: s
        }

        PlanActionField.ITINERARY_SLOTS -> {
            decodeRawBacked(ItinerarySlot.serializer(), data)?.let { s.copy(itinerarySlots = s.itinerarySlots.addUnique(it) { sl -> sl.key }) } ?: s
        }

        else -> {
            s
        }
    }
}

private fun remove(s: PlanSelections, field: PlanActionField, data: JsonObject?): PlanSelections {
    if (data == null) return s
    return when (field) {
        PlanActionField.ACTIVITIES -> s.copy(activities = s.activities.filterNot { it.name == data.str("name") })
        PlanActionField.TIPS -> s.copy(tips = s.tips.filterNot { it.title == data.str("title") })
        PlanActionField.GETTING_AROUND -> s.copy(gettingAround = s.gettingAround.filterNot { it.name == data.str("name") })
        PlanActionField.ITINERARY_SLOTS -> s.copy(itinerarySlots = s.itinerarySlots.filterNot { it.key == data.str("key") })
        else -> s
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private inline fun <T> List<T>.addUnique(item: T, key: (T) -> Any?): List<T> = if (any { key(it) == key(item) }) this else this + item
