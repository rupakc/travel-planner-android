package io.github.rupakc.travelplanner.feature.trips.trip

import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.feature.trips.hub.NewTripError
import io.github.rupakc.travelplanner.feature.trips.hub.validateNewTrip
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

private const val MAX_TITLE = 80

/** Mirrors the server's `trip_title_invalid` (1..80 characters once trimmed). */
fun isValidTripTitle(title: String): Boolean = title.trim().length in 1..MAX_TITLE

/** True when [start] and [end] (each blank or `YYYY-MM-DD`) are an acceptable pair; both blank clears the dates. */
fun areValidTripDates(start: String, end: String): Boolean = validateNewTrip("t", start, end) != NewTripError.Dates

/** `PATCH` body for a new title. */
fun renamePatch(title: String): JsonObject = buildJsonObject { put("title", JsonPrimitive(title.trim())) }

/** `PATCH` body for new dates; a blank value is a JSON null (clears it). */
fun datesPatch(start: String, end: String): JsonObject =
    buildJsonObject {
        put("start_date", start.trim().ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
        put("end_date", end.trim().ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
    }

/** `PATCH` body for a phase override; null clears it ("Follow my dates"). */
fun phasePatch(phase: Phase?): JsonObject = buildJsonObject { put("phase", phase?.let { JsonPrimitive(it.wire) } ?: JsonNull) }

/** A search was run for the trip (web `hasSearch`): origin and destination are both known. A dreaming trip made from the hub has neither. */
fun hasSearch(searchData: JsonObject?): Boolean {
    fun text(key: String) = ((searchData?.get(key) as? JsonPrimitive)?.takeIf { it.isString })?.contentOrNull?.isNotBlank() == true
    return text("origin") && text("destination")
}

/** Days of the trip including both ends, or null without dates (the "of N" in "Day 2 of 8"). */
fun tripLengthDays(trip: Trip): Int? {
    fun day(v: String?): LocalDate? =
        try {
            v?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }
    val s = day(trip.startDate) ?: return null
    val e = day(trip.endDate)?.takeIf { !it.isBefore(s) } ?: s
    return (ChronoUnit.DAYS.between(s, e) + 1).toInt()
}
