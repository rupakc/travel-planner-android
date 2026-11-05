package io.github.rupakc.travelplanner.core.model.plan

import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.json.decodeRawBacked
import io.github.rupakc.travelplanner.core.model.json.decodeRawBackedList
import io.github.rupakc.travelplanner.core.model.json.toJson
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.PlaceToSee
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import io.github.rupakc.travelplanner.core.model.section.Tip
import io.github.rupakc.travelplanner.core.model.section.TransportOption
import io.github.rupakc.travelplanner.core.model.section.TravelEvent
import io.github.rupakc.travelplanner.core.model.section.WalkingTour
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * My Plan `selections`, mirroring exactly the keys the web writes
 * (`EMPTY_SELECTIONS` in `frontend/src/utils/planHelpers.js`, api-contract §5).
 *
 * Items keep their original JSON ([RawBacked]) so a plan saved on the web and
 * re-saved on the phone loses nothing. Keys the app does not know are kept in
 * [extras] and written back unchanged. [itinerarySnapshot] is the
 * `selections.itinerary` the web adds on save.
 */
@Serializable(with = PlanSelectionsSerializer::class)
data class PlanSelections(
    val flight: Flight? = null,
    val flights: List<Flight> = emptyList(),
    val hotel: Hotel? = null,
    val activities: List<Activity> = emptyList(),
    val placesToSee: List<PlaceToSee> = emptyList(),
    val walkingTours: List<WalkingTour> = emptyList(),
    val sim: SimPlan? = null,
    val tips: List<Tip> = emptyList(),
    val gettingAround: List<TransportOption> = emptyList(),
    val events: List<TravelEvent> = emptyList(),
    /** `{ "<slotKey>": "text" }` */
    val itineraryNotes: Map<String, String> = emptyMap(),
    /** `{ "<slotKey>": {activity?, cost?, …} }` (inner keys UNVERIFIED beyond activity/cost). */
    val itineraryEdits: Map<String, JsonObject> = emptyMap(),
    val itinerarySlots: List<ItinerarySlot> = emptyList(),
    val itineraryLayout: ItineraryLayout? = null,
    /** `null` or `{categories, luggage_note, checked_items, custom_items, total_items, checked_count}`. */
    val packingList: JsonObject? = null,
    /** `selections.itinerary` snapshot `{days, total_estimated_cost_usd}` added by the web on save. */
    val itinerarySnapshot: JsonObject? = null,
    /** Unknown top-level keys, preserved verbatim. */
    val extras: Map<String, JsonElement> = emptyMap(),
) {
    val itinerary: ItineraryData?
        get() = itinerarySnapshot?.let { runCatching { TravelJson.decodeFromJsonElement(ItineraryData.serializer(), it) }.getOrNull() }

    /** Web `countSelections`. */
    val count: Int
        get() =
            (if (flight != null) 1 else 0) + flights.size + (if (hotel != null) 1 else 0) + activities.size +
                placesToSee.size + walkingTours.size + (if (sim != null) 1 else 0) + gettingAround.size + tips.size +
                events.size + itinerarySlots.size + (if (packingList != null) 1 else 0)

    val isEmpty: Boolean get() = count == 0

    companion object {
        val EMPTY = PlanSelections()
    }
}

/** Itinerary overlay (`utils/itineraryLayout.js`): positions map original slot key → new key or null (removed). */
@Serializable
data class ItineraryLayout(val fingerprint: String? = null, val positions: Map<String, String?> = emptyMap())

/** Wire keys of [PlanSelections], in the web's order. */
object SelectionKeys {
    const val FLIGHT = "flight"
    const val FLIGHTS = "flights"
    const val HOTEL = "hotel"
    const val ACTIVITIES = "activities"
    const val PLACES_TO_SEE = "places_to_see"
    const val WALKING_TOURS = "walking_tours"
    const val SIM = "sim"
    const val TIPS = "tips"
    const val GETTING_AROUND = "getting_around"
    const val EVENTS = "events"
    const val ITINERARY_NOTES = "itinerary_notes"
    const val ITINERARY_EDITS = "itinerary_edits"
    const val ITINERARY_SLOTS = "itinerary_slots"
    const val ITINERARY_LAYOUT = "itinerary_layout"
    const val PACKING_LIST = "packing_list"
    const val ITINERARY = "itinerary"

    val ALL =
        listOf(
            FLIGHT,
            FLIGHTS,
            HOTEL,
            ACTIVITIES,
            PLACES_TO_SEE,
            WALKING_TOURS,
            SIM,
            TIPS,
            GETTING_AROUND,
            EVENTS,
            ITINERARY_NOTES,
            ITINERARY_EDITS,
            ITINERARY_SLOTS,
            ITINERARY_LAYOUT,
            PACKING_LIST,
            ITINERARY,
        )
}

object PlanSelectionsSerializer : KSerializer<PlanSelections> {
    override val descriptor: SerialDescriptor = SerialDescriptor("travelplanner.PlanSelections", JsonObject.serializer().descriptor)

