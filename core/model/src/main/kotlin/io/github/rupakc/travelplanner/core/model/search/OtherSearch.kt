package io.github.rupakc.travelplanner.core.model.search

import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientStringList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** `POST /api/discover` body ("Surprise me"). */
@Serializable
data class DiscoveryRequest(
    val origin: String,
    @SerialName("departure_date") val departureDate: String,
    val nationality: String,
    @SerialName("budget_usd") val budgetUsd: Double? = null,
    @SerialName("return_date") val returnDate: String? = null,
    val interests: List<String> = emptyList(),
    val adults: Int = 1,
    val children: Int = 0,
    val seniors: Int = 0,
    val infants: Int = 0,
)

/** `POST /api/discover` response (keys from `.agents/discovery.md` + `discovery_agent.py`). */
@Serializable
data class DiscoveryResponse(val destinations: List<DiscoveredDestination> = emptyList())

@Serializable
data class DiscoveredDestination(
    val city: String? = null,
    val country: String? = null,
    /** Rough AI estimate — always label as an estimate. */
    @SerialName("estimated_cost_usd_low") val estimatedCostUsdLow: LenientDouble = null,
    @SerialName("estimated_cost_usd_high") val estimatedCostUsdHigh: LenientDouble = null,
    @SerialName("visa_type") val visaType: String? = null,
    /** false → the UI shows an amber "Verify" badge. */
    @SerialName("visa_verified") val visaVerified: LenientBoolean = null,
    @SerialName("weather_emoji") val weatherEmoji: String? = null,
    @SerialName("weather_description") val weatherDescription: String? = null,
    @SerialName("flight_duration_hours") val flightDurationHours: LenientDouble = null,
    @SerialName("flight_duration_label") val flightDurationLabel: String? = null,
    @SerialName("match_reasons") val matchReasons: LenientStringList = emptyList(),
    val highlights: LenientStringList = emptyList(),
)

/** `POST /api/compare/destination` response (`services/compare.py::summarize_destination`). */
@Serializable
data class CompareSummary(
    val destination: String? = null,
    val label: String? = null,
    val flight: CompareFlight? = null,
    val hotel: CompareHotel? = null,
    val weather: CompareWeather? = null,
    val visa: CompareVisa? = null,
    val ease: CompareEase? = null,
    @SerialName("daily_cost_usd") val dailyCostUsd: LenientDouble = null,
    val estimate: CompareEstimate? = null,
    /** Pieces that failed: subset of flights, hotels, weather, visa. */
    val missing: List<String> = emptyList(),
)

@Serializable
data class CompareFlight(
    @SerialName("price_usd") val priceUsd: LenientDouble = null,
    val stops: LenientInt = null,
    @SerialName("duration_minutes") val durationMinutes: LenientInt = null,
    val airline: String? = null,
    val source: String? = null,
)

@Serializable
data class CompareHotel(
    @SerialName("typical_nightly_usd") val typicalNightlyUsd: LenientDouble = null,
    @SerialName("cheapest_good_nightly_usd") val cheapestGoodNightlyUsd: LenientDouble = null,
    val count: LenientInt = null,
)

@Serializable
data class CompareWeather(
    @SerialName("avg_high_c") val avgHighC: LenientDouble = null,
    @SerialName("avg_low_c") val avgLowC: LenientDouble = null,
    @SerialName("poor_days") val poorDays: LenientInt = null,
    @SerialName("total_days") val totalDays: LenientInt = null,
    val source: String? = null,
)

@Serializable
data class CompareVisa(
    @SerialName("visa_type") val visaType: String? = null,
    @SerialName("max_stay_days") val maxStayDays: LenientInt = null,
    @SerialName("fee_usd") val feeUsd: LenientDouble = null,
    @SerialName("processing_time") val processingTime: String? = null,
    val verified: LenientBoolean = null,
)

@Serializable
data class CompareEase(
    val total: LenientInt = null,
    val grade: String? = null,
    val safety: LenientInt = null,
    val english: LenientInt = null,
    val infrastructure: LenientInt = null,
    @SerialName("visa_ease") val visaEase: LenientInt = null,
)

@Serializable
data class CompareEstimate(
    @SerialName("total_usd") val totalUsd: LenientDouble = null,
    @SerialName("flight_usd") val flightUsd: LenientDouble = null,
    @SerialName("stay_usd") val stayUsd: LenientDouble = null,
    @SerialName("daily_usd") val dailyUsd: LenientDouble = null,
    @SerialName("included_in_daily") val includedInDaily: LenientBoolean = null,
    val nights: LenientInt = null,
    val partial: LenientBoolean = null,
    /** over | under | null */
    @SerialName("budget_status") val budgetStatus: String? = null,
)

/** `POST /api/flights/fare-calendar` response. */
@Serializable
data class FareCalendar(
    val dates: List<FareCalendarDate> = emptyList(),
    val currency: String? = null,
    val source: String? = null,
)

@Serializable
data class FareCalendarDate(
    @SerialName("departure_date") val departureDate: String? = null,
    @SerialName("return_date") val returnDate: String? = null,
    @SerialName("cheapest_usd") val cheapestUsd: LenientDouble = null,
    @SerialName("is_selected") val isSelected: LenientBoolean = null,
)

/** `POST /api/layover` body. */
@Serializable
data class LayoverRequest(
    val city: String,
    @SerialName("duration_hours") val durationHours: Double,
    val nationality: String,
    val airport: String? = null,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    val interests: List<String> = emptyList(),
)

/** `POST /api/layover` response (keys from `.agents/layover.md`; UNVERIFIED at runtime). */
@Serializable
data class LayoverPlan(
    @SerialName("feasible_to_exit") val feasibleToExit: LenientBoolean = null,
    @SerialName("transit_visa") val transitVisa: TransitVisa? = null,
    @SerialName("usable_city_hours") val usableCityHours: LenientDouble = null,
    @SerialName("buffer_advice") val bufferAdvice: String? = null,
    @SerialName("verdict_summary") val verdictSummary: String? = null,
    val plan: List<LayoverStep> = emptyList(),
    @SerialName("airside_alternative") val airsideAlternative: String? = null,
    val notes: LenientStringList = emptyList(),
    val error: String? = null,
)

@Serializable
data class TransitVisa(val required: LenientBoolean = null, val notes: String? = null)

@Serializable
data class LayoverStep(
    @SerialName("time_slot") val timeSlot: String? = null,
    val activity: String? = null,
    @SerialName("cost_usd") val costUsd: LenientDouble = null,
)

/**
 * `POST /api/stress-test` body: a [TravelSearchRequest] plus `itinerary` (required,
 * `{days:[…]}`) and optional `flights`, `visa`, `weather` section objects.
 * Built as JSON by the repository (see `StressTestPayload.toJson`).
 */
data class StressTestPayload(
    val request: TravelSearchRequest,
    val itinerary: JsonObject,
    val flights: JsonObject? = null,
    val visa: JsonObject? = null,
    val weather: JsonObject? = null,
)
