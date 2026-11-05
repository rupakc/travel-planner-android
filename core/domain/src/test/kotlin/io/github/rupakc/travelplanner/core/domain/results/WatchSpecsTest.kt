@file:Suppress("MaxLineLength") // fixtures ported verbatim from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.alerts.ActivityWatchSpec
import io.github.rupakc.travelplanner.core.model.alerts.HotelWatchSpec
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The cases of `hotelWatch.test.js`, `activityWatch.test.js` and `fareWatch.test.js` for the
 * builders the Results Watch buttons use (names: "file › describe - it"). The dialog/alerts
 * helpers in those files (hotelPreview, matchedElsewhere, perPerson, messageTrip, watchStatus,
 * sparkGeometry, …) belong to the alerts feature and are ported there.
 */
class WatchSpecsTest {
    // ── hotelWatch.test.js ──
    private val today = LocalDate.parse("2026-11-05")
    private val searchSd = search("2026-12-10", "2026-12-14", null, destination = "Lisbon, Portugal", adults = 2, numTravelers = 2)
    private val hotelJ = obj("name" to "Lumen Hotel", "price_per_night_usd" to 100, "total_price_usd" to 410)

    private fun hotel(vararg over: Pair<String, Any?>): Hotel = hotelsOf(obj("results" to listOf(hotelJ.with(*over)))).results.single()

    private fun guests(adults: Int = 0, children: Int = 0, seniors: Int = 0, travellers: Int = 0) =
        WatchSpecs.hotelGuests(search(adults = adults, children = children, seniors = seniors, numTravelers = travellers))

    private fun stay(h: Hotel, sd: io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest? = searchSd) = WatchSpecs.hotelStay(h, sd, today)

    @Test
    fun `hotelWatch - hotelGuests - counts every traveller as a guest (Google Hotels takes adults only), one room up to 4`() {
        assertEquals(2 to 1, guests(adults = 2))
        assertEquals(4 to 1, guests(adults = 2, children = 2))
        assertEquals(1 to 1, guests(seniors = 1))
        assertEquals(5 to 2, guests(travellers = 5))
        assertEquals(9 to 3, guests(adults = 9))
        assertEquals(1 to 1, guests())
        assertEquals(16 to 4, guests(adults = 30))
    }

    @Test
    fun `hotelWatch - hotelStay - uses the trip dates for a single-city trip`() {
        assertEquals(Triple("2026-12-10", "2026-12-14", "Lisbon, Portugal"), stay(hotel()))
        assertEquals("Lisbon", stay(hotel("city" to "Lisbon"))?.third)
    }

    @Test
    fun `hotelWatch - hotelStay - is null for a hotel in another city than the trip's`() {
        assertNull(stay(hotel("city" to "Tokyo")))
        assertNull(stay(hotel("city" to "lisboa")))
    }

    @Test
    fun `hotelWatch - hotelStay - is null without both dates, or once check-in is today or past`() {
        assertNull(stay(hotel(), searchSd.copy(returnDate = null)))
        assertNull(stay(hotel(), searchSd.copy(departureDate = "")))
        assertNull(stay(hotel(), searchSd.copy(departureDate = "2026-11-05")))
        assertNull(stay(hotel(), null))
    }

    @Test
    fun `hotelWatch - hotelStay - uses the city's own nights on a multi-city trip`() {
        val multi = searchSd.copy(destination = "Paris", destinations = listOf("Paris, France", "Rome, Italy", "Madrid"), departureDate = "2026-12-01", returnDate = "2026-12-10", destinationNights = listOf(2, null, null))
        assertEquals(Triple("2026-12-01", "2026-12-03", "Paris"), stay(hotel("city" to "Paris"), multi))
        assertEquals(Triple("2026-12-03", "2026-12-07", "rome"), stay(hotel("city" to "rome"), multi))
        assertEquals(Triple("2026-12-07", "2026-12-10", "Madrid, Spain"), stay(hotel("city" to "Madrid, Spain"), multi))
        // A hotel we can't place in a city, or a city not on the trip: no dates
        assertNull(stay(hotel(), multi))
        assertNull(stay(hotel("city" to "Berlin"), multi))
    }

