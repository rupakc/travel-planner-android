package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/*
 * Test helpers that mirror the web tests' object literals: `obj("k" to v, …)` builds the JSON
 * the backend would send, and the `…Of` functions decode it with the app's real decoders.
 * `UNDEFINED` drops a key (the web's `{ ...x, key: undefined }`).
 */

internal object Undefined

internal val UNDEFINED = Undefined

internal fun toJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.filter { it.value !== Undefined }.associate { (k, v) -> k.toString() to toJson(v) })
        is List<*> -> JsonArray(value.map(::toJson))
        else -> error("Unsupported test value $value")
    }

internal fun obj(vararg pairs: Pair<String, Any?>): JsonObject = toJson(mapOf(*pairs)) as JsonObject

/** `{ ...base, ...over }` */
internal fun JsonObject.with(vararg pairs: Pair<String, Any?>): JsonObject {
    val over = mapOf(*pairs)
    return JsonObject((this + over.filterValues { it !== Undefined }.mapValues { toJson(it.value) }) - over.filterValues { it === Undefined }.keys)
}

internal fun obj(json: String): JsonObject = TravelJson.parseToJsonElement(json).jsonObject

private fun decode(id: SectionId, o: JsonObject) = SectionDecoder.decode(id, o)

internal fun flightsOf(o: JsonObject) = decode(SectionId.FLIGHTS, o) as FlightsData

internal fun flightsOf(json: String) = flightsOf(obj(json))

internal fun hotelsOf(o: JsonObject) = decode(SectionId.HOTELS, o) as HotelsData

internal fun hotelsOf(json: String) = hotelsOf(obj(json))

internal fun activitiesOf(o: JsonObject) = decode(SectionId.ACTIVITIES, o) as ActivitiesData

internal fun activitiesOf(json: String) = activitiesOf(obj(json))

internal fun placesOf(o: JsonObject) = decode(SectionId.PLACES_TO_SEE, o) as PlacesData

internal fun toursOf(o: JsonObject) = decode(SectionId.WALKING_TOURS, o) as WalkingToursData

internal fun simOf(o: JsonObject) = decode(SectionId.SIM, o) as SimData

internal fun weatherOf(o: JsonObject) = decode(SectionId.WEATHER, o) as WeatherData

internal fun itineraryOf(o: JsonObject) = decode(SectionId.ITINERARY, o) as ItineraryData

internal fun selectionsOf(o: JsonObject) = PlanSelectionsSerializer.fromJson(o)

internal fun selectionsOf(json: String) = selectionsOf(obj(json))

/** A results object like the web's `{ flights: {...}, hotels: {...}, … }`. */
internal fun results(o: JsonObject): TripResults {
    fun section(key: String) = o[key] as? JsonObject
    return TripResults(
        flights = section("flights")?.let(::flightsOf),
        hotels = section("hotels")?.let(::hotelsOf),
        activities = section("activities")?.let(::activitiesOf),
        walkingTours = section("walking_tours")?.let(::toursOf),
        placesToSee = section("places_to_see")?.let(::placesOf),
        sim = section("sim")?.let(::simOf),
        weather = section("weather")?.let(::weatherOf),
        itinerary = section("itinerary")?.let(::itineraryOf),
    )
}

/** A search request with the web tests' `searchData` fields (other required fields are placeholders). */
internal fun search(
    departure: String = "2026-04-01",
    returnDate: String? = "2026-04-08",
    budget: Double? = 3000.0,
    destinations: List<String>? = null,
    destination: String = destinations?.last() ?: "Paris",
    adults: Int = 1,
    children: Int = 0,
    seniors: Int = 0,
    numTravelers: Int = 1,
    nights: List<Int?>? = null,
) = TravelSearchRequest(
    origin = "NYC",
    destination = destination,
    departureDate = departure,
    returnDate = returnDate,
    nationality = "American",
    budgetUsd = budget,
    destinations = destinations,
    adults = adults,
    children = children,
    seniors = seniors,
    numTravelers = numTravelers,
    destinationNights = nights,
)
