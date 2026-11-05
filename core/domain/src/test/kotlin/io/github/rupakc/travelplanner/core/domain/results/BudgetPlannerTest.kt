@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length") // fixtures ported verbatim from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case of `frontend/src/utils/budgetPlanner.test.js` (names: "describe - it"). */
class BudgetPlannerTest {
    // 2026-04-01 → 2026-04-08 = 7 nights
    private fun sd(budget: Double? = 3000.0, returnDate: String? = "2026-04-08") = search("2026-04-01", returnDate, budget)

    private fun flight(n: String, price: Any?, vararg over: Pair<String, Any?>) =
        obj(
            "airline" to "Air",
            "flight_number" to n,
            "price_usd" to price,
            "source" to "live",
            "outbound" to mapOf("airline" to "Air", "flight_number" to n, "stops" to 0, "duration_minutes" to 600),
        ).with(*over)

    private fun hotel(name: String, nightly: Any?, vararg over: Pair<String, Any?>) =
        obj("name" to name, "price_per_night_usd" to nightly, "star_rating" to 3, "review_score" to 8).with(*over)

    private fun activity(name: String, price: Any?, score: Double) = obj("name" to name, "price_usd" to price, "similarity_score" to score)

    private fun fl(vararg f: JsonObject) = mapOf("results" to f.toList())

    private fun hl(vararg h: JsonObject) = mapOf("results" to h.toList())

    private fun r(vararg sections: Pair<String, Any?>) = results(obj(*sections))

    private fun a(f: Double, s: Double, ac: Double, o: Double) = BudgetAllocation(f, s, ac, o)

    private fun keyOf(f: JsonObject) = flightKey(flightsOf(obj("results" to listOf(f))).results.single())

    private fun allIntegers(x: BudgetAllocation) = listOf(x.flights, x.stay, x.activities, x.other).all { it == Math.floor(it) }

    @Test
    fun `tripNights - counts nights from dates, minimum 1, default 7`() {
        assertEquals(7, BudgetPlanner.tripNights(sd()))
        assertEquals(1, BudgetPlanner.tripNights(sd(returnDate = "2026-04-01")))
        assertEquals(7, BudgetPlanner.tripNights(sd(returnDate = null)))
        assertEquals(7, BudgetPlanner.tripNights(null))
        assertEquals(7, BudgetPlanner.tripNights(sd(returnDate = "nope")))
    }

    @Test
    fun `flightKey - is stable per fare and differs by leg, price, airline and number`() {
        val f = flight("A1", 500)
        assertEquals(keyOf(f), keyOf(f.with()))
        assertNotEquals(keyOf(f), keyOf(flight("A2", 500)))
        assertNotEquals(keyOf(f), keyOf(flight("A1", 501)))
        assertNotEquals(keyOf(f), keyOf(f.with("leg_index" to 1)))
    }

    private val defaults =
        r(
            "flights" to fl(flight("A", 800), flight("B", 650), flight("J", "varies")),
            "hotels" to hl(hotel("H1", 100), hotel("H2", 150), hotel("H3", 200), hotel("Bad", "n/a")),
        )

    @Test
    fun `defaultAllocation - returns null without a positive budget`() {
        assertNull(BudgetPlanner.defaultAllocation(sd(budget = null), defaults))
        assertNull(BudgetPlanner.defaultAllocation(sd(budget = 0.0), defaults))
        // ("lots" cannot reach the app: budget_usd is a number in TravelSearchRequest.)
        assertNull(BudgetPlanner.defaultAllocation(sd(budget = -5.0), defaults))
    }

    @Test
    fun `defaultAllocation - cheapest fare, median nightly x nights, 15 percent activities, rest to other`() {
        assertEquals(a(650.0, 1050.0, 450.0, 850.0), BudgetPlanner.defaultAllocation(sd(), defaults))
    }

    @Test
    fun `defaultAllocation - sums exactly to the budget as integers`() {
        val x = BudgetPlanner.defaultAllocation(sd(budget = 2999.6), r("flights" to fl(flight("A", 333.33))))!!
        assertTrue(allIntegers(x))
        assertEquals(3000.0, x.total, 0.0)
    }

