package io.github.rupakc.travelplanner.feature.trips.hub

import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripsHubRulesTest {
    private fun trip(id: Long, phase: Phase?) = Trip(id = id, phase = phase)

    @Test
    fun `groups follow the served order and skip the pinned trip`() {
        val trips = listOf(trip(1, Phase.ON_THE_ROAD), trip(2, Phase.READY), trip(3, Phase.READY), trip(4, Phase.DREAMING), trip(5, Phase.MEMORIES))
        val groups = groupByPhase(trips, pinnedId = 2)
        assertEquals(listOf(Phase.ON_THE_ROAD, Phase.READY, Phase.DREAMING, Phase.MEMORIES), groups.map { it.phase })
        assertEquals(listOf(3L), groups[1].trips.map { it.id })
    }

    @Test
    fun `a trip with an unknown phase is shown as planning`() {
        assertEquals(listOf(Phase.PLANNING), groupByPhase(listOf(trip(1, null)), null).map { it.phase })
    }

    @Test
    fun `new trip validation`() {
        assertNull(validateNewTrip("Lisbon", "", ""))
        assertNull(validateNewTrip(" Lisbon ", "2027-04-01", "2027-04-08"))
        assertNull(validateNewTrip("Lisbon", "2027-04-01", ""))
        assertEquals(NewTripError.Title, validateNewTrip("   ", "", ""))
        assertEquals(NewTripError.Title, validateNewTrip("x".repeat(81), "", ""))
        assertEquals(NewTripError.Dates, validateNewTrip("T", "2027-04-08", "2027-04-01"))
        assertEquals(NewTripError.Dates, validateNewTrip("T", "04/01/2027", ""))
        assertEquals(NewTripError.Dates, validateNewTrip("T", "", "2027-04-01"))
        assertEquals(NewTripError.Dates, validateNewTrip("T", "2027-01-01", "2028-01-02"))
        assertNull(validateNewTrip("T", "2027-01-01", "2028-01-01"))
        assertEquals(NewTripError.Dates, validateNewTrip("T", "1999-12-31", ""))
        assertEquals(NewTripError.Dates, validateNewTrip("T", "2101-01-01", ""))
    }

    @Test
    fun `server error codes map to field errors`() {
        assertEquals(NewTripError.Dates, newTripErrorForCode("trip_dates_invalid"))
        assertEquals(NewTripError.Title, newTripErrorForCode("trip_title_invalid"))
        assertNull(newTripErrorForCode("other"))
        assertNull(newTripErrorForCode(null))
    }
}
