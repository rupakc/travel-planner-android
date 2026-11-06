package io.github.rupakc.travelplanner.core.data.place

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SearchRepository
import io.github.rupakc.travelplanner.core.domain.place.airportCodesIn
import io.github.rupakc.travelplanner.core.domain.place.cityForCode
import io.github.rupakc.travelplanner.core.domain.place.placeName
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

/**
 * The one airport-code → city lookup for every screen (web `useAirportCities`): the search form stores "LIS",
 * screens show "Lisbon". Codes are looked up in the backend's local airport data (`GET /api/airports/search`,
 * no paid calls) and the cities found are kept for the process. A code the lookup cannot name stays a code
 * (display with [io.github.rupakc.travelplanner.core.domain.place.placeName]); a miss is asked again next time.
 */
@Singleton
class PlaceNameLookup
    @Inject
    constructor(
        private val search: SearchRepository,
    ) {
        private val known = MutableStateFlow<Map<String, String>>(emptyMap())

        /** Every code → city found so far. */
        val names: StateFlow<Map<String, String>> = known.asStateFlow()

        /** Looks up the airport codes among [places] not known yet; returns code → city for the codes in [places] that have one. */
        suspend fun resolve(places: Iterable<String?>): Map<String, String> {
            val codes = airportCodesIn(places)
            codes.filterNot { it in known.value }.forEach { code ->
                val rows = (search.airports(code, LOOKUP_LIMIT) as? AppResult.Success)?.value.orEmpty()
                cityForCode(code, rows)?.let { city -> known.update { it + (code to city) } }
            }
            return known.value.filterKeys { it in codes }
        }

        /** [place] as shown: its city when it is a known airport code, else [place] itself. */
        suspend fun displayName(place: String): String = placeName(place, resolve(listOf(place)))

        /**
         * Code → city for the codes among [places]: the names already known at once, then (when the lookup finds
         * more) the fuller map. Completes after the lookup, so a screen can `flatMapLatest` over its data.
         */
        fun namesFor(places: Iterable<String?>): Flow<Map<String, String>> =
            flow {
                val codes = airportCodesIn(places)
                val first = known.value.filterKeys { it in codes }
                emit(first)
                if (codes.any { it !in first }) {
                    val found = resolve(codes)
                    if (found != first) emit(found)
                }
            }

        private companion object {
            const val LOOKUP_LIMIT = 5
        }
    }
