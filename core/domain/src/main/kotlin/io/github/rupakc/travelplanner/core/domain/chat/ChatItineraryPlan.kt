package io.github.rupakc.travelplanner.core.domain.chat

import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatItineraryDay
import io.github.rupakc.travelplanner.core.model.chat.ChatSlot
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.json.decodeRawBacked
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** The `trip_map` event of a planning turn (origin, cities in route order, dates, travellers). */
data class TripRoute(
    val origin: String? = null,
    val cities: List<String> = emptyList(),
    val departureDate: String? = null,
    val returnDate: String? = null,
    val numTravelers: Int? = null,
)

private val TIMES_OF_DAY = listOf("morning", "afternoon", "evening")

private fun ChatItineraryDay.slot(timeOfDay: String): ChatSlot? =
    when (timeOfDay) {
        "morning" -> morning
        "afternoon" -> afternoon
        else -> evening
    }

/**
 * `selections.itinerary_slots` for every filled slot of a chat itinerary, in the exact
 * shape the web's chat itinerary "Add" builds (`key "<day>-<tod>"`, `day_number`,
 * `time_of_day`, `activity`, `location`, `estimated_cost_usd`) plus `date` when known
 * (api-contract §5). Only values from the itinerary are copied; nothing is filled in.
 */
fun chatItinerarySlots(itinerary: ChatItinerary): List<ItinerarySlot> =
    itinerary.days.flatMapIndexed { index, day ->
        val dayNumber = day.dayNumber ?: (index + 1)
        TIMES_OF_DAY.mapNotNull { tod ->
            val slot = day.slot(tod)?.takeIf { !it.activity.isNullOrBlank() } ?: return@mapNotNull null
            val raw =
                buildJsonObject {
                    put("key", ItinerarySlot.slotKey(dayNumber, tod))
                    put("day_number", dayNumber)
                    day.date?.let { put("date", it) }
                    put("time_of_day", tod)
                    put("activity", slot.activity)
                    put("location", slot.place.orEmpty())
                    slot.costUsd?.let { put("estimated_cost_usd", it) }
                }
            decodeRawBacked(ItinerarySlot.serializer(), raw)
        }
    }

/**
 * The `selections.itinerary` snapshot (search-itinerary schema: `days[].slots[]`) for a
 * chat itinerary, so a plan saved from chat shows its days on the web and in My trips.
 * Costs are copied per slot only; trip totals are left out because the chat total is a
 * per-person estimate and the search total is not (no relabelling of numbers).
 */
fun chatItinerarySnapshot(itinerary: ChatItinerary): JsonObject {
    val slotsByDay = chatItinerarySlots(itinerary).groupBy { it.dayNumber }
    val days =
        itinerary.days.mapIndexed { index, day ->
            val dayNumber = day.dayNumber ?: (index + 1)
            buildJsonObject {
                put("day_number", dayNumber)
                day.date?.let { put("date", it) }
                day.city?.let { put("city", it) }
                day.theme?.let { put("theme", it) }
                put(
                    "slots",
                    JsonArray(
                        slotsByDay[dayNumber].orEmpty().map { slot ->
                            buildJsonObject {
                                slot.raw.forEach { (k, v) -> if (k != "key" && k != "day_number" && k != "date") put(k, v) }
                            }
                        },
                    ),
                )
            }
        }
    return buildJsonObject { put("days", JsonArray(days)) }
}

/**
 * The search behind a chat plan, from the stored `session_context` and the `trip_map`
 * route — or null when origin, destination or departure date is unknown (a plan is then
 * saved without search data, as the web chat does). The final city is the destination;
 * multi-city routes keep all cities in `destinations`.
 */
fun chatTripSearchRequest(sessionContext: JsonObject, route: TripRoute?): TravelSearchRequest? {
    fun ctx(key: String): String? = (sessionContext[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf(String::isNotBlank)
    val cities = route?.cities.orEmpty().filter(String::isNotBlank)
    val origin = route?.origin?.takeIf(String::isNotBlank) ?: ctx("origin") ?: return null
    val destination = cities.lastOrNull() ?: ctx("destination") ?: return null
    val departure = route?.departureDate?.takeIf(String::isNotBlank) ?: ctx("departure_date") ?: return null
    val travelers =
        route?.numTravelers?.takeIf { it > 0 }
            ?: (sessionContext["num_travelers"] as? JsonPrimitive)?.doubleOrNull?.toInt()?.takeIf { it > 0 }
            ?: 1
    val interests = (sessionContext["interests"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
    return TravelSearchRequest(
        origin = origin,
        destination = destination,
        departureDate = departure,
        returnDate = route?.returnDate?.takeIf(String::isNotBlank) ?: ctx("return_date"),
        interests = interests,
        nationality = ctx("nationality").orEmpty(),
        budgetUsd = (sessionContext["budget_usd"] as? JsonPrimitive)?.doubleOrNull,
        numTravelers = travelers,
        adults = travelers,
        destinations = cities.takeIf { it.size > 1 },
    )
}

/** Encodes [request] as plan `search_data`. */
fun TravelSearchRequest.toSearchData(): JsonObject = TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), this) as JsonObject
