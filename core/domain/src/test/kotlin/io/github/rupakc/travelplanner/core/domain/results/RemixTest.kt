@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length") // fixtures ported verbatim from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case of `frontend/src/utils/remix.test.js` (names: "describe - it"). */
class RemixTest {
    private val baseSearch = search("2026-08-10", "2026-08-17", 3000.0, destination = "Tokyo")

    @Test
    fun `REMIX_PRESETS - exposes five presets with ids, labels and apply functions`() {
        assertEquals(5, RemixPreset.entries.size)
        assertTrue(RemixPreset.entries.all { it.id.isNotBlank() })
        assertEquals(listOf("week_later", "half_budget", "luxe", "slower_pace", "long_weekend"), RemixPreset.entries.map { it.id })
    }

    @Test
    fun `applyRemix - week_later shifts both dates by 7 days, preserving duration`() {
        val out = Remix.apply(baseSearch, "week_later")
        assertEquals("2026-08-17" to "2026-08-24", out.departureDate to out.returnDate)
    }

    @Test
    fun `applyRemix - week_later crosses month boundaries correctly`() {
        val out = Remix.apply(baseSearch.copy(departureDate = "2026-08-28", returnDate = "2026-08-30"), "week_later")
        assertEquals("2026-09-04" to "2026-09-06", out.departureDate to out.returnDate)
    }

    @Test
    fun `applyRemix - week_later on a one-way trip only shifts departure`() {
        val out = Remix.apply(baseSearch.copy(returnDate = null), "week_later")
        assertEquals("2026-08-17", out.departureDate)
        assertNull(out.returnDate)
    }

    @Test
    fun `applyRemix - half_budget halves and rounds the budget`() {
        assertEquals(1667.0, Remix.apply(baseSearch.copy(budgetUsd = 3333.0), "half_budget").budgetUsd)
    }

    @Test
    fun `applyRemix - half_budget with no budget is a no-op`() {
        val noBudget = baseSearch.copy(budgetUsd = null)
        assertSame(noBudget, Remix.apply(noBudget, "half_budget"))
        assertNull(Remix.apply(noBudget, RemixPreset.HALF_BUDGET))
    }

    @Test
    fun `applyRemix - luxe doubles the budget`() {
        assertEquals(6000.0, Remix.apply(baseSearch, "luxe").budgetUsd)
    }

    @Test
    fun `applyRemix - slower_pace sets pace to relaxed and touches nothing else`() {
        val out = Remix.apply(baseSearch, "slower_pace")
        assertEquals("relaxed", out.pace)
        assertEquals(baseSearch.copy(pace = "relaxed"), out)
    }

    @Test
    fun `applyRemix - long_weekend sets return to departure + 3 days`() {
        assertEquals("2026-08-13", Remix.apply(baseSearch, "long_weekend").returnDate)
    }

    @Test
    fun `applyRemix - unknown preset returns the input unchanged`() {
        assertSame(baseSearch, Remix.apply(baseSearch, "nope"))
    }

    @Test
    fun `applyRemix - does not mutate the input object`() {
        val copy = baseSearch.copy()
        Remix.apply(baseSearch, "week_later")
        assertEquals(copy, baseSearch)
    }

    @Test
    fun `applyRemix - handles malformed departure_date gracefully`() {
        val bad = baseSearch.copy(departureDate = "not-a-date")
        assertSame(bad, Remix.apply(bad, "week_later"))
        assertSame(bad, Remix.apply(bad, "long_weekend"))
    }

    private val snapshotResults =
        results(
            obj(
                "flights" to mapOf("results" to listOf(mapOf("price_usd" to 900), mapOf("price_usd" to 750), mapOf("price_usd" to "x"))),
                "hotels" to mapOf("results" to listOf(mapOf("price_per_night_usd" to 120), mapOf("price_per_night_usd" to 95))),
                "itinerary" to mapOf("total_estimated_cost_usd" to 1400),
                "weather" to mapOf("days" to listOf(mapOf("is_poor" to true), mapOf("is_poor" to false), mapOf("is_poor" to true))),
            ),
        )

    @Test
    fun `snapshotMetrics - extracts min flight, min hotel nightly, itinerary total and poor weather days`() {
        assertEquals(RemixMetrics(750.0, 95.0, 1400.0, 2.0), Remix.snapshot(snapshotResults))
    }

    @Test
    fun `snapshotMetrics - returns nulls for missing or empty sections`() {
        assertEquals(RemixMetrics(null, null, null, null), Remix.snapshot(TripResults()))
    }

    @Test
    fun `snapshotMetrics - ignores error payloads and non-numeric prices`() {
        val snap =
            Remix.snapshot(
                results(
                    obj(
                        "flights" to mapOf("error" to "boom"),
                        "hotels" to mapOf("results" to listOf(mapOf("price_per_night_usd" to -5))),
                        "itinerary" to mapOf("total_estimated_cost_usd" to "NaN"),
                    ),
                ),
            )
        assertNull(snap.minFlightUsd)
        assertNull(snap.minHotelNightlyUsd)
        assertNull(snap.itineraryTotalUsd)
    }

    @Test
    fun `diffMetrics - computes deltas with direction and improvement flags`() {
        val rows = Remix.diff(RemixMetrics(900.0, 100.0, 1400.0, 3.0), RemixMetrics(700.0, 130.0, 1400.0, 1.0)).associateBy { it.metric }
        rows.getValue(RemixMetric.MIN_FLIGHT).let { assertEquals(Triple(-200.0, "down", true), Triple(it.delta, it.direction, it.improved)) }
        rows.getValue(RemixMetric.MIN_HOTEL_NIGHTLY).let { assertEquals(Triple(30.0, "up", false), Triple(it.delta, it.direction, it.improved)) }
        rows.getValue(RemixMetric.ITINERARY_TOTAL).let { assertEquals(Triple(0.0, "same", null), Triple(it.delta, it.direction, it.improved)) }
        rows.getValue(RemixMetric.POOR_WEATHER_DAYS).let { assertEquals(-2.0 to true, it.delta to it.improved) }
    }

    @Test
    fun `diffMetrics - skips metrics missing on either side`() {
        assertEquals(emptyList<MetricDiff>(), Remix.diff(RemixMetrics(minFlightUsd = 900.0), RemixMetrics(minFlightUsd = null)))
    }

    @Test
    fun `formatMetricValue - formats usd values with dollar sign and thousands separators`() {
        assertEquals("$1,235", Remix.formatMetricValue(1234.6, isMoney = true))
    }

    @Test
    fun `formatMetricValue - formats counts as plain strings and null as em dash`() {
        assertEquals("3", Remix.formatMetricValue(3.0, isMoney = false))
        assertEquals("—", Remix.formatMetricValue(null, isMoney = true))
    }
}
