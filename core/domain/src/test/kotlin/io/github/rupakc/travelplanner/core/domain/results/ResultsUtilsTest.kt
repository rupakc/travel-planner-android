@file:Suppress("MaxLineLength") // inline JSON fixtures ported from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.search.FareCalendarDate
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from the web's remix, fareCalendar, verification and orderSectionByCity tests, plus watch specs. */
class ResultsUtilsTest {
    private val base = search(departure = "2026-08-10", returnDate = "2026-08-17", budget = 3000.0)

    @Test
    fun `remix presets`() {
        assertEquals("2026-08-17" to "2026-08-24", Remix.apply(base, RemixPreset.WEEK_LATER)!!.let { it.departureDate to it.returnDate })
        val month = Remix.apply(base.copy(departureDate = "2026-08-28", returnDate = "2026-08-30"), RemixPreset.WEEK_LATER)!!
        assertEquals("2026-09-04" to "2026-09-06", month.departureDate to month.returnDate)
        assertNull(Remix.apply(base.copy(returnDate = null), RemixPreset.WEEK_LATER)!!.returnDate)
        assertEquals(1667.0, Remix.apply(base.copy(budgetUsd = 3333.0), RemixPreset.HALF_BUDGET)!!.budgetUsd)
        assertNull(Remix.apply(base.copy(budgetUsd = null), RemixPreset.HALF_BUDGET))
        assertEquals(6000.0, Remix.apply(base, RemixPreset.LUXE)!!.budgetUsd)
        val slow = Remix.apply(base, RemixPreset.SLOWER_PACE)!!
        assertEquals("relaxed", slow.pace)
        assertEquals(base.departureDate, slow.departureDate)
        assertEquals("2026-08-13", Remix.apply(base, RemixPreset.LONG_WEEKEND)!!.returnDate)
    }

    @Test
    fun `remix metrics and diff`() {
        val before =
            Remix.snapshot(
                TripResults(
                    flights = flightsOf("""{"results":[{"price_usd":500},{"price_usd":"x"},{"price_usd":450}]}"""),
                    hotels = hotelsOf("""{"results":[{"price_per_night_usd":120}]}"""),
                    weather = SectionDecoder.decode(SectionId.WEATHER, obj("""{"days":[{"is_poor":true},{"is_poor":false}]}""")) as WeatherData,
                    itinerary = SectionDecoder.decode(SectionId.ITINERARY, obj("""{"total_estimated_cost_usd":0}""")) as ItineraryData,
                ),
            )
        assertEquals(RemixMetrics(450.0, 120.0, null, 1.0), before)
        val rows = Remix.diff(before, RemixMetrics(400.0, 150.0, 900.0, 1.0))
        assertEquals(listOf(RemixMetric.MIN_FLIGHT, RemixMetric.MIN_HOTEL_NIGHTLY, RemixMetric.POOR_WEATHER_DAYS), rows.map { it.metric })
        assertEquals(listOf(true, false, null), rows.map { it.improved })
        assertEquals(-50.0, rows[0].delta, 0.0)
    }

    private fun day(dep: String, price: Double?, selected: Boolean = false) = FareCalendarDate(dep, null, price, selected)

    @Test
    fun `fare calendar helpers`() {
        assertEquals("2026-11-04" to "2026-11-11", FareCalendarRules.shiftDates("2026-11-01", "2026-11-08", 3))
        assertEquals("2026-10-29" to null, FareCalendarRules.shiftDates("2026-11-01", null, -3))
        assertEquals(-3L, FareCalendarRules.dayOffset("2026-11-04", "2026-11-01"))
        val dates = listOf(day("a", 300.0), day("b", null), day("c", 250.0), day("d", 250.0, selected = true))
        assertEquals("c", FareCalendarRules.cheapestDeparture(dates))
        assertNull(FareCalendarRules.cheapestDeparture(listOf(day("a", null))))
        assertEquals(50.0, FareCalendarRules.fareDelta(dates[0], dates[3]))
        assertNull(FareCalendarRules.fareDelta(dates[1], dates[3]))
        assertEquals(0L, FareCalendarRules.roundedDelta(0.4))
        assertEquals(-45L, FareCalendarRules.roundedDelta(-45.2))
        assertEquals(5L, FareCalendarRules.daysUntil("2026-11-10", LocalDate.parse("2026-11-05")))
        assertEquals(0L, FareCalendarRules.daysUntil("2026-11-01", LocalDate.parse("2026-11-05")))
        assertEquals(0L, FareCalendarRules.daysUntil("garbage", LocalDate.parse("2026-11-05")))
    }

    @Test
    fun `price insights chip`() {
        assertEquals(
            PriceInsight(PriceLevel.LOW, 300.0, 500.0),
            FareCalendarRules.priceInsights(obj("""{"price_level":"low","typical_price_range":[300,500]}""")),
        )
        assertEquals(PriceInsight(PriceLevel.HIGH, null, null), FareCalendarRules.priceInsights(obj("""{"price_level":"high","typical_price_range":[300]}""")))
        assertNull(FareCalendarRules.priceInsights(obj("""{"price_level":"weird"}""")))
        assertNull(FareCalendarRules.priceInsights(null))
    }