    override fun deserialize(decoder: Decoder): PlanSelections {
        val jsonDecoder = decoder as? JsonDecoder ?: error("PlanSelections can only be decoded from JSON")
        val obj = jsonDecoder.decodeJsonElement() as? JsonObject ?: return PlanSelections()
        return fromJson(obj, jsonDecoder.json)
    }

    override fun serialize(encoder: Encoder, value: PlanSelections) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("PlanSelections can only be encoded to JSON")
        jsonEncoder.encodeJsonElement(toJson(value, jsonEncoder.json))
    }

    fun fromJson(obj: JsonObject, json: Json = TravelJson): PlanSelections =
        PlanSelections(
            flight = decodeRawBacked(Flight.serializer(), obj[SelectionKeys.FLIGHT], json),
            flights = decodeRawBackedList(Flight.serializer(), obj[SelectionKeys.FLIGHTS], json),
            hotel = decodeRawBacked(Hotel.serializer(), obj[SelectionKeys.HOTEL], json),
            activities = decodeRawBackedList(Activity.serializer(), obj[SelectionKeys.ACTIVITIES], json),
            placesToSee = decodeRawBackedList(PlaceToSee.serializer(), obj[SelectionKeys.PLACES_TO_SEE], json),
            walkingTours = decodeRawBackedList(WalkingTour.serializer(), obj[SelectionKeys.WALKING_TOURS], json),
            sim = decodeRawBacked(SimPlan.serializer(), obj[SelectionKeys.SIM], json),
            tips = decodeRawBackedList(Tip.serializer(), obj[SelectionKeys.TIPS], json),
            gettingAround = decodeRawBackedList(TransportOption.serializer(), obj[SelectionKeys.GETTING_AROUND], json),
            events = decodeRawBackedList(TravelEvent.serializer(), obj[SelectionKeys.EVENTS], json),
            itineraryNotes =
                (obj[SelectionKeys.ITINERARY_NOTES] as? JsonObject)
                    ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.let { k to it } }
                    ?.toMap().orEmpty(),
            itineraryEdits =
                (obj[SelectionKeys.ITINERARY_EDITS] as? JsonObject)
                    ?.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }?.toMap().orEmpty(),
            itinerarySlots = decodeRawBackedList(ItinerarySlot.serializer(), obj[SelectionKeys.ITINERARY_SLOTS], json),
            itineraryLayout =
                (obj[SelectionKeys.ITINERARY_LAYOUT] as? JsonObject)
                    ?.let { runCatching { json.decodeFromJsonElement(ItineraryLayout.serializer(), it) }.getOrNull() },
            packingList = obj[SelectionKeys.PACKING_LIST] as? JsonObject,
            itinerarySnapshot = obj[SelectionKeys.ITINERARY] as? JsonObject,
            extras = obj.filterKeys { it !in SelectionKeys.ALL },
        )

    fun toJson(value: PlanSelections, json: Json = TravelJson): JsonObject =
        buildJsonObject {
            fun <T : RawBacked<T>> one(key: String, item: T?, serializer: KSerializer<T>) =
                put(key, item?.toJson(serializer, json) ?: JsonNull)

            fun <T : RawBacked<T>> many(key: String, items: List<T>, serializer: KSerializer<T>) =
                put(key, JsonArray(items.map { it.toJson(serializer, json) }))

            one(SelectionKeys.FLIGHT, value.flight, Flight.serializer())
            many(SelectionKeys.FLIGHTS, value.flights, Flight.serializer())
            one(SelectionKeys.HOTEL, value.hotel, Hotel.serializer())
            many(SelectionKeys.ACTIVITIES, value.activities, Activity.serializer())
            many(SelectionKeys.PLACES_TO_SEE, value.placesToSee, PlaceToSee.serializer())
            many(SelectionKeys.WALKING_TOURS, value.walkingTours, WalkingTour.serializer())
            one(SelectionKeys.SIM, value.sim, SimPlan.serializer())
            many(SelectionKeys.TIPS, value.tips, Tip.serializer())
            many(SelectionKeys.GETTING_AROUND, value.gettingAround, TransportOption.serializer())
            many(SelectionKeys.EVENTS, value.events, TravelEvent.serializer())
            put(SelectionKeys.ITINERARY_NOTES, JsonObject(value.itineraryNotes.mapValues { JsonPrimitive(it.value) }))
            put(SelectionKeys.ITINERARY_EDITS, JsonObject(value.itineraryEdits))
            many(SelectionKeys.ITINERARY_SLOTS, value.itinerarySlots, ItinerarySlot.serializer())
            put(
                SelectionKeys.ITINERARY_LAYOUT,
                value.itineraryLayout?.let { json.encodeToJsonElement(ItineraryLayout.serializer(), it) } ?: JsonNull,
            )
            put(SelectionKeys.PACKING_LIST, value.packingList ?: JsonNull)
            value.itinerarySnapshot?.let { put(SelectionKeys.ITINERARY, it) }
            value.extras.forEach { (k, v) -> put(k, v) }
        }
}
