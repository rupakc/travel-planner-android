package io.github.rupakc.travelplanner.core.domain.place

import io.github.rupakc.travelplanner.core.model.reference.Airport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceNamesTest {
    private val names = mapOf("LIS" to "Lisbon", "JFK" to "New York")

    @Test
    fun `an airport code the lookup knows shows the city`() {
        assertEquals("Lisbon", placeName("LIS", names))
        assertEquals("Lisbon", placeName(" LIS ", names))
    }

    @Test
    fun `an unknown code or typed text stays as it is`() {
        assertEquals("OPO", placeName("OPO", names))
        assertEquals("Lisbon, Portugal", placeName("Lisbon, Portugal", names))
        assertEquals("", placeName(null, names))
    }

    @Test
    fun `route joins the places with arrows`() {
        assertEquals("New York → Lisbon", placeRoute(listOf("JFK", "LIS"), names))
        assertEquals("New York → OPO", placeRoute(listOf("JFK", null, " ", "OPO"), names))
    }

    @Test
    fun `airport codes are the three-letter upper-case values only`() {
        assertEquals(listOf("LIS", "JFK"), airportCodesIn(listOf("LIS", "Lisbon", " JFK", null, "LIS", "lis", "LHR,LGW")))
    }

    @Test
    fun `the city comes from the row with that exact IATA code, shortened`() {
        val rows =
            listOf(
                Airport(iataCode = "LIA", city = "Liangping"),
                Airport(iataCode = "lis", city = "Lisbon, Portugal"),
            )
        assertEquals("Lisbon", cityForCode("LIS", rows))
        assertNull(cityForCode("OPO", rows))
        assertNull(cityForCode("LIS", listOf(Airport(iataCode = "LIS", city = " "))))
    }
}
