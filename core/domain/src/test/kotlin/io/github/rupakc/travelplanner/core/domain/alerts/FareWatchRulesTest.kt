package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.FareWatchHistory
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLegGroup
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors frontend/src/utils/fareWatch.test.js. */
class FareWatchRulesTest {
    private val now = Instant.parse("2026-11-04T12:00:00Z")
    private val today = LocalDate.parse("2026-11-04")

    private fun watch(
        status: String = "active",
        baseline: Double? = 500.0,
        last: Double? = 454.0,
        originLabel: String = "Berlin, Germany",
        originCode: String = "BER",
        flexible: Int = 0,
        history: List<FareWatchHistory> = emptyList(),
    ) = FareWatch(
        id = 7,
        status = status,
        originLabel = originLabel,
        destinationLabel = "Lisbon, Portugal",
        originCode = originCode,
        destinationCode = "LIS",
        departureDate = "2026-12-01",
        returnDate = "2026-12-08",
        numTravelers = 2,
        baselinePrice = baseline,
        lastPrice = last,
        flexibleDays = flexible,
        history = history,
    )

    @Test
    fun `watches only single-city trips departing after today`() {
        val t = WatchTrip("BER", "LIS", "2026-11-05", null, 1)
        assertTrue(isWatchableTrip(t, today))
        assertFalse(isWatchableTrip(t.copy(departureDate = "2026-11-04"), today))
        assertFalse(isWatchableTrip(t.copy(destinations = listOf("Paris", "Rome")), today))
        assertTrue(isWatchableTrip(t.copy(destinations = listOf("LIS")), today))
        assertFalse(isWatchableTrip(t.copy(origin = ""), today))
        assertFalse(isWatchableTrip(null, today))
    }

    @Test
    fun `status keys`() {
        assertEquals(WatchStatusKey.DROPPED, watchStatus(watch()))
        assertEquals(WatchStatusKey.WATCHING, watchStatus(watch(last = 520.0)))
        assertEquals(WatchStatusKey.WATCHING, watchStatus(watch(last = 500.0)))
        assertEquals(WatchStatusKey.PENDING, watchStatus(watch(baseline = null, last = null)))
        assertEquals(WatchStatusKey.PAUSED, watchStatus(watch(status = "paused")))
        assertEquals(WatchStatusKey.EXPIRED, watchStatus(watch(status = "expired")))
        assertEquals(WatchStatusKey.PROBLEM, watchStatus(watch(status = "error")))
    }

    @Test
    fun `price change in whole dollars`() {
        assertEquals(PriceChange(-46.0, Direction.DOWN), priceChange(watch()))
        assertEquals(PriceChange(30.0, Direction.UP), priceChange(watch(last = 530.0)))
        assertEquals(Direction.SAME, priceChange(watch(last = 500.4))?.direction)
        assertNull(priceChange(watch(baseline = null)))
    }

    @Test
    fun `sparkline maps higher prices higher`() {
        val g = sparkGeometry(listOf(500.0, 480.0, null, 520.0, 454.0), 100f, 20f, pad = 0f)!!
        assertEquals(4, g.points.size)
        assertEquals(0f, g.points[0].x)
        assertEquals(100f, g.points[3].x)
        assertEquals(0f, g.points[2].y)
        assertEquals(20f, g.points[3].y)
        assertEquals(454.0, g.min, 0.0)
        assertEquals(520.0, g.max, 0.0)
    }

    @Test
    fun `sparkline folds the target into the scale and handles flat, single and empty`() {
        val g = sparkGeometry(listOf(500.0, 480.0, 520.0, 454.0), 100f, 20f, pad = 0f, target = 400.0)!!
        assertEquals(400.0, g.min, 0.0)
        assertEquals(20f, g.targetY)
        assertTrue(sparkGeometry(listOf(300.0, 300.0), 100f, 20f, 0f)!!.points.all { it.y == 10f })
        assertEquals(listOf(SparkPoint(100f, 10f, 300.0)), sparkGeometry(listOf(300.0), 100f, 20f, 0f)!!.points)
        assertNull(sparkGeometry(emptyList(), 100f, 20f))
    }

    @Test
    fun `time words`() {
        assertEquals(TimeAgo.JustNow, timeAgo("2026-11-04T11:59:40Z", now))
        assertEquals(TimeAgo.Minutes(5), timeAgo("2026-11-04T11:55:00Z", now))
        assertEquals(TimeAgo.Hours(2), timeAgo("2026-11-04T10:00:00+00:00", now))
        assertEquals(TimeAgo.Yesterday, timeAgo("2026-11-03T10:00:00.123456+00:00", now))
        assertEquals(TimeAgo.Days(3), timeAgo("2026-11-01T12:00:00Z", now))
        assertEquals(TimeUntil.Hours(3), timeUntil("2026-11-04T15:00:00Z", now))
        assertEquals(TimeUntil.Minutes(20), timeUntil("2026-11-04T12:20:00Z", now))
        assertEquals(TimeUntil.Soon, timeUntil("2026-11-04T11:00:00Z", now))
        assertEquals(TimeUntil.Days(2), timeUntil("2026-11-06T12:00:00Z", now))
        assertNull(timeUntil(null, now))
    }

