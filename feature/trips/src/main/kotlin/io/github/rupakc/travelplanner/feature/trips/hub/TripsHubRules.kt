package io.github.rupakc.travelplanner.feature.trips.hub

import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Trips of one phase, in the served order. */
data class PhaseGroup(val phase: Phase, val trips: List<Trip>)

/** The hub's phase groups, keeping the served order; the pinned "Next up" trip is not repeated. An unknown phase counts as planning. */
fun groupByPhase(trips: List<Trip>, pinnedId: Long?): List<PhaseGroup> {
    val groups = LinkedHashMap<Phase, MutableList<Trip>>()
    trips.filter { it.id != pinnedId }.forEach { groups.getOrPut(it.phase ?: Phase.PLANNING) { mutableListOf() } += it }
    return groups.map { (phase, list) -> PhaseGroup(phase, list) }
}

/** Which field of the new-trip form is wrong. */
enum class NewTripError { Title, Dates }

private const val MAX_TITLE = 80
private const val MAX_SPAN_DAYS = 365L
private const val MIN_YEAR = 2000
private const val MAX_YEAR = 2100

private fun day(text: String): LocalDate? =
    try {
        LocalDate.parse(text.trim())
    } catch (_: DateTimeParseException) {
        null
    }

/** Mirrors the server's `trip_title_invalid` / `trip_dates_invalid` (Trips-API) so the form fails before the request. Dates are optional. */
fun validateNewTrip(title: String, start: String, end: String): NewTripError? {
    if (title.trim().length !in 1..MAX_TITLE) return NewTripError.Title
    val hasStart = start.isNotBlank()
    val hasEnd = end.isNotBlank()
    if (!hasStart && !hasEnd) return null
    val s = if (hasStart) day(start) ?: return NewTripError.Dates else null
    val e = if (hasEnd) day(end) ?: return NewTripError.Dates else null
    val bad =
        (s == null && e != null) ||
            listOfNotNull(s, e).any { it.year !in MIN_YEAR..MAX_YEAR } ||
            (s != null && e != null && (e.isBefore(s) || ChronoUnit.DAYS.between(s, e) > MAX_SPAN_DAYS))
    return if (bad) NewTripError.Dates else null
}

/** The field a backend `error_code` blames, or null for any other code. */
fun newTripErrorForCode(code: String?): NewTripError? =
    when (code) {
        "trip_dates_invalid" -> NewTripError.Dates
        "trip_title_invalid" -> NewTripError.Title
        else -> null
    }
