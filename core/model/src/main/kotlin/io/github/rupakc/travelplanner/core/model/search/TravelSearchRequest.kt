package io.github.rupakc.travelplanner.core.model.search

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared request model (api-contract §1). Dates are ISO `YYYY-MM-DD` strings.
 * `taste_context` is set server-side and deliberately not modelled.
 */
@Serializable
data class TravelSearchRequest(
    val origin: String,
    val destination: String,
    @SerialName("departure_date") val departureDate: String,
    @SerialName("return_date") val returnDate: String? = null,
    val interests: List<String> = emptyList(),
    val nationality: String,
    @SerialName("residence_permits") val residencePermits: List<String> = emptyList(),
    @SerialName("existing_visas") val existingVisas: List<String> = emptyList(),
    @SerialName("budget_usd") val budgetUsd: Double? = null,
    @SerialName("num_travelers") val numTravelers: Int = 1,
    val adults: Int = 1,
    val children: Int = 0,
    val seniors: Int = 0,
    val infants: Int = 0,
    @SerialName("accessibility_needs") val accessibilityNeeds: List<String> = emptyList(),
    /** Multi-city ordered list; final destination last (same as [destination]). */
    val destinations: List<String>? = null,
    @SerialName("destination_nights") val destinationNights: List<Int?>? = null,
    /** relaxed | balanced | packed */
    val pace: String = PACE_BALANCED,
    /** 0.0..1.0 */
    val serendipity: Double = 0.5,
    @SerialName("must_include") val mustInclude: List<MustIncludePlace>? = null,
    @SerialName("import_source") val importSource: ImportSource? = null,
) {
    val isMultiCity: Boolean get() = (destinations?.size ?: 0) > 1

    companion object {
        const val PACE_RELAXED = "relaxed"
        const val PACE_BALANCED = "balanced"
        const val PACE_PACKED = "packed"
    }
}

@Serializable
data class MustIncludePlace(
    val name: String,
    val kind: String = "sight",
    val city: String? = null,
    val town: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String? = null,
    val quote: String? = null,
    val verified: Boolean = false,
)

@Serializable
data class ImportSource(val url: String? = null, val title: String? = null)

/** Extra fields of `/api/flights/filtered` (merged into the request JSON). */
@Serializable
data class FlightFilters(
    @SerialName("max_stops") val maxStops: Int? = null,
    @SerialName("max_price_usd") val maxPriceUsd: Double? = null,
    @SerialName("departure_time_earliest") val departureTimeEarliest: String? = null,
    @SerialName("departure_time_latest") val departureTimeLatest: String? = null,
    @SerialName("arrival_time_earliest") val arrivalTimeEarliest: String? = null,
    @SerialName("arrival_time_latest") val arrivalTimeLatest: String? = null,
)

/** Extra fields of `/api/hotels/filtered`. */
@Serializable
data class HotelFilters(
    @SerialName("num_beds") val numBeds: Int? = null,
    @SerialName("max_price_per_night_usd") val maxPricePerNightUsd: Double? = null,
    /** basic | good | excellent */
    @SerialName("wifi_quality") val wifiQuality: String? = null,
    @SerialName("max_distance_from_center_km") val maxDistanceFromCenterKm: Double? = null,
    @SerialName("private_washroom") val privateWashroom: Boolean? = null,
)

/** Extra fields of `/api/activities/filtered`. */
@Serializable
data class ActivityFilters(
    @SerialName("filter_interests") val filterInterests: List<String>? = null,
    @SerialName("max_price_usd") val maxPriceUsd: Double? = null,
    @SerialName("available_from") val availableFrom: String? = null,
    @SerialName("available_to") val availableTo: String? = null,
    @SerialName("min_rating") val minRating: Double? = null,
)
