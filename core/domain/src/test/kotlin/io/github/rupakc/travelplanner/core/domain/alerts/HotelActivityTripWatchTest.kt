package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors hotelWatch.test.js, activityWatch.test.js and tripWatch.test.js. */
class HotelActivityTripWatchTest {
    private val today = LocalDate.parse("2026-11-05")
    private val search =
        TravelSearchRequest(
            origin = "BER",
            destination = "Lisbon, Portugal",
            departureDate = "2026-12-10",
            returnDate = "2026-12-14",
            nationality = "",
            numTravelers = 2,
            adults = 2,
        )
    private val hotel = Hotel(name = "Lumen Hotel", pricePerNightUsd = 100.0, totalPriceUsd = 410.0)

    private fun json(text: String) = TravelJson.parseToJsonElement(text) as JsonObject

    @Test
    fun `hotel guests count every traveller, one room up to four`() {
        assertEquals(HotelGuests(2, 1), hotelGuests(search.copy(adults = 2)))
        assertEquals(HotelGuests(4, 1), hotelGuests(search.copy(adults = 2, children = 2)))
        assertEquals(HotelGuests(5, 2), hotelGuests(search.copy(adults = 0, numTravelers = 5)))
        assertEquals(HotelGuests(9, 3), hotelGuests(search.copy(adults = 9)))
        assertEquals(HotelGuests(16, 4), hotelGuests(search.copy(adults = 30)))
        assertEquals(HotelGuests(1, 1), hotelGuests(null))
    }

    @Test
    fun `hotel stay single and multi city`() {
        assertEquals(HotelStay("2026-12-10", "2026-12-14", "Lisbon, Portugal"), hotelStay(hotel, search, today))
        assertNull(hotelStay(hotel.copy(city = "Tokyo"), search, today))
        assertNull(hotelStay(hotel.copy(city = "lisboa"), search, today))
        assertNull(hotelStay(hotel, search.copy(returnDate = null), today))
        assertNull(hotelStay(hotel, search.copy(departureDate = "2026-11-05"), today))
        val multi =
            search.copy(
                destination = "Paris",
                destinations = listOf("Paris, France", "Rome, Italy", "Madrid"),
                departureDate = "2026-12-01",
                returnDate = "2026-12-10",
                destinationNights = listOf(2, null, null),
            )
        assertEquals(HotelStay("2026-12-01", "2026-12-03", "Paris"), hotelStay(hotel.copy(city = "Paris"), multi, today))
        assertEquals(HotelStay("2026-12-03", "2026-12-07", "rome"), hotelStay(hotel.copy(city = "rome"), multi, today))
        assertEquals(HotelStay("2026-12-07", "2026-12-10", "Madrid, Spain"), hotelStay(hotel.copy(city = "Madrid, Spain"), multi, today))
        assertNull(hotelStay(hotel, multi, today))
        assertNull(hotelStay(hotel.copy(city = "Berlin"), multi, today))
    }

    @Test
    fun `hotel spec carries the scaled AI estimate`() {
        val spec = hotelWatchSpec(hotel, search, today)!!
        assertEquals("Lumen Hotel", spec.name)
        assertEquals(2, spec.adults)
        assertEquals(1, spec.rooms)
        assertEquals(410.0, spec.estimateTotal)
        assertEquals(360.0, hotelWatchSpec(Hotel(name = "X", pricePerNightUsd = 90.0), search, today)!!.estimateTotal)
        assertNull(hotelWatchSpec(Hotel(name = "X"), search, today)!!.estimateTotal)
        assertEquals(820.0, hotelWatchSpec(hotel, search.copy(adults = 6), today)!!.estimateTotal)
        assertNull(hotelWatchSpec(hotel.copy(name = " "), search, today))
    }

