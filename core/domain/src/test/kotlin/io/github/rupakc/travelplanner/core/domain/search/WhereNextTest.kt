package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhereNextTest {
    private val today = LocalDate.parse("2027-04-10")
    private val trip =
        TravelSearchRequest(
            origin = "JFK",
            destination = "Lisbon, Portugal",
            departureDate = "2027-03-01",
            nationality = "American",
            interests = listOf("food"),
        )

    @Test fun `the request takes the trip's origin, nationality and interests with dates ahead and the trip's length`() {
        val r = whereNextRequest(trip, Preferences(nationality = "French"), 10, today)!!
        assertEquals("JFK", r.origin)
        assertEquals("American", r.nationality)
        assertEquals(listOf("food"), r.interests)
        assertEquals("2027-05-25", r.departureDate)
        assertEquals("2027-06-04", r.returnDate)
    }

    @Test fun `preferences fill what the trip lacks, and no origin or nationality means no request`() {
        val bare = trip.copy(origin = "", nationality = "", interests = emptyList())
        val r = whereNextRequest(bare, Preferences(nationality = "French", currentResidence = "Paris", interests = listOf("art")), null, today)!!
        assertEquals("Paris", r.origin)
        assertEquals("French", r.nationality)
        assertEquals(listOf("art"), r.interests)
        assertEquals("2027-06-01", r.returnDate)
        assertNull(whereNextRequest(bare, Preferences(), null, today))
        assertNull(whereNextRequest(null, null, null, today))
    }

    @Test fun `picks drop the places just visited and stop at five`() {
        val all = listOf("Lisbon", "Porto", "Rome", "Oslo", "Kyoto", "Lima", "Cusco").map { DiscoveredDestination(city = it, country = "X") }
        assertEquals(listOf("Porto", "Rome", "Oslo", "Kyoto", "Lima"), whereNextPicks(all, listOf("Lisbon, Portugal")).map { it.city })
        assertEquals("Porto, Portugal", DiscoveredDestination(city = "Porto", country = "Portugal").searchText())
    }
}