    @Test
    fun `clearPendingVerification resolves pending chips and keeps identity otherwise`() {
        val results =
            mapOf(
                "hotels" to obj("""{"results":[{"name":"A"}],"verification":{"level":"unverified","summary":"Checking…","pending":true}}"""),
                "flights" to obj("""{"results":[],"verification":{"level":"verified","pending":false}}"""),
            )
        val out = clearPendingVerification(results)
        val v = out.getValue("hotels")["verification"]!!.jsonObject
        assertEquals("false", v["pending"]!!.jsonPrimitive.content)
        assertEquals(GAVE_UP_SUMMARY, v["summary"]!!.jsonPrimitive.content)
        assertSame(results["flights"], out["flights"])
        assertSame(results["hotels"]!!["results"], out.getValue("hotels")["results"])
        val none = mapOf("flights" to obj("""{"verification":{"pending":false}}"""))
        assertSame(none, clearPendingVerification(none))
    }

    @Test
    fun `stripLinkEvidence drops link_verified and link_checked from saved items`() {
        val sel =
            obj(
                """{"hotel":{"name":"H","link_verified":false},"activities":[{"name":"Tour","link_verified":true,"link_checked":true,"booking_url":"https://x"},{"name":"Plain"}],"itinerary_notes":{"a":"note"},"flight":null}""",
            )
        val out = stripLinkEvidence(sel)
        assertEquals(obj("""{"name":"Tour","booking_url":"https://x"}"""), out["activities"]!!.jsonArray[0])
        assertSame(sel["activities"]!!.jsonArray[1], out["activities"]!!.jsonArray[1])
        assertEquals(obj("""{"name":"H"}"""), out["hotel"])
        assertSame(sel["itinerary_notes"], out["itinerary_notes"])
        val typed = stripLinkEvidence(selectionsOf(sel.toString()))
        assertFalse(typed.activities[0].raw.containsKey("link_verified"))
    }

    private fun names(o: JsonObject, field: String) = (o[field] as JsonArray).map { (it as JsonObject)["name"]?.jsonPrimitive?.content }

    @Test
    fun `orderSectionByCity sorts city-tagged lists into journey order`() {
        val dests = listOf("Amsterdam", "New York", "San Francisco")
        val out =
            orderSectionByCity(
                obj("""{"results":[{"name":"c","city":"San Francisco"},{"name":"a","city":"Amsterdam"},{"name":"b","city":"New York, USA"}]}"""),
                dests,
            )
        assertEquals(listOf("a", "b", "c"), names(out, "results"))
        val stable =
            orderSectionByCity(obj("""{"hotels":[{"name":"x","city":"Atlantis"},{"name":"h1","city":"Amsterdam"},{"name":"h2","city":"Amsterdam"}]}"""), dests)
        assertEquals(listOf("h1", "h2", "x"), names(stable, "hotels"))
        val arrays = orderSectionByCity(obj("""{"places":[{"name":"p2","city":["New York","NYC"]},{"name":"p3"},{"name":"p1","city":"amsterdam"}]}"""), dests)
        assertEquals(listOf("p1", "p2", "p3"), names(arrays, "places"))
        val single = obj("""{"results":[{"city":"Rome"}]}""")
        assertSame(single, orderSectionByCity(single, listOf("Rome")))
    }

    @Test
    fun `section arrangement puts visible features first and never drops sections`() {
        val all = SectionArrangement.arrange(null)
        assertEquals(SectionId.entries, all.map { it.id })
        assertTrue(all.all { it.relevant })
        val visible = setOf(FeatureId.VISA_CHECKER, FeatureId.PACKING_LIST)
        val arranged = SectionArrangement.arrange(visible)
        assertEquals(SectionId.entries.size, arranged.size)
        assertEquals(SectionId.FLIGHTS, arranged.first().id)
        val firstHidden = arranged.indexOfFirst { !it.relevant }
        assertTrue(arranged.take(firstHidden).all { it.relevant })
        assertTrue(arranged.first { it.id == SectionId.EVENTS }.let { !it.relevant })
        assertTrue(arranged.first { it.id == SectionId.VISA }.relevant)
    }