    @Test
    fun `matched elsewhere`() {
        assertTrue(matchedElsewhere(PriceWatch(id = 1, title = "Lumen Hotel & The Lisbon Light Show", subject = json("""{"requested_name":"Lumen Hotel"}"""))))
        assertFalse(matchedElsewhere(PriceWatch(id = 1, title = "Lumen Hotel", subject = json("""{"requested_name":"lumen  hotel"}"""))))
        assertFalse(matchedElsewhere(PriceWatch(id = 1, title = "Lumen Hotel")))
    }

    @Test
    fun `activity spec uses the itinerary date after tomorrow and the estimate`() {
        val itinerary =
            ItineraryData(
                days =
                    listOf(
                        ItineraryDay(date = "2026-11-06", slots = listOf(ItinerarySlot(activity = "Tram 28 ride"))),
                        ItineraryDay(date = "2026-12-11", slots = listOf(ItinerarySlot(activity = "Sunset sail on the Tagus river"))),
                    ),
            )
        val spec = activityWatchSpec(Activity(name = "Sunset sail", priceUsd = 45.0), search, itinerary, today)!!
        assertEquals("Lisbon", spec.city)
        assertEquals(2, spec.travellers)
        assertEquals("2026-12-11", spec.date)
        assertEquals(45.0, spec.estimatePrice)
        assertNull(activityWatchSpec(Activity(name = "Tram 28 ride"), search, itinerary, today)!!.date)
        assertNull(activityWatchSpec(Activity(name = "X"), search.copy(destinations = listOf("A", "B")), null, today))
        assertNull(activityWatchSpec(Activity(name = " "), search, null, today))
    }

    @Test
    fun `activity helpers`() {
        assertEquals("GetYourGuide", providerName("getyourguide"))
        assertEquals("Viator", providerName("viator"))
        assertNull(providerName(null))
        assertTrue(titleDiffers("Sunset sail", "Lisbon Sunset Sailing Tour"))
        assertFalse(titleDiffers("Sunset Sail", "sunset-sail"))
        assertEquals(3, travellersFrom(search.copy(numTravelers = 0, adults = 2, children = 1)))
        val w = PriceWatch(id = 1, lastPrice = 23.26, subject = json("""{"travellers":2}"""))
        assertEquals(11.63, perPerson(w)!!, 0.001)
        assertEquals(9.9, perPerson(w.copy(lastObservation = json("""{"unit_price":9.9}""")))!!, 0.0)
        assertEquals("EUR", watchCurrency(PriceWatch(id = 1, subject = json("""{"currency":"EUR"}"""))))
    }

    private val flightRow = """{"id":"flight","type":"flight","group":"flights","label":"Flights","live":true,"status":"ok","price":542,"delta":-70}"""
    private val hotelRow = """{"id":"hotel","type":"hotel","group":"stay","label":"Lumen Hotel","live":true,"status":"ok","price":356,"delta":-42}"""
    private val simRow = """{"id":"sim","type":"other","group":"other","label":"SIM / eSIM","live":false,"status":"estimate","price":13}"""

    @Test
    fun `trip rows, groups and badges`() {
        val w = PriceWatch(id = 1, lastObservation = json("""{"breakdown":[$flightRow]}"""), subject = json("""{"components":[$simRow]}"""))
        assertEquals(listOf("flight"), tripRows(w).map { it.id })
        assertEquals(listOf("sim"), tripRows(w.copy(lastObservation = null)).map { it.id })
        val eur = """{"id":"a","group":"activities","price":50,"currency":"EUR","in_total":false}"""
        val rows = tripRows(PriceWatch(id = 1, subject = json("""{"components":[$simRow,$hotelRow,$flightRow,$eur]}""")))
        assertEquals(listOf(TripGroup.FLIGHTS to 542.0, TripGroup.STAY to 356.0, TripGroup.OTHER to 13.0), groupTotals(rows).map { it.group to it.amount })
        assertEquals(1.0, groupTotals(rows).sumOf { it.share }, 1e-9)
        assertEquals("<1%", percent(0.001))
        assertEquals("43%", percent(0.426))
        assertEquals(RowBadge.LIVE, rowBadge(rows[2]))
        assertEquals(RowBadge.ESTIMATE, rowBadge(rows[0]))
        assertEquals(RowBadge.UNAVAILABLE, rowBadge(rows[1].copy(status = "unavailable")))
        assertEquals(RowBadge.NOT_CHECKED, rowBadge(rows[1].copy(status = "error")))
        assertEquals(RowBadge.LIVE_EARLIER, rowBadge(rows[1].copy(status = "stale")))
        assertEquals(2, liveCount(rows.take(3)))
        assertEquals(1, estimateCount(rows.take(3)))
    }

