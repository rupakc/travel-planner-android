package io.github.rupakc.travelplanner.core.domain.export

import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import io.github.rupakc.travelplanner.core.model.section.Hotel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanShareTextTest {
    private val labels =
        PlanTextLabels(
            flights = "Flights",
            hotel = "Hotel",
            activities = "Activities",
            places = "Places",
            walkingTours = "Free walking tours",
            events = "Events",
            sim = "SIM",
            gettingAround = "Getting around",
            tips = "Tips",
            itinerary = "Itinerary",
            day = { "Day $it" },
            estimatedCost = { "Estimated cost: $it" },
            perNight = { "$it / night" },
            estimate = "Estimate",
        )

    @Test
    fun `lists selections with their own prices and only web links`() {
        val selections =
            PlanSelections(
                flight =
                    Flight(
                        priceUsd = 900.0,
                        source = "estimate",
                        bookingUrl = "https://flights.example/x",
                        outbound = FlightLeg(airline = "ANA", flightNumber = "NH9", origin = "JFK", destination = "HND", departureDate = "2027-04-01"),
                    ),
                hotel = Hotel(name = "Park Hyatt", pricePerNightUsd = 450.0, bookingUrl = "javascript:alert(1)"),
                activities = listOf(Activity(name = "Sushi class", priceUsd = null)),
            )
        val text = PlanShareText.build("Tokyo Trip", listOf("JFK → HND"), selections, emptyList(), 2250.0, labels)
        assertTrue(text.startsWith("Tokyo Trip\nJFK → HND\nEstimated cost: $2,250"))
        assertTrue(text.contains("ANA NH9 · JFK → HND · 2027-04-01 · $900 (Estimate)"))
        assertTrue(text.contains("https://flights.example/x"))
        assertTrue(text.contains("Park Hyatt, $450 / night"))
        assertFalse(text.contains("javascript"))
        // No price invented for an activity without one
        assertTrue(text.contains("  • Sushi class\n") || text.endsWith("  • Sushi class"))
    }

    @Test
    fun `adds the itinerary with edits and notes`() {
        val days =
            PlanIcs.planItineraryDays(
                null,
                Json.parseToJsonElement(
                    """{"days":[{"day_number":1,"date":"2027-04-01","slots":[{"time_of_day":"morning","activity":"Market","duration_hours":2}]}]}""",
                ).jsonObject,
            )
        val sel = PlanSelections(itineraryNotes = mapOf("1-morning" to "Go early"))
        val text = PlanShareText.build("T", emptyList(), sel, days, null, labels)
        assertTrue(text.contains("Itinerary\nDay 1, 2027-04-01\n  • 09:00–11:00 · Market\n    Go early"))
    }

    @Test
    fun `empty plan is just its name`() {
        assertEquals("T", PlanShareText.build("T", emptyList(), PlanSelections(), emptyList(), 0.0, labels))
    }
}
