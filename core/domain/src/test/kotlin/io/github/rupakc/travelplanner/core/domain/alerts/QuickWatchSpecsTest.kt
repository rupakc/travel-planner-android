package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import java.time.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickWatchSpecsTest {
    private val today = LocalDate.parse("2026-11-13")
    private val trip =
        TravelSearchRequest(
            origin = "JFK",
            destination = "LIS",
            departureDate = "2027-03-01",
            returnDate = "2027-03-08",
            nationality = "American",
        )

    @Test fun `a flight card watches the trip's route and needs a future single-city trip`() {
        val flight = PlanItem.FlightItem(io.github.rupakc.travelplanner.core.model.section.Flight())
        val draft = QuickWatchSpecs.forItem(flight, trip, today)!!
        assertEquals("flight", draft.kind)
        assertEquals("JFK → LIS", draft.title)
        assertEquals(JsonPrimitive("LIS"), draft.spec["destination"])
        assertNull(QuickWatchSpecs.forItem(flight, trip.copy(departureDate = "2026-01-01"), today))
        assertNull(QuickWatchSpecs.forItem(flight, null, today))
    }

    @Test fun `a hotel card needs a name and a trip, an item without them is not watchable`() {
        assertNull(QuickWatchSpecs.forItem(PlanItem.HotelItem(Hotel(name = "Lumen")), null, today))
        assertNull(QuickWatchSpecs.forItem(PlanItem.ActivityItem(Activity()), trip, today))
    }

    @Test fun `the whole trip is kind trip with the plan id`() {
        val draft = QuickWatchSpecs.forTrip(9, "Lisbon")
        assertEquals("trip", draft.kind)
        assertNotNull(draft.spec["plan_id"])
        assertEquals(JsonPrimitive(9), draft.spec["plan_id"])
    }
}
