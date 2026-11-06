package io.github.rupakc.travelplanner.core.data

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** One airport-code → city lookup for every screen (web `useAirportCities`). */
class PlaceNameLookupTest {
    private val search =
        FakeSearchRepository().apply {
            airports = listOf(Airport(iataCode = "LIS", city = "Lisbon"), Airport(iataCode = "JFK", city = "New York, United States"))
        }
    private val lookup = PlaceNameLookup(search)

    @Test
    fun `codes resolve to cities and unknown codes or typed names are left out`() =
        runTest {
            val names = lookup.resolve(listOf("LIS", "JFK", "OPO", "Lisbon", null))
            assertEquals(mapOf("LIS" to "Lisbon", "JFK" to "New York"), names)
            assertEquals("Lisbon", lookup.displayName("LIS"))
            assertEquals("OPO", lookup.displayName("OPO"))
            assertEquals("Porto, Portugal", lookup.displayName("Porto, Portugal"))
        }

    @Test
    fun `a found city is cached, a miss is asked again`() =
        runTest {
            lookup.resolve(listOf("LIS", "OPO"))
            lookup.resolve(listOf("LIS", "OPO"))
            assertEquals(listOf("LIS", "OPO", "OPO"), search.airportQueries)
            assertEquals(mapOf("LIS" to "Lisbon"), lookup.names.value)
        }

    @Test
    fun `namesFor emits the known names at once, then the looked-up ones`() =
        runTest {
            lookup.resolve(listOf("LIS"))
            lookup.namesFor(listOf("LIS", "JFK")).test {
                assertEquals(mapOf("LIS" to "Lisbon"), awaitItem())
                assertEquals(mapOf("LIS" to "Lisbon", "JFK" to "New York"), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `namesFor emits once when nothing new is found`() =
        runTest {
            lookup.namesFor(listOf("OPO", "Lisbon")).test {
                assertEquals(emptyMap<String, String>(), awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a failed lookup keeps the code`() =
        runTest {
            search.airportsFailure = io.github.rupakc.travelplanner.core.common.AppError.Network()
            assertEquals(emptyMap<String, String>(), lookup.resolve(listOf("LIS")))
            assertEquals("LIS", lookup.displayName("LIS"))
        }
}
