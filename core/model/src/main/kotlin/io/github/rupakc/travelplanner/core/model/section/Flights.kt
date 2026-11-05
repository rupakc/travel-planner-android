package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.json.RawBackedListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/** §3.4.1 flights. Multi-city: [tripType] = "multi_city", [legs] grouped, [results] flattened. */
@Serializable
data class FlightsData(
    @Serializable(with = FlightListSerializer::class) val results: List<Flight> = emptyList(),
    /** Live price insights (shape not modelled; UNVERIFIED keys). */
    @SerialName("price_insights") val priceInsights: JsonObject? = null,
    @SerialName("trip_type") val tripType: String? = null,
    val legs: List<FlightLegGroup> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData {
    val isMultiCity: Boolean get() = tripType == "multi_city"
}

@Serializable
data class Flight(
    /** Total price in USD. */
    @SerialName("price_usd") val priceUsd: LenientDouble = null,
    /** round_trip | one_way */
    @SerialName("trip_type") val tripType: String? = null,
    @SerialName("booking_url") val bookingUrl: String? = null,
    /** "google_flights" (live), "estimate", or a platform name. */
    val source: String? = null,
    val outbound: FlightLeg? = null,
    @SerialName("return") val returnLeg: FlightLeg? = null,
    @SerialName("live_fare") val liveFare: LenientBoolean = null,
    @SerialName("carbon_emissions_grams") val carbonEmissionsGrams: LenientInt = null,
    @SerialName("often_delayed") val oftenDelayed: LenientBoolean = null,
    @SerialName("leg_index") val legIndex: LenientInt = null,
    @SerialName("leg_from") val legFrom: String? = null,
    @SerialName("leg_to") val legTo: String? = null,
    @SerialName("leg_date") val legDate: String? = null,
    val city: String? = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<Flight> {
    override fun withRaw(raw: JsonObject): Flight = copy(raw = raw)

    val isEstimate: Boolean get() = source == "estimate"

    /** Web `sameFlight`: same leg index, price, airline and flight number. */
    fun sameAs(other: Flight): Boolean =
        legIndex == other.legIndex &&
            priceUsd == other.priceUsd &&
            outbound?.airline == other.outbound?.airline &&
            outbound?.flightNumber == other.outbound?.flightNumber
}

@Serializable
data class FlightLeg(
    val airline: String? = null,
    @SerialName("flight_number") val flightNumber: String? = null,
    val origin: String? = null,
    val destination: String? = null,
    @SerialName("departure_date") val departureDate: String? = null,
    /** "HH:MM" */
    @SerialName("departure_time") val departureTime: String? = null,
    /** "HH:MM" or "15:30+1" (next day). */
    @SerialName("arrival_time") val arrivalTime: String? = null,
    @SerialName("duration_minutes") val durationMinutes: LenientInt = null,
    val stops: LenientInt = null,
    val layovers: List<FlightLayover> = emptyList(),
)

@Serializable
data class FlightLayover(
    val city: String? = null,
    val airport: String? = null,
    @SerialName("duration_hours") val durationHours: LenientDouble = null,
)

/** Multi-city leg group. */
@Serializable
data class FlightLegGroup(
    @SerialName("leg_index") val legIndex: LenientInt = null,
    val label: String? = null,
    val date: String? = null,
    val from: String? = null,
    val to: String? = null,
    @Serializable(with = FlightListSerializer::class) val results: List<Flight> = emptyList(),
    val error: String? = null,
)

object FlightListSerializer : RawBackedListSerializer<Flight>(Flight.serializer())

/** §3.4.2 weather (flat days, even for multi-city). */
@Serializable
data class WeatherData(
    val days: List<WeatherDay> = emptyList(),
    @SerialName("poor_weather_day_count") val poorWeatherDayCount: LenientInt = null,
    /** open-meteo | llm-estimate */
    val source: String? = null,
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

@Serializable
data class WeatherDay(
    val date: String? = null,
    val city: String? = null,
    val description: String? = null,
    @SerialName("weather_code") val weatherCode: LenientInt = null,
    @SerialName("temp_high_c") val tempHighC: LenientDouble = null,
    @SerialName("temp_low_c") val tempLowC: LenientDouble = null,
    @SerialName("precipitation_mm") val precipitationMm: LenientDouble = null,
    @SerialName("wind_kmh") val windKmh: LenientDouble = null,
    @SerialName("is_poor") val isPoor: LenientBoolean = null,
)