    @Test
    fun `defaultAllocation - rounds flights and stay up to the next 10 and activities to the nearest 10`() {
        val res = r("flights" to fl(flight("A", 499.4), flight("B", 520)), "hotels" to hl(hotel("H1", 100.3), hotel("H2", 120.45), hotel("H3", 300)))
        val x = BudgetPlanner.defaultAllocation(sd(budget = 3333.0), res)!!
        assertEquals(a(500.0, 850.0, 500.0, 1483.0), x)
        assertTrue(listOf(x.flights, x.stay, x.activities).all { it % 10 == 0.0 })
    }

    @Test
    fun `defaultAllocation - default shares cover the cheapest fare, the median hotel and each multi-city leg`() {
        val single = r("flights" to fl(flight("A", 499.4), flight("B", 700)), "hotels" to hl(hotel("H1", 99.99), hotel("H2", 120.45), hotel("H3", 300)))
        val fit1 = BudgetPlanner.fitResults(single, BudgetPlanner.defaultAllocation(sd(), single)!!, sd())
        assertEquals(1, fit1.flights.count)
        assertEquals(listOf("H1", "H2"), fit1.hotels.fits.toList())
        val legs =
            r(
                "flights" to
                    mapOf(
                        "legs" to
                            listOf(
                                mapOf("leg_index" to 0, "results" to listOf(flight("A0", 123.45, "leg_index" to 0))),
                                mapOf("leg_index" to 1, "results" to listOf(flight("A1", 98.76, "leg_index" to 1))),
                                mapOf("leg_index" to 2, "results" to listOf(flight("A2", 0.1 + 0.2, "leg_index" to 2))),
                            ),
                    ),
            )
        val x = BudgetPlanner.defaultAllocation(sd(), legs)!!
        assertEquals(230.0, x.flights, 0.0)
        assertEquals(3, BudgetPlanner.fitResults(legs, x, sd()).flights.count)
    }

    @Test
    fun `defaultAllocation - overshoot keeps the cheapest fare - 300 total, 280 fare, no hotels`() {
        val res = r("flights" to fl(flight("F", 280)))
        val x = BudgetPlanner.defaultAllocation(sd(budget = 300.0), res)!!
        assertEquals(280.0, x.flights, 0.0)
        assertEquals(300.0, x.total, 0.0)
        assertTrue(allIntegers(x) && listOf(x.flights, x.stay, x.activities, x.other).all { it >= 0 })
        assertEquals(1, BudgetPlanner.fitResults(res, x, sd()).flights.count)
    }

    @Test
    fun `defaultAllocation - overshoot shrinks activities, then stay, before flights`() {
        val res = r("flights" to fl(flight("F", 280)), "hotels" to hl(hotel("H", 40)))
        val x = BudgetPlanner.defaultAllocation(sd(budget = 500.0), res)!!
        assertEquals(a(280.0, 220.0, 0.0, 0.0), x)
        assertEquals(1, BudgetPlanner.fitResults(res, x, sd()).flights.count)
    }

    @Test
    fun `defaultAllocation - overshoot snaps the shrunk shares to the 10 grid`() {
        // 15% of 315 = 47.25 → 50; only 35 left → activities 30, other 5
        assertEquals(a(280.0, 0.0, 30.0, 5.0), BudgetPlanner.defaultAllocation(sd(budget = 315.0), r("flights" to fl(flight("F", 280)))))
    }

    @Test
    fun `defaultAllocation - a fare above the total gets the whole total and does not fit`() {
        val res = r("flights" to fl(flight("F", 350)), "hotels" to hl(hotel("H", 40)))
        val x = BudgetPlanner.defaultAllocation(sd(budget = 300.0), res)!!
        assertEquals(a(300.0, 0.0, 0.0, 0.0), x)
        assertEquals(0, BudgetPlanner.fitResults(res, x, sd()).flights.count)
    }

