package io.github.rupakc.travelplanner.core.domain.place

import io.github.rupakc.travelplanner.core.domain.results.WatchSpecs
import io.github.rupakc.travelplanner.core.domain.search.shortCity
import io.github.rupakc.travelplanner.core.model.reference.Airport

// The search form stores airport codes ("LIS"); screens show the city ("Lisbon"), looked up like the web's
// `useAirportCities`. These pure helpers turn a code → city map (from `PlaceNameLookup` in core:data) into display
// text. A code the lookup does not know stays as it is: nothing is invented.

/** [place]'s display name: the city [names] holds for an airport code ("LIS" → "Lisbon"), else [place] unchanged. */
fun placeName(place: String?, names: Map<String, String>): String {
    val text = place.orEmpty()
    return names[text.trim()] ?: text
}

/** "New York → Lisbon": the non-blank [places] by [placeName], joined with arrows. */
fun placeRoute(places: List<String?>, names: Map<String, String>): String =
    places.filterNot { it.isNullOrBlank() }.joinToString(" → ") { placeName(it, names) }

/** The distinct airport codes ("LIS") among [places], trimmed; city names and other text are skipped. */
fun airportCodesIn(places: Iterable<String?>): List<String> = places.filter(WatchSpecs::isAirportCode).map { it.orEmpty().trim() }.distinct()

/** The city of the airport search row whose IATA code is [code] ("Lisbon, Portugal" → "Lisbon"), or null. */
fun cityForCode(code: String, rows: List<Airport>): String? =
    rows.firstOrNull { it.iataCode.equals(code, ignoreCase = true) }?.city?.let(::shortCity)?.takeIf(String::isNotEmpty)