    @Test
    fun `target helpers`() {
        assertEquals(450.0, defaultTarget(452.0, 500.0))
        assertEquals(450.0, defaultTarget(520.0, 500.0))
        assertEquals(450.0, defaultTarget(null, 500.0))
        assertNull(defaultTarget(null, null))
        assertEquals(TargetError.NOT_A_PRICE, checkTarget("", 500.0).error)
        assertEquals(TargetError.NOT_A_PRICE, checkTarget("abc", 500.0).error)
        assertEquals(TargetError.NOT_A_PRICE, checkTarget("0", 500.0).error)
        assertEquals(TargetError.NOT_A_PRICE, checkTarget("-4", 500.0).error)
        assertEquals(TargetCheck(450.0, null, false), checkTarget("450", 500.0))
        assertEquals(1200.0, checkTarget("$1,200", 1500.0).value)
        assertTrue(checkTarget("520", 500.0).alreadyUnder)
        assertEquals(50.0, rangePosition(400.0, 600.0, 500.0)!!, 0.01)
        assertEquals(0.0, rangePosition(400.0, 600.0, 100.0))
        assertEquals(100.0, rangePosition(400.0, 600.0, 900.0))
        assertNull(rangePosition(null, 600.0, 500.0))
    }

    @Test
    fun labels() {
        assertEquals("1–8 Dec", formatDateRange("2026-12-01", "2026-12-08"))
        assertEquals("28 Nov – 5 Dec", formatDateRange("2026-11-28", "2026-12-05"))
        assertEquals("28 Dec 2026 – 4 Jan 2027", formatDateRange("2026-12-28", "2027-01-04"))
        assertEquals("1 Dec", formatDateRange("2026-12-01", null))
        assertEquals("Berlin → Lisbon", routeLabel(watch()))
        assertEquals("LHR → Lisbon", routeLabel(watch(originLabel = "", originCode = "LHR,LGW")))
        assertEquals("Tue 3 Nov", formatDay("2026-11-03"))
        assertEquals("$1,235", formatUsd(1234.6))
    }

    @Test
    fun `replan uses preferences and optionally the cheapest dates`() {
        val prefs = Preferences(nationality = "German", interests = listOf("food"), residencePermits = listOf("EU"))
        val r = replanRequest(watch(), prefs)!!
        assertEquals("Berlin, Germany", r.origin)
        assertEquals("Lisbon, Portugal", r.destination)
        assertEquals("2026-12-01", r.departureDate)
        assertEquals(2, r.numTravelers)
        assertEquals(2, r.adults)
        assertEquals("German", r.nationality)
        val w = watch(history = listOf(FareWatchHistory(price = 500.0, cheapestDepartureDate = "2026-12-03", cheapestReturnDate = "2026-12-10")))
        assertEquals("2026-12-03", replanRequest(w, prefs, cheapest = true)!!.departureDate)
        assertEquals("2026-12-01", replanRequest(watch(), prefs, cheapest = true)!!.departureDate)
        assertEquals("", replanRequest(watch(), null)!!.nationality)
    }

    @Test
    fun `cheaper nearby dates only for flexible watches that beat the own price`() {
        val h = FareWatchHistory(price = 500.0, cheapestPrice = 450.0, cheapestDepartureDate = "2026-12-03")
        assertNull(cheaperNearby(watch(history = listOf(h))))
        assertEquals(CheaperNearby("2026-12-03", 50.0), cheaperNearby(watch(flexible = 3, history = listOf(h))))
        assertNull(cheaperNearby(watch(flexible = 3, history = listOf(h.copy(cheapestPrice = 520.0)))))
    }

    @Test
    fun `cheapest flight price ignores junk and multi-city`() {
        val flights = FlightsData(results = listOf(Flight(priceUsd = 530.0), Flight(priceUsd = 480.0), Flight(priceUsd = 0.0), Flight()))
        assertEquals(480.0, cheapestFlightPrice(flights))
        assertNull(cheapestFlightPrice(FlightsData()))
        assertNull(cheapestFlightPrice(null))
        assertNull(cheapestFlightPrice(flights.copy(legs = listOf(FlightLegGroup()))))
    }

    @Test
    fun `trip builds create and match requests`() {
        val t = WatchTrip("BER", "LIS", "2026-12-01", "2026-12-08", 2, planId = 4)
        val req = t.toCreateRequest("below_target", 400.0, flexible = true, email = false)
        assertEquals(3, req.flexibleDays)
        assertEquals(400.0, req.targetPrice)
        assertEquals(4L, t.toMatchRequest().planId)
        assertNotNull(req)
    }
}