    @Test
    fun `defaultAllocation - a fare just under an off-grid total still fits`() {
        val res = r("flights" to fl(flight("F", 296)))
        val x = BudgetPlanner.defaultAllocation(sd(budget = 299.0), res)!!
        assertEquals(299.0, x.flights, 0.0)
        assertEquals(1, BudgetPlanner.fitResults(res, x, sd()).flights.count)
    }

    @Test
    fun `defaultAllocation - fit comparisons tolerate floating-point noise`() {
        // 0.1 + 0.2 = 0.30000000000000004 > 0.3
        val res =
            r(
                "flights" to fl(flight("F", 0.1 + 0.2)),
                "hotels" to hl(hotel("H", (0.1 + 0.2) / 7)),
                "activities" to mapOf("results" to listOf(activity("A", 0.1 + 0.2, 1.0))),
            )
        val fit = BudgetPlanner.fitResults(res, a(0.3, 0.3, 0.3, 0.0), sd())
        assertEquals(1, fit.flights.count)
        assertEquals(1, fit.hotels.count)
        assertEquals(setOf("A"), fit.activities.picked)
    }

    @Test
    fun `defaultAllocation - multi-city sums the cheapest priced fare per leg`() {
        val res =
            r(
                "flights" to
                    mapOf(
                        "legs" to
                            listOf(
                                mapOf("leg_index" to 0, "results" to listOf(flight("A", 200, "leg_index" to 0), flight("B", 150, "leg_index" to 0))),
                                mapOf("leg_index" to 1, "results" to listOf(flight("C", 100, "leg_index" to 1))),
                                mapOf("leg_index" to 2, "results" to emptyList<Any>()),
                            ),
                    ),
            )
        assertEquals(250.0, BudgetPlanner.defaultAllocation(sd(), res)!!.flights, 0.0)
    }

    @Test
    fun `defaultAllocation - when the defaults overshoot, activities then stay give way so other is never negative`() {
        assertEquals(a(650.0, 350.0, 0.0, 0.0), BudgetPlanner.defaultAllocation(sd(budget = 1000.0), defaults))
    }

    @Test
    fun `defaultAllocation - missing or errored sections allocate 0`() {
        assertEquals(a(0.0, 0.0, 450.0, 2550.0), BudgetPlanner.defaultAllocation(sd(), r("flights" to mapOf("error" to "x"))))
    }

    private val base = a(600.0, 1000.0, 400.0, 1000.0)

    @Test
    fun `reallocate - takes from other when a category grows, gives back when it shrinks`() {
        assertEquals(base.copy(stay = 1500.0, other = 500.0), BudgetPlanner.reallocate(base, BudgetCategory.STAY, 1500.0, 3000.0))
        assertEquals(base.copy(flights = 200.0, other = 1400.0), BudgetPlanner.reallocate(base, BudgetCategory.FLIGHTS, 200.0, 3000.0))
    }

    @Test
    fun `reallocate - clamps when other would go below zero, and at zero from below`() {
        assertEquals(base.copy(activities = 1400.0, other = 0.0), BudgetPlanner.reallocate(base, BudgetCategory.ACTIVITIES, 5000.0, 3000.0))
        assertEquals(base.copy(activities = 0.0, other = 1400.0), BudgetPlanner.reallocate(base, BudgetCategory.ACTIVITIES, -50.0, 3000.0))
    }

    @Test
    fun `reallocate - keeps the total constant and ignores other or unknown keys`() {
        assertEquals(3000.0, BudgetPlanner.reallocate(base, BudgetCategory.STAY, 1234.0, 3000.0).total, 0.0)
        // "other" and unknown keys are not BudgetCategory values in Kotlin: the type system rules them out.
        assertEquals(BudgetCategory.entries.toSet(), setOf(BudgetCategory.FLIGHTS, BudgetCategory.STAY, BudgetCategory.ACTIVITIES))
    }

    @Test
    fun `withTotal - a larger or smaller total moves other first`() {
        assertEquals(base.copy(other = 2000.0), BudgetPlanner.withTotal(base, 4000.0))
        assertEquals(base.copy(other = 500.0), BudgetPlanner.withTotal(base, 2500.0))
    }

