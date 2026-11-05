package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.model.search.FareCalendarDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Every case of `frontend/src/utils/fareCalendar.test.js` (names: "describe - it"). */
class FareCalendarRulesTest {
    private fun shift(dep: String, ret: String?, offset: Long) = FareCalendarRules.shiftDates(dep, ret, offset)

    @Test
    fun `shiftDates and dayOffset - shifts both dates by the same offset, keeping trip length`() {
        assertEquals("2026-11-03" to "2026-11-10", shift("2026-10-30", "2026-11-06", 4))
        assertEquals("2026-02-28" to "2026-03-07", shift("2026-03-01", "2026-03-08", -1))
    }

    @Test
    fun `shiftDates and dayOffset - keeps a missing return date missing`() {
        assertEquals("2026-11-01" to null, shift("2026-10-30", null, 2))
    }

    @Test
    fun `shiftDates and dayOffset - counts whole days between dates`() {
        assertEquals(4L, FareCalendarRules.dayOffset("2026-10-30", "2026-11-03"))
        assertEquals(-4L, FareCalendarRules.dayOffset("2026-11-03", "2026-10-30"))
        assertEquals(2L, FareCalendarRules.dayOffset("2026-03-28", "2026-03-30")) // across a DST switch
    }

    private val dates =
        listOf(
            FareCalendarDate("2026-10-28", null, 520.0, false),
            FareCalendarDate("2026-10-29", null, null, false),
            FareCalendarDate("2026-10-30", null, 565.0, true),
            FareCalendarDate("2026-10-31", null, 610.0, false),
        )

    @Test
    fun `cheapest and deltas - finds the cheapest priced date`() {
        assertEquals("2026-10-28", FareCalendarRules.cheapestDeparture(dates))
        assertNull(FareCalendarRules.cheapestDeparture(listOf(FareCalendarDate("x", null, null))))
        assertNull(FareCalendarRules.cheapestDeparture(emptyList()))
    }

    @Test
    fun `cheapest and deltas - computes the delta against the selected date`() {
        assertEquals(-45.0, FareCalendarRules.fareDelta(dates[0], dates[2]))
        assertEquals(45.0, FareCalendarRules.fareDelta(dates[3], dates[2]))
        assertNull(FareCalendarRules.fareDelta(dates[1], dates[2]))
        assertNull(FareCalendarRules.fareDelta(dates[0], FareCalendarDate(cheapestUsd = null)))
        assertNull(FareCalendarRules.fareDelta(dates[0], null))
    }

    @Test
    fun `cheapest and deltas - formats deltas with a real minus sign`() {
        assertEquals("−$45", FareCalendarRules.formatDelta(-45.0))
        assertEquals("+$1,230", FareCalendarRules.formatDelta(1230.0))
        assertEquals("same", FareCalendarRules.formatDelta(0.0))
        assertEquals("", FareCalendarRules.formatDelta(null))
        assertEquals("−$45", FareCalendarRules.formatDelta(-44.6))
    }

    @Test
    fun `cheapest and deltas - formats prices in whole dollars`() {
        assertEquals("$1,480", FareCalendarRules.formatUsd(1480.4))
    }

    @Test
    fun `date labels - formats a day as weekday, day and month`() {
        assertEquals("Tue 3 Nov", FareCalendarRules.formatDay("2026-11-03"))
    }

    @Test
    fun `date labels - formats a trip range and one-way trips`() {
        assertEquals("Tue 3 Nov – Tue 10 Nov", FareCalendarRules.formatTripRange("2026-11-03", "2026-11-10"))
        assertEquals("Tue 3 Nov", FareCalendarRules.formatTripRange("2026-11-03", null))
    }

    @Test
    fun `date labels - builds the re-plan confirmation`() {
        assertEquals("Re-plan for Tue 3 Nov – Tue 10 Nov? This runs a new search.", FareCalendarRules.replanPrompt("2026-11-03", "2026-11-10"))
    }

    private fun chip(json: String?) = FareCalendarRules.priceInsights(json?.let(::obj))

    @Test
    fun `priceInsightsChip - describes the price level with the typical range`() {
        val low = chip("""{"price_level":"low","typical_price_range":[420,610]}""")!!
        assertEquals(PriceLevel.LOW, low.level)
        assertEquals("Fares are low right now · typical $420–$610", FareCalendarRules.priceInsightsText(low))
        assertEquals("Fares are high right now · typical $1,420–$1,610", FareCalendarRules.priceInsightsText(chip("""{"price_level":"high","typical_price_range":[1420,1610]}""")!!))
    }

    @Test
    fun `priceInsightsChip - omits a missing or malformed range`() {
        assertEquals(PriceInsight(PriceLevel.TYPICAL, null, null), chip("""{"price_level":"typical"}"""))
        assertEquals("Fares are typical right now", FareCalendarRules.priceInsightsText(chip("""{"price_level":"typical","typical_price_range":["a",3]}""")!!))
    }

    @Test
    fun `priceInsightsChip - returns null without a known level`() {
        assertNull(chip(null))
        assertNull(chip("""{"lowest_price":300}"""))
        assertNull(chip("""{"price_level":"wild"}"""))
    }

    private val now = LocalDate.of(2026, 10, 29) // local 29 Oct

    @Test
    fun `daysUntil - counts calendar days from today to the departure date`() {
        assertEquals(5L, FareCalendarRules.daysUntil("2026-11-03", now))
        assertEquals(0L, FareCalendarRules.daysUntil("2026-10-29", now))
    }

    @Test
    fun `daysUntil - never goes negative and tolerates a missing date`() {
        assertEquals(0L, FareCalendarRules.daysUntil("2026-10-01", now))
        assertEquals(0L, FareCalendarRules.daysUntil(null, now))
        assertEquals(0L, FareCalendarRules.daysUntil("Tokyo, Japan", now))
    }

    @Test
    fun `year boundary - shifts, counts and labels dates across Dec to Jan`() {
        assertEquals("2027-01-02" to "2027-01-06", shift("2026-12-30", "2027-01-03", 3))
        assertEquals("2026-12-29" to "2027-01-02", shift("2027-01-01", "2027-01-05", -3))
        assertEquals(3L, FareCalendarRules.dayOffset("2026-12-30", "2027-01-02"))
        assertEquals("Fri 1 Jan", FareCalendarRules.formatDay("2027-01-01"))
        assertEquals("Thu 31 Dec – Thu 7 Jan", FareCalendarRules.formatTripRange("2026-12-31", "2027-01-07"))
        assertEquals(3L, FareCalendarRules.daysUntil("2027-01-02", LocalDate.of(2026, 12, 30)))
    }
}