    @Test
    fun `text filter narrows list sections and matches others as a whole`() {
        val raw = obj("""{"results":[{"name":"Café Lisboa","city":"Lisbon"},{"name":"Tower","city":"Belém"}]}""")
        val hotels = hotelsOf(raw.toString())
        val (filtered, count) = ResultsTextFilter.filter(hotels, raw, "cafe")
        assertEquals(1, count)
        assertEquals(listOf("Café Lisboa"), (filtered as io.github.rupakc.travelplanner.core.model.section.HotelsData).results.map { it.name })
        assertEquals(1, ResultsTextFilter.filter(hotels, raw, "BELEM").second)
        val visaRaw = obj("""{"requirement":{"visa_type":"visa-free","notes":"90 days"}}""")
        val visa = SectionDecoder.decode(SectionId.VISA, visaRaw)
        assertEquals(1, ResultsTextFilter.filter(visa, visaRaw, "visa-free").second)
        assertEquals(0, ResultsTextFilter.filter(visa, visaRaw, "tokyo").second)
        assertTrue(ResultsTextFilter.matches(JsonPrimitive("x"), " "))
    }

    @Test
    fun `fare watch trip rules`() {
        val today = LocalDate.parse("2026-11-05")
        val trip = search(departure = "2026-12-01", returnDate = "2026-12-08")
        assertTrue(WatchSpecs.isWatchableTrip(trip, today))
        assertFalse(WatchSpecs.isWatchableTrip(trip.copy(departureDate = "2026-11-05"), today))
        assertFalse(WatchSpecs.isWatchableTrip(trip.copy(destinations = listOf("Paris", "Rome")), today))
        assertEquals(450.0, WatchSpecs.cheapestFlightPrice(flightsOf("""{"results":[{"price_usd":500},{"price_usd":450},{"price_usd":0}]}""")))
        assertNull(WatchSpecs.cheapestFlightPrice(flightsOf("""{"legs":[{"results":[{"price_usd":5}]}]}""")))
        val req = WatchSpecs.fareTrip(trip)
        assertEquals("NYC" to "Paris", req.origin to req.destination)
        assertTrue(WatchSpecs.isAirportCode("LIS"))
        assertFalse(WatchSpecs.isAirportCode("Lisbon"))
    }

    @Test
    fun `hotel watch spec`() {
        val today = LocalDate.parse("2026-11-05")
        val sd = search(departure = "2026-12-10", returnDate = "2026-12-14").copy(destination = "Lisbon", adults = 2, numTravelers = 2)
        val hotel = hotelsOf("""{"results":[{"name":" Hotel Lisboa ","price_per_night_usd":100,"city":"Lisbon, Portugal"}]}""").results[0]
        val spec = WatchSpecs.hotelWatchSpec(hotel, sd, today)!!
        assertEquals("Hotel Lisboa", spec.name)
        assertEquals("2026-12-10" to "2026-12-14", spec.checkIn to spec.checkOut)
        assertEquals(2 to 1, spec.adults to spec.rooms)
        assertEquals(400.0, spec.estimateTotal)
        assertNull(WatchSpecs.hotelWatchSpec(hotel, sd.copy(destination = "Porto"), today))
        assertNull(WatchSpecs.hotelWatchSpec(hotel, sd.copy(returnDate = null), today))
        assertNull(WatchSpecs.hotelWatchSpec(hotel, sd, LocalDate.parse("2026-12-10")))
        assertEquals(6 to 2, WatchSpecs.hotelGuests(sd.copy(adults = 6)))
        // Multi-city: Lisbon 2 nights then Porto 2 nights
        val multi = sd.copy(destinations = listOf("Lisbon", "Porto"), destination = "Porto", destinationNights = listOf(null, null))
        val porto = hotel.copy(city = "Porto")
        assertEquals("2026-12-12" to "2026-12-14", WatchSpecs.hotelWatchSpec(porto, multi, today)!!.let { it.checkIn to it.checkOut })
        assertEquals(listOf(3, 1), WatchSpecs.allocateDays(2, 4, listOf(3, null)))
        assertEquals(listOf(2, 2), WatchSpecs.allocateDays(2, 4, null))
    }

    @Test
    fun `activity watch spec`() {
        val today = LocalDate.parse("2026-11-05")
        val sd = search(departure = "2026-12-10", returnDate = "2026-12-14").copy(destination = "Lisbon, Portugal", numTravelers = 3)
        val itinerary =
            SectionDecoder.decode(
                SectionId.ITINERARY,
                obj("""{"days":[{"date":"2026-12-11","slots":[{"activity":"Morning: Tram 28 tour of Alfama"}]}]}"""),
            ) as ItineraryData
        val act = activitiesOf("""{"results":[{"name":"Tram 28 tour","price_usd":25}]}""").results[0]
        val spec = WatchSpecs.activitySpec(act, sd, itinerary, today)!!
        assertEquals("Lisbon", spec.city)
        assertEquals(3, spec.travellers)
        assertEquals("2026-12-11", spec.date)
        assertEquals(25.0, spec.estimatePrice)
        assertNull(WatchSpecs.activitySpec(act, sd.copy(destinations = listOf("Lisbon", "Porto")), itinerary, today))
        assertNull(WatchSpecs.activitySpec(act, sd, itinerary, LocalDate.parse("2026-12-10"))!!.date)
        assertEquals(20, WatchSpecs.travellersFrom(sd.copy(numTravelers = 0, adults = 30)))
    }
}
