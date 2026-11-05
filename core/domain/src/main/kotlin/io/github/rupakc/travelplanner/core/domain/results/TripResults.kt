package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The streamed sections the pure results utilities read (the web passes its
 * `results` object). Errored sections may be present; utilities treat them as empty.
 */
data class TripResults(
    val flights: FlightsData? = null,
    val hotels: HotelsData? = null,
    val activities: ActivitiesData? = null,
    val walkingTours: WalkingToursData? = null,
    val placesToSee: PlacesData? = null,
    val sim: SimData? = null,
    val weather: WeatherData? = null,
    val itinerary: ItineraryData? = null,
)

/** Web `listOf(section)`: the results of a non-error section. */
internal fun <T> listOrEmpty(errored: Boolean?, items: List<T>?): List<T> = if (errored != false || items == null) emptyList() else items

internal val FlightsData?.okResults get() = listOrEmpty(this?.isError, this?.results)
internal val HotelsData?.okResults get() = listOrEmpty(this?.isError, this?.results)
internal val ActivitiesData?.okResults get() = listOrEmpty(this?.isError, this?.results)

/*
 * Legacy flat flights (`{airline, flight_number, stops, duration_minutes}` at the
 * top level) are read from the raw JSON, as the web's `f.outbound?.x ?? f.x`.
 */
private fun Flight.rawString(key: String): String? = (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun Flight.rawNumber(key: String): Double? = (raw[key] as? JsonPrimitive)?.let { p -> p.doubleOrNull?.takeIf { it.isFinite() } }

/** Outbound airline, else the flat `airline`. */
val Flight.airlineName: String? get() = outbound?.airline ?: rawString("airline")

/** Outbound flight number, else the flat `flight_number`. */
val Flight.flightNo: String? get() = outbound?.flightNumber ?: rawString("flight_number")

/** Outbound stops, else the flat `stops`. */
val Flight.outboundStops: Int? get() = outbound?.stops ?: rawNumber("stops")?.toInt()

/** Outbound duration, else the flat `duration_minutes`. */
val Flight.outboundMinutes: Int? get() = outbound?.durationMinutes ?: rawNumber("duration_minutes")?.toInt()

/** Web `flightKey` (budgetPlanner): leg, price, airline, number. */
fun flightKey(f: Flight): String =
    listOf(f.legIndex?.toString().orEmpty(), jsNumber(f.priceUsd), f.airlineName.orEmpty(), f.flightNo.orEmpty()).joinToString("|")

/** Web `sameFlight` incl. the flat legacy fields. */
fun sameFlight(a: Flight?, b: Flight?): Boolean =
    a != null && b != null && a.legIndex == b.legIndex && a.priceUsd == b.priceUsd && a.airlineName == b.airlineName && a.flightNo == b.flightNo

private fun jsNumber(v: Double?): String =
    when {
        v == null -> ""
        v == Math.floor(v) && !v.isInfinite() -> v.toLong().toString()
        else -> v.toString()
    }

/** Median of a non-empty list (average of the middle two for even sizes). */
internal fun median(values: List<Double>): Double {
    val v = values.sorted()
    val mid = v.size / 2
    return if (v.size % 2 == 1) v[mid] else (v[mid - 1] + v[mid]) / 2
}