    @Test
    fun `hotelWatch - hotelWatchSpec - builds the spec the hotel source matches, with the AI total as an estimate`() {
        assertEquals(HotelWatchSpec("Lumen Hotel", "Lisbon, Portugal", "2026-12-10", "2026-12-14", adults = 2, rooms = 1, estimateTotal = 410.0), WatchSpecs.hotelWatchSpec(hotel(), searchSd, today))
    }

    @Test
    fun `hotelWatch - hotelWatchSpec - estimates the total from the nightly price when there is none`() {
        assertEquals(360.0, WatchSpecs.hotelWatchSpec(hotel("name" to "X", "price_per_night_usd" to 90, "total_price_usd" to UNDEFINED), searchSd, today)!!.estimateTotal)
        assertNull(WatchSpecs.hotelWatchSpec(hotel("name" to "X", "price_per_night_usd" to UNDEFINED, "total_price_usd" to UNDEFINED), searchSd, today)!!.estimateTotal)
    }

    @Test
    fun `hotelWatch - hotelWatchSpec - scales the one-room AI estimate to the rooms watched`() {
        val spec = WatchSpecs.hotelWatchSpec(hotel(), searchSd.copy(adults = 6), today)!!
        assertEquals(Triple(6, 2, 820.0), Triple(spec.adults, spec.rooms, spec.estimateTotal))
    }

    @Test
    fun `hotelWatch - hotelWatchSpec - is null without a name or dates`() {
        assertNull(WatchSpecs.hotelWatchSpec(hotel("name" to " "), searchSd, today))
        assertNull(WatchSpecs.hotelWatchSpec(hotel(), searchSd.copy(returnDate = null), today))
    }

    // ── activityWatch.test.js ──
    private val now = LocalDate.of(2026, 11, 5)

    private fun travellers(travelers: Int = 0, adults: Int = 0, children: Int = 0, seniors: Int = 0) =
        WatchSpecs.travellersFrom(search(numTravelers = travelers, adults = adults, children = children, seniors = seniors))

    @Test
    fun `activityWatch - travellersFrom - uses num_travelers, else the traveller breakdown, clamped to 1-20`() {
        assertEquals(2, travellers(travelers = 2))
        assertEquals(4, travellers(adults = 2, children = 1, seniors = 1)) // infants never count
        assertEquals(1, travellers())
        assertEquals(1, WatchSpecs.travellersFrom(null))
        assertEquals(20, travellers(travelers = 40))
    }

    private val itinerary =
        itineraryOf(
            obj(
                "days" to
                    listOf(
                        mapOf("date" to "2026-12-10", "slots" to listOf(mapOf("activity" to "Arrive in Lisbon"))),
                        mapOf("date" to "2026-12-11", "slots" to listOf(mapOf("activity" to "Small group walking tour of Belém"), mapOf("activity" to "Fado night"))),
                    ),
            ),
        )

    @Test
    fun `activityWatch - activityDate - finds the day a named activity is scheduled on`() {
        assertEquals("2026-12-11", WatchSpecs.activityDate(itinerary, "Small Group Walking Tour of Belem"))
        assertEquals("2026-12-11", WatchSpecs.activityDate(itinerary, "Fado night"))
        assertNull(WatchSpecs.activityDate(itinerary, "Sintra day trip"))
        assertNull(WatchSpecs.activityDate(null, "x"))
    }

    private val tripSd = search(destination = "Lisbon, Portugal", numTravelers = 2)

    private fun act(vararg p: Pair<String, Any?>): Activity = activitiesOf(obj("results" to listOf(obj(*p)))).results.single()

    @Test
    fun `activityWatch - activitySpec - builds name, city, travellers, the itinerary date and the AI estimate`() {
        assertEquals(
            ActivityWatchSpec("Small group walking tour of Belém", "Lisbon", travellers = 2, date = "2026-12-11", estimatePrice = 30.0),
            WatchSpecs.activitySpec(act("name" to "Small group walking tour of Belém", "price_usd" to 30), tripSd, itinerary, now),
        )
    }