    @Test
    fun `what changed in the last check`() {
        val w = PriceWatch(id = 1, checkCount = 2, lastObservation = json("""{"breakdown":[$hotelRow,$flightRow,$simRow]}"""))
        assertEquals(TripChange(listOf(GroupMove(TripGroup.FLIGHTS, -70.0), GroupMove(TripGroup.STAY, -42.0)), emptyList()), whatChanged(w))
        val gone = hotelRow.replace("\"ok\"", "\"unavailable\"").replace("-42", "0")
        val flat = flightRow.replace("-70", "0")
        assertEquals(TripChange(emptyList(), listOf("Lumen Hotel")), whatChanged(w.copy(lastObservation = json("""{"breakdown":[$flat,$gone]}"""))))
        assertNull(whatChanged(w.copy(checkCount = 1)))
        assertNull(whatChanged(PriceWatch(id = 1)))
    }

    @Test
    fun `plans, replan and preview parts`() {
        val plan = Plan(id = 3, searchData = json("""{"origin":"BER","destination":"LIS","departure_date":"2026-12-10","nationality":""}"""))
        assertTrue(isWatchablePlan(plan, today))
        assertFalse(
            isWatchablePlan(plan.copy(searchData = json("""{"origin":"BER","destination":"LIS","departure_date":"2026-11-05","nationality":""}""")), today),
        )
        val fare = """{"type":"flight","live":true,"subject":{"origin_label":"Berlin","destination_label":"Lisbon","departure_date":"2026-12-10"}}"""
        assertEquals("Berlin", tripReplanRequest(PriceWatch(id = 1, subject = json("""{"components":[$fare]}""")), null)!!.origin)
        assertNull(tripReplanRequest(PriceWatch(id = 1, subject = json("""{"components":[$simRow]}""")), null))
        val selections =
            PlanSelections(
                flight = Flight(priceUsd = 612.0),
                hotel = Hotel(totalPriceUsd = 398.0),
                activities = listOf(Activity(priceUsd = 30.0), Activity(priceUsd = 20.0)),
                sim = SimPlan(priceUsd = 13.0),
            )
        val parts = tripPreviewParts(selections, search.copy(departureDate = "2099-12-10", returnDate = "2099-12-14"))
        assertEquals(listOf(612.0, 398.0, 100.0, 13.0), parts.map { it.amount })
        assertEquals(TripPart.Note.CHEAPEST_FARE, parts[0].note)
        assertEquals(TripPart.Note.PER_PERSON, parts[2].note)
        assertEquals(2, parts[2].travellers)
        assertTrue(tripPreviewParts(PlanSelections(activities = listOf(Activity(name = "Free walk"))), search).isEmpty())
    }

    @Test
    fun `total difference explains the gap`() {
        val rows = """[{"id":"flight","type":"flight","live":true,"price":612},{"id":"activity-0","type":"activity","live":true,"price":60}]"""
        val w = PriceWatch(id = 1, baselinePrice = 685.0, subject = json("""{"travellers":2,"components":$rows}"""))
        assertEquals(TotalDifference(685.0, 640.0, 45.0, listOf(DiffReason.CHEAPEST_FARE, DiffReason.ACTIVITIES_PER_PERSON), 2), totalDifference(w, 640.0))
        assertNull(totalDifference(w, 685.4))
        assertNull(totalDifference(w, null))
    }
}