    @Test
    fun `withTotal - scales categories down when other cannot absorb the cut`() {
        val x = BudgetPlanner.withTotal(base, 1000.0)
        assertEquals(1000.0, x.total, 0.0)
        assertTrue(x.other >= 0)
        assertTrue(x.stay < 1000)
    }

    @Test
    fun `withTotal - negative or junk totals become 0`() {
        assertEquals(0.0, BudgetPlanner.withTotal(base, -5.0).total, 0.0)
        assertEquals(0.0, BudgetPlanner.withTotal(base, Double.NaN).total, 0.0)
    }

    private val alloc = a(700.0, 1050.0, 100.0, 1150.0)

    @Test
    fun `fitResults - hotels fit when nightly x nights is at most stay, junk prices are unknown`() {
        val res = r("hotels" to hl(hotel("Cheap", 100), hotel("Edge", 150), hotel("Dear", 151), hotel("Junk", "varies"), hotel("Zero", 0)))
        val fit = BudgetPlanner.fitResults(res, alloc, sd())
        assertEquals(7, fit.nights)
        assertEquals(listOf("Cheap", "Edge"), fit.hotels.fits.toList())
        assertEquals(listOf("Dear"), fit.hotels.over.toList())
        assertEquals(2, fit.hotels.count)
        assertEquals(3, fit.hotels.total)
    }

    @Test
    fun `fitResults - flights fit when price is at most the flights share, junk prices never fit nor count`() {
        val ok = flight("OK", 700)
        val dear = flight("DEAR", 701)
        val junk = flight("J", "tbd")
        val fit = BudgetPlanner.fitResults(r("flights" to fl(ok, dear, junk)), alloc, sd())
        assertTrue(keyOf(ok) in fit.flights.fits)
        assertFalse(keyOf(dear) in fit.flights.fits)
        assertTrue(keyOf(dear) in fit.flights.over)
        assertFalse(keyOf(junk) in fit.flights.over)
        assertEquals(1, fit.flights.count)
        assertEquals(2, fit.flights.total)
    }

    @Test
    fun `fitResults - multi-city - each leg gets the flights share in proportion to its cheapest fare`() {
        // cheapest: leg0 300, leg1 100 → total 400; share 600 → leg0 cap 450, leg1 cap 150
        val a0 = flight("A0", 300, "leg_index" to 0)
        val b0 = flight("B0", 450, "leg_index" to 0)
        val c0 = flight("C0", 451, "leg_index" to 0)
        val a1 = flight("A1", 100, "leg_index" to 1)
        val b1 = flight("B1", 160, "leg_index" to 1)
        val unknownLeg = flight("U2", "n/a", "leg_index" to 2)
        val res =
            r(
                "flights" to
                    mapOf(
                        "legs" to
                            listOf(
                                mapOf("leg_index" to 0, "results" to listOf(a0, b0, c0)),
                                mapOf("leg_index" to 1, "results" to listOf(a1, b1)),
                                mapOf("leg_index" to 2, "results" to listOf(unknownLeg)),
                            ),
                    ),
            )
        val fit = BudgetPlanner.fitResults(res, alloc.copy(flights = 600.0), sd())
        assertTrue(listOf(a0, b0, a1).all { keyOf(it) in fit.flights.fits })
        assertTrue(listOf(c0, b1).all { keyOf(it) in fit.flights.over })
        assertFalse(keyOf(unknownLeg) in fit.flights.fits)
        assertFalse(keyOf(unknownLeg) in fit.flights.over)
        assertEquals(3, fit.flights.count)
        assertEquals(5, fit.flights.total)
    }