    @Test
    fun `activityWatch - activitySpec - prefers the activity's own city and leaves out unknown or past dates`() {
        assertEquals(ActivityWatchSpec("Tram 28", "Porto", travellers = 2), WatchSpecs.activitySpec(act("name" to "Tram 28", "city" to "Porto"), tripSd, itinerary, now))
        assertNull(WatchSpecs.activitySpec(act("name" to "Fado night"), tripSd, itinerary, LocalDate.of(2026, 12, 20))!!.date)
    }

    @Test
    fun `activityWatch - activitySpec - is null without a name or a city`() {
        assertNull(WatchSpecs.activitySpec(act("name" to ""), tripSd, itinerary, now))
        assertNull(WatchSpecs.activitySpec(act("name" to "Tour"), null, null, now))
    }

    @Test
    fun `activityWatch - fix round 1 - sends a date only when it is after tomorrow locally (the server counts in UTC)`() {
        fun day(date: String) = itineraryOf(obj("days" to listOf(mapOf("date" to date, "slots" to listOf(mapOf("activity" to "Fado night"))))))
        val sd = search(destination = "Lisbon")
        assertNull(WatchSpecs.activitySpec(act("name" to "Fado night"), sd, day("2026-11-06"), now)!!.date)
        assertEquals("2026-11-07", WatchSpecs.activitySpec(act("name" to "Fado night"), sd, day("2026-11-07"), now)!!.date)
    }

    // ── fareWatch.test.js ──
    private val fareNow = LocalDate.parse("2026-11-04")

    @Test
    fun `fareWatch - trips - builds a trip from search data`() {
        val sd = search("2026-12-01", "", null, destination = "Lisbon", numTravelers = 2).copy(origin = "BER")
        val trip = WatchSpecs.fareTrip(sd)
        assertEquals(listOf("BER", "Lisbon", "2026-12-01"), listOf(trip.origin, trip.destination, trip.departureDate))
        assertEquals(2, trip.numTravelers)
        assertNull(trip.destinations)
    }

    @Test
    fun `fareWatch - trips - watches only single-city trips departing after today`() {
        val t = search("2026-11-05", null, null, destination = "LIS").copy(origin = "BER")
        assertTrue(WatchSpecs.isWatchableTrip(t, fareNow))
        assertFalse(WatchSpecs.isWatchableTrip(t.copy(departureDate = "2026-11-04"), fareNow))
        assertFalse(WatchSpecs.isWatchableTrip(t.copy(destinations = listOf("Paris", "Rome")), fareNow))
        assertTrue(WatchSpecs.isWatchableTrip(t.copy(destinations = listOf("LIS")), fareNow))
        assertFalse(WatchSpecs.isWatchableTrip(t.copy(origin = ""), fareNow))
        assertFalse(WatchSpecs.isWatchableTrip(null, fareNow))
    }

    @Test
    fun `fareWatch - cheapestFlightPrice - is the lowest priced flight card, or null`() {
        assertEquals(480.0, WatchSpecs.cheapestFlightPrice(flightsOf("""{"results":[{"price_usd":530},{"price_usd":480},{"price_usd":0},{}]}""")))
        assertNull(WatchSpecs.cheapestFlightPrice(flightsOf("""{"results":[]}""")))
        assertNull(WatchSpecs.cheapestFlightPrice(null))
        assertNull(WatchSpecs.cheapestFlightPrice(flightsOf("""{"legs":[{}],"results":[{"price_usd":100}]}""")))
    }

    @Test
    fun `allocateDays - honours requested nights when they fit, else spreads evenly`() {
        assertEquals(listOf(3, 1), WatchSpecs.allocateDays(2, 4, listOf(3, null)))
        assertEquals(listOf(2, 2), WatchSpecs.allocateDays(2, 4, null))
        assertEquals(listOf(2, 4, 3), WatchSpecs.allocateDays(3, 9, listOf(2, null, null)))
    }
}
