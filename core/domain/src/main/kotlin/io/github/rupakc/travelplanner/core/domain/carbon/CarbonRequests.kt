package io.github.rupakc.travelplanner.core.domain.carbon

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** Meals choice of the footprint (web `DIETS`); [wire] is the request value. */
enum class CarbonDiet(val wire: String) {
    MIXED("mixed"),
    MEAT_HEAVY("meat_heavy"),
    PESCATARIAN("pescatarian"),
    VEGETARIAN("vegetarian"),
    VEGAN("vegan"),
    ;

    companion object {
        /** The diet for [wire]; anything unknown is [MIXED] (the backend default). */
        fun fromWire(wire: String?): CarbonDiet = entries.firstOrNull { it.wire == wire } ?: MIXED
    }
}

/**
 * Request bodies for `POST /api/carbon/trip`, a port of the web's `utils/carbon.js`. The backend does all the
 * arithmetic; these only trim what the screen holds to the fields the calculator reads (the body is capped at
 * 512 KB, and a new field on a result never changes the request). Everything is built from the objects received
 * (their raw JSON), so nothing is invented. Pure.
 */
object CarbonRequests {
    private val SEARCH_FIELDS =
        listOf(
            "origin",
            "destination",
            "destinations",
            "destination_nights",
            "departure_date",
            "return_date",
            "num_travelers",
            "adults",
            "children",
            "seniors",
        )
    private val LEG_FIELDS = listOf("origin", "destination", "stops", "emissions", "leg_index", "flight_number", "departure_date")
    private val SEGMENT_FIELDS = listOf("flight_number", "origin", "destination", "departure_date")
    private val HOTEL_FIELDS = listOf("name", "city", "country_code")
    private val SLOT_FIELDS = listOf("activity", "lat", "lng")
    private const val MAX_DAYS = 60
    private const val MAX_SLOTS = 12

    /** The card on the Results page: the cheapest flight (per leg on multi-city), the first hotel, the itinerary. */
    fun fromResults(search: JsonObject?, flights: JsonObject?, hotels: JsonObject?, itinerary: JsonObject?, diet: CarbonDiet): JsonObject {
        val legs = (flights?.get("legs") as? JsonArray)?.filterIsInstance<JsonObject>()
        val flightsBody =
            when {
                !legs.isNullOrEmpty() -> {
                    buildJsonObject {
                        put("legs", JsonArray(legs.map { leg -> buildJsonObject { put("results", cheapestTrimmed(leg["results"])) } }))
                    }
                }

                flights?.get("results") is JsonArray -> {
                    buildJsonObject { put("results", cheapestTrimmed(flights["results"])) }
                }

                else -> {
                    JsonObject(emptyMap())
                }
            }
        val hotel = ((hotels?.get("results") as? JsonArray)?.firstOrNull() as? JsonObject)?.let { pick(it, HOTEL_FIELDS) }
        return buildJsonObject {
            put("search", pick(search, SEARCH_FIELDS))
            put(
                "results",
                buildJsonObject {
                    put("flights", flightsBody)
                    put("hotels", buildJsonObject { put("results", JsonArray(listOfNotNull(hotel))) })
                },
            )
            put("diet", diet.wire)
            trimItinerary(itinerary)?.let { put("itinerary", it) }
        }
    }

    /** The card in My Plan: the plan's `selections` JSON (flight, flights, hotel, getting_around, activities). */
    fun fromSelections(search: JsonObject?, selections: JsonObject?, itinerary: JsonObject?, diet: CarbonDiet): JsonObject {
        val s = selections ?: JsonObject(emptyMap())
        return buildJsonObject {
            put("search", pick(search, SEARCH_FIELDS))
            put(
                "selections",
                buildJsonObject {
                    put("flight", trimFlight(s["flight"] as? JsonObject) ?: JsonNull)
                    put("flights", JsonArray(objects(s["flights"]).mapNotNull(::trimFlight)))
                    put("hotel", (s["hotel"] as? JsonObject)?.let { pick(it, HOTEL_FIELDS) } ?: JsonNull)
                    put("getting_around", JsonArray(objects(s["getting_around"]).map { pick(it, listOf("type", "scope")) }))
                    put("activities", JsonArray(objects(s["activities"]).map { pick(it, listOf("name")) }))
                },
            )
            put("diet", diet.wire)
            trimItinerary(itinerary)?.let { put("itinerary", it) }
        }
    }