    @Test
    fun `fitResults - activities - greedy by similarity desc, ties cheaper first, free ones always fit`() {
        val res =
            r(
                "activities" to
                    mapOf(
                        "results" to
                            listOf(
                                activity("Big", 80, 0.9),
                                activity("TieDear", 30, 0.7),
                                activity("TieCheap", 15, 0.7),
                                activity("Free", 0, 0.1),
                                activity("Junk", "varies", 1.0),
                                activity("Late", 5, 0.2),
                            ),
                    ),
            )
        val fit = BudgetPlanner.fitResults(res, alloc, sd())
        // 80 (Big) + 15 (TieCheap) = 95; TieDear 30 would exceed 100; Late 5 → 100
        assertEquals(listOf("Big", "Free", "Late", "TieCheap"), fit.activities.picked.sorted())
        assertEquals(100.0, fit.activities.spent, 0.0)
    }

    @Test
    fun `fitResults - free activities fit even with a zero share`() {
        val res = r("activities" to mapOf("results" to listOf(activity("Free", 0, 0.5), activity("Paid", 1, 0.9))))
        val fit = BudgetPlanner.fitResults(res, alloc.copy(activities = 0.0), sd())
        assertEquals(setOf("Free"), fit.activities.picked)
        assertEquals(0.0, fit.activities.spent, 0.0)
    }

    @Test
    fun `fitResults - tolerates missing or errored sections`() {
        val fit = BudgetPlanner.fitResults(r("hotels" to mapOf("error" to "boom")), alloc, null)
        assertEquals(0 to 0, fit.hotels.count to fit.hotels.total)
        assertEquals(0 to 0, fit.flights.count to fit.flights.total)
        assertEquals(0.0, fit.activities.spent, 0.0)
        assertEquals(7, fit.nights)
    }

    private val capped =
        r(
            "flights" to
                fl(
                    flight("CHEAP", 400, "outbound" to mapOf("airline" to "Air", "flight_number" to "CHEAP", "stops" to 2, "duration_minutes" to 900)),
                    flight("DIRECT", 450),
                ),
            "hotels" to hl(hotel("Mid", 200, "review_score" to 9), hotel("Budget", 80, "star_rating" to 2, "review_score" to 7.5)),
        )

    private fun fitFor(x: BudgetAllocation) = BudgetPlanner.capsFromFit(BudgetPlanner.fitResults(capped, x, sd()))

    @Test
    fun `best-value package with caps - limits the hotel and flight pools to what fits`() {
        val pkg = BestValuePackage.build(capped, sd(), fitFor(a(420.0, 700.0, 0.0, 1880.0)))!!
        assertEquals("Budget", pkg.selections.hotel?.name)
        assertEquals("CHEAP", pkg.selections.flight?.flightNo)
        val uncapped = BestValuePackage.build(capped, sd())!!
        assertEquals("Mid", uncapped.selections.hotel?.name)
        assertEquals("DIRECT", uncapped.selections.flight?.flightNo)
    }

    @Test
    fun `best-value package with caps - falls back to the uncapped behaviour when nothing fits`() {
        val pkg = BestValuePackage.build(capped, sd(), fitFor(a(10.0, 10.0, 0.0, 2980.0)))!!
        assertEquals("Mid", pkg.selections.hotel?.name)
        assertEquals("DIRECT", pkg.selections.flight?.flightNo)
    }

    @Test
    fun `best-value package with caps - caps apply per multi-city leg`() {
        val a0 = flight("A0", 300, "leg_index" to 0, "outbound" to mapOf("airline" to "Air", "flight_number" to "A0", "stops" to 1, "duration_minutes" to 600))
        val b0 = flight("B0", 330, "leg_index" to 0)
        val res = r("flights" to mapOf("legs" to listOf(mapOf("leg_index" to 0, "results" to listOf(a0, b0)))))
        val caps = BudgetPlanner.capsFromFit(BudgetPlanner.fitResults(res, a(310.0, 0.0, 0.0, 0.0), sd()))
        assertEquals("A0", BestValuePackage.build(res, sd(), caps)!!.selections.flights[0].flightNo)
        assertEquals("B0", BestValuePackage.build(res, sd())!!.selections.flights[0].flightNo)
        assertSame(null, BestValuePackage.build(TripResults(), sd(), caps))
    }
}
