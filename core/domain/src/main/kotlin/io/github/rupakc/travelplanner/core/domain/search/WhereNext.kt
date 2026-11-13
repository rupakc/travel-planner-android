package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.search.DiscoveryRequest
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate

/** At most this many suggestions are shown after a trip. */
const val WHERE_NEXT_MAX = 5

private const val LEAD_DAYS = 45L
private const val DEFAULT_TRIP_DAYS = 7
private const val MIN_TRIP_DAYS = 3
private const val MAX_TRIP_DAYS = 14

/**
 * The `POST /api/discover` body for "Where next" after a trip: origin, nationality and interests come from the trip's
 * search (preferences fill what it lacks), the trip's length is kept, and dates start [LEAD_DAYS] from [today].
 * Null when there is no origin or nationality to send (the backend requires both). The server adds the Taste Graph.
 */
fun whereNextRequest(trip: TravelSearchRequest?, prefs: Preferences?, tripDays: Int?, today: LocalDate): DiscoveryRequest? {
    val origin = trip?.origin?.trim().orEmpty().ifEmpty { prefs?.currentResidence?.trim().orEmpty() }
    val nationality = trip?.nationality?.trim().orEmpty().ifEmpty { prefs?.nationality?.trim().orEmpty() }
    if (origin.isEmpty() || nationality.isEmpty()) return null
    val days = (tripDays ?: DEFAULT_TRIP_DAYS).coerceIn(MIN_TRIP_DAYS, MAX_TRIP_DAYS)
    val departure = today.plusDays(LEAD_DAYS)
    return DiscoveryRequest(
        origin = origin,
        departureDate = departure.toString(),
        nationality = nationality,
        returnDate = departure.plusDays(days.toLong()).toString(),
        interests = trip?.interests?.takeIf { it.isNotEmpty() } ?: prefs?.interests.orEmpty(),
        adults = (trip?.adults ?: prefs?.adults ?: 1).coerceAtLeast(1),
        children = trip?.children ?: prefs?.children ?: 0,
        seniors = trip?.seniors ?: prefs?.seniors ?: 0,
        infants = trip?.infants ?: prefs?.infants ?: 0,
    )
}

/**
 * Up to [WHERE_NEXT_MAX] suggestions without a place the traveller just visited ([visited]: the trip's destination text and
 * country names). Matching is case-insensitive on the city or the "City, Country" pair.
 */
fun whereNextPicks(destinations: List<DiscoveredDestination>, visited: Collection<String>): List<DiscoveredDestination> {
    val seen = visited.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    return destinations
        .filter { d -> !d.city.isNullOrBlank() && d.city!!.trim().lowercase() !in seen }
        .take(WHERE_NEXT_MAX)
}

/** "City, Country" as Search takes it. */
fun DiscoveredDestination.searchText(): String = listOfNotNull(city?.ifBlank { null }, country?.ifBlank { null }).joinToString(", ")