    /** Web `selectionsHaveTravel`: the plan card shows only once a flight or a hotel is picked. */
    fun selectionsHaveTravel(selections: JsonObject?): Boolean {
        val s = selections ?: return false
        return s["flight"] is JsonObject || objects(s["flights"]).isNotEmpty() || s["hotel"] is JsonObject
    }

    /** The lowest-priced result, the first among equals; a missing or 0 price (SerpAPI's "none") sorts last. */
    fun cheapest(results: JsonElement?): JsonObject? {
        var best: JsonObject? = null
        for (r in objects(results)) if (best == null || priceOf(r) < priceOf(best)) best = r
        return best
    }

    /** Days (≤ 60) of slots (≤ 12 each) with only `activity`, `lat`, `lng`; null when there are no days. */
    fun trimItinerary(itinerary: JsonObject?): JsonObject? {
        val days = itinerary?.get("days") as? JsonArray
        if (days.isNullOrEmpty()) return null
        return buildJsonObject {
            put(
                "days",
                JsonArray(
                    days.take(MAX_DAYS).map { day ->
                        val slots = ((day as? JsonObject)?.get("slots") as? JsonArray).orEmpty()
                        buildJsonObject { put("slots", JsonArray(slots.take(MAX_SLOTS).map { pick(it as? JsonObject, SLOT_FIELDS) })) }
                    },
                ),
            )
        }
    }

    /** A flight with only the fields the calculator reads, at the top level and on `outbound` / `return`. */
    fun trimFlight(flight: JsonObject?): JsonObject? {
        flight ?: return null
        val top = trimLeg(flight).toMutableMap()
        (flight["outbound"] as? JsonObject)?.let { top["outbound"] = trimLeg(it) }
        (flight["return"] as? JsonObject)?.let { top["return"] = trimLeg(it) }
        return JsonObject(top)
    }

    private fun trimLeg(leg: JsonObject): JsonObject {
        val out = pick(leg, LEG_FIELDS).toMutableMap()
        (leg["layovers"] as? JsonArray)?.let { list -> out["layovers"] = JsonArray(list.map { pick(it as? JsonObject, listOf("airport")) }) }
        (leg["segments"] as? JsonArray)?.let { list -> out["segments"] = JsonArray(list.map { pick(it as? JsonObject, SEGMENT_FIELDS) }) }
        return JsonObject(out)
    }

    private fun cheapestTrimmed(results: JsonElement?): JsonArray = JsonArray(listOfNotNull(trimFlight(cheapest(results))))

    private fun priceOf(r: JsonObject): Double {
        val raw = r["price_usd"]?.takeUnless { it is JsonNull } ?: r["price"]?.takeUnless { it is JsonNull } ?: return Double.POSITIVE_INFINITY
        val v = (raw as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.trim().toDoubleOrNull() }
        return if (v != null && v.isFinite() && v > 0) v else Double.POSITIVE_INFINITY
    }

    private fun objects(element: JsonElement?): List<JsonObject> = (element as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

    /** Web `pick`: the listed keys whose values are present and not null. */
    private fun pick(obj: JsonObject?, keys: List<String>): JsonObject {
        obj ?: return JsonObject(emptyMap())
        val out = LinkedHashMap<String, JsonElement>()
        for (k in keys) obj[k]?.takeUnless { it is JsonNull }?.let { out[k] = it }
        return JsonObject(out)
    }
}
