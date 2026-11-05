package io.github.rupakc.travelplanner.core.domain.chat

import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatItineraryDay
import io.github.rupakc.travelplanner.core.model.chat.ChatSlot
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatItineraryPlanTest {
    private val itinerary =
        ChatItinerary(
            days =
                listOf(
                    ChatItineraryDay(
                        dayNumber = 1,
                        date = "2026-04-01",
                        city = "Tokyo",
                        morning = ChatSlot(activity = "Senso-ji", place = "Asakusa", costUsd = 0.0),
                        evening = ChatSlot(activity = "Ramen"),
                    ),
                    ChatItineraryDay(dayNumber = 2, afternoon = ChatSlot(activity = " ")),
                ),
        )

    @Test
    fun `slots copy only filled itinerary values in the web shape`() {
        val slots = chatItinerarySlots(itinerary)
        assertEquals(listOf("1-morning", "1-evening"), slots.map { it.key })
        val first = slots.first()
        assertEquals("Asakusa", first.location)
        assertEquals(0.0, first.estimatedCostUsd)
        assertEquals("2026-04-01", first.date)
        assertNull(slots[1].estimatedCostUsd)
        assertEquals("", slots[1].location)
    }

    @Test
    fun `snapshot uses the search itinerary schema`() {
        val snapshot = chatItinerarySnapshot(itinerary)
        val days = snapshot["days"] as JsonArray
        assertEquals(2, days.size)
        val day1 = days[0] as JsonObject
        assertEquals("Tokyo", day1["city"]?.jsonPrimitive?.content)
        val slots = day1["slots"] as JsonArray
        assertEquals("morning", (slots[0] as JsonObject)["time_of_day"]?.jsonPrimitive?.content)
        assertNull((slots[0] as JsonObject)["key"])
        assertEquals(0, ((days[1] as JsonObject)["slots"] as JsonArray).size)
    }

    @Test
    fun `search request from route and session context`() {
        val ctx = TravelJson.parseToJsonElement("""{"nationality":"American","budget_usd":2000,"interests":["food"]}""") as JsonObject
        val request = chatTripSearchRequest(ctx, TripRoute("New York", listOf("Paris", "Rome"), "2026-09-01", "2026-09-10", 2))!!
        assertEquals("Rome", request.destination)
        assertEquals(listOf("Paris", "Rome"), request.destinations)
        assertEquals("American", request.nationality)
        assertEquals(2000.0, request.budgetUsd)
        assertEquals(2, request.numTravelers)
        assertEquals(listOf("food"), request.interests)
    }

    @Test
    fun `no search request without origin, destination and date`() {
        assertNull(chatTripSearchRequest(JsonObject(emptyMap()), TripRoute(cities = listOf("Paris"))))
        val ctx = TravelJson.parseToJsonElement("""{"origin":"NYC","destination":"Lisbon","departure_date":"2026-05-01"}""") as JsonObject
        val fromContext = chatTripSearchRequest(ctx, null)!!
        assertEquals("Lisbon", fromContext.destination)
        assertNull(fromContext.destinations)
        assertEquals("", fromContext.nationality)
    }
}
