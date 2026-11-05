package io.github.rupakc.travelplanner.core.domain.results

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from `frontend/src/utils/budgetPlanner.test.js`. */
class BudgetPlannerTest {
    private fun flight(n: String, price: String, extra: String = "") =
        """{"airline":"Air","flight_number":"$n","price_usd":$price,"source":"live",
           "outbound":{"airline":"Air","flight_number":"$n","stops":0,"duration_minutes":600}$extra}"""

    private fun hotel(name: String, nightly: String) = """{"name":"$name","price_per_night_usd":$nightly,"star_rating":3,"review_score":8}"""

    private fun activity(name: String, price: String, score: Double) = """{"name":"$name","price_usd":$price,"similarity_score":$score}"""

    private fun results(flights: List<String> = emptyList(), hotels: List<String> = emptyList(), activities: List<String> = emptyList()) =
        TripResults(
            flights = flightsOf("""{"results":[${flights.joinToString()}]}"""),
            hotels = hotelsOf("""{"results":[${hotels.joinToString()}]}"""),
            activities = activitiesOf("""{"results":[${activities.joinToString()}]}"""),
        )

    private fun alloc(f: Double, s: Double, a: Double, o: Double) = BudgetAllocation(f, s, a, o)

    @Test
    fun `tripNights counts nights, minimum 1, default 7`() {
        assertEquals(7, BudgetPlanner.tripNights(search()))
        assertEquals(1, BudgetPlanner.tripNights(search(returnDate = "2026-04-01")))
        assertEquals(7, BudgetPlanner.tripNights(null))
        assertEquals(7, BudgetPlanner.tripNights(search(returnDate = "nope")))
    }

    @Test
    fun `flightKey is stable and differs by leg, price, airline and number`() {
        val f =
            flightsOf("""{"results":[${flight("A1", "500")}, ${flight("A2", "500")}, ${flight("A1", "501")}, ${flight("A1", "500", ",\"leg_index\":1")}]}""")
        val keys = f.results.map(::flightKey)
        assertEquals(4, keys.toSet().size)
        assertEquals("|500|Air|A1", keys[0])
    }

    private val defaults =
        results(
            flights = listOf(flight("A", "800"), flight("B", "650"), flight("J", "\"varies\"")),
            hotels = listOf(hotel("H1", "100"), hotel("H2", "150"), hotel("H3", "200"), hotel("Bad", "\"n/a\"")),
        )

    @Test
    fun `defaultAllocation returns null without a positive budget`() {
        assertNull(BudgetPlanner.defaultAllocation(search(budget = null), defaults))
        assertNull(BudgetPlanner.defaultAllocation(search(budget = 0.0), defaults))
    }

    @Test
    fun `cheapest fare, median nightly x nights, 15 percent activities, rest to other`() {
        assertEquals(alloc(650.0, 1050.0, 450.0, 850.0), BudgetPlanner.defaultAllocation(search(), defaults))
    }

    @Test
    fun `rounds flights and stay up to the next 10 and activities to the nearest 10`() {
        val r = results(listOf(flight("A", "499.4"), flight("B", "520")), listOf(hotel("H1", "100.3"), hotel("H2", "120.45"), hotel("H3", "300")))
        assertEquals(alloc(500.0, 850.0, 500.0, 1483.0), BudgetPlanner.defaultAllocation(search(budget = 3333.0), r))
    }

    @Test
    fun `sums exactly to the rounded budget`() {
        val a = BudgetPlanner.defaultAllocation(search(budget = 2999.6), results(listOf(flight("A", "333.33"))))!!
        assertEquals(3000.0, a.total, 0.0)
    }

    @Test
    fun `overshoot shrinks activities, then stay, before flights`() {
        val r = results(listOf(flight("F", "280")), listOf(hotel("H", "40")))
        assertEquals(alloc(280.0, 220.0, 0.0, 0.0), BudgetPlanner.defaultAllocation(search(budget = 500.0), r))
        assertEquals(alloc(280.0, 0.0, 30.0, 5.0), BudgetPlanner.defaultAllocation(search(budget = 315.0), results(listOf(flight("F", "280")))))
    }

    @Test
    fun `a fare above the total gets the whole total and does not fit`() {
        val r = results(listOf(flight("F", "350")), listOf(hotel("H", "40")))
        val a = BudgetPlanner.defaultAllocation(search(budget = 300.0), r)!!
        assertEquals(alloc(300.0, 0.0, 0.0, 0.0), a)
        assertEquals(0, BudgetPlanner.fitResults(r, a, search()).flights.count)
    }

    @Test
    fun `multi-city sums the cheapest priced fare per leg`() {
        val r =
            TripResults(
                flights =
                    flightsOf(
                        """{"legs":[{"leg_index":0,"results":[${flight("A", "200", ",\"leg_index\":0")},${flight("B", "150", ",\"leg_index\":0")}]},
                        {"leg_index":1,"results":[${flight("C", "100", ",\"leg_index\":1")}]},{"leg_index":2,"results":[]}]}""",
                    ),
            )
        assertEquals(250.0, BudgetPlanner.defaultAllocation(search(), r)!!.flights, 0.0)
    }

    @Test
    fun `missing or errored sections allocate 0`() {
        val r = TripResults(flights = flightsOf("""{"error":"x"}"""))
        assertEquals(alloc(0.0, 0.0, 450.0, 2550.0), BudgetPlanner.defaultAllocation(search(), r))
    }

    @Test
    fun `reallocate takes from other, clamps, keeps the total`() {
        val base = alloc(600.0, 1000.0, 400.0, 1000.0)
        assertEquals(base.copy(stay = 1500.0, other = 500.0), BudgetPlanner.reallocate(base, BudgetCategory.STAY, 1500.0, 3000.0))
        assertEquals(base.copy(flights = 200.0, other = 1400.0), BudgetPlanner.reallocate(base, BudgetCategory.FLIGHTS, 200.0, 3000.0))
        assertEquals(base.copy(activities = 1400.0, other = 0.0), BudgetPlanner.reallocate(base, BudgetCategory.ACTIVITIES, 5000.0, 3000.0))
        assertEquals(base.copy(activities = 0.0, other = 1400.0), BudgetPlanner.reallocate(base, BudgetCategory.ACTIVITIES, -50.0, 3000.0))
        assertEquals(3000.0, BudgetPlanner.reallocate(base, BudgetCategory.STAY, 1234.0, 3000.0).total, 0.0)
    }

    @Test
    fun `withTotal moves other first, then scales down`() {
        val base = alloc(600.0, 1000.0, 400.0, 1000.0)
        assertEquals(base.copy(other = 2000.0), BudgetPlanner.withTotal(base, 4000.0))
        assertEquals(base.copy(other = 500.0), BudgetPlanner.withTotal(base, 2500.0))
        val small = BudgetPlanner.withTotal(base, 1000.0)
        assertEquals(1000.0, small.total, 0.0)
        assertTrue(small.other >= 0 && small.stay < 1000)
        assertEquals(0.0, BudgetPlanner.withTotal(base, -5.0).total, 0.0)
    }

    @Test
    fun `hotels fit when nightly x nights is within stay, junk is unknown`() {
        val r = results(hotels = listOf(hotel("Cheap", "100"), hotel("Edge", "150"), hotel("Dear", "151"), hotel("Junk", "\"varies\""), hotel("Zero", "0")))
        val fit = BudgetPlanner.fitResults(r, alloc(700.0, 1050.0, 100.0, 1150.0), search())
        assertEquals(7, fit.nights)
        assertEquals(listOf("Cheap", "Edge"), fit.hotels.fits.toList())
        assertEquals(listOf("Dear"), fit.hotels.over.toList())
        assertEquals(3, fit.hotels.total)
    }

    @Test
    fun `multi-city legs get the share in proportion to their cheapest fare`() {
        val legs =
            flightsOf(
                """{"legs":[
                {"leg_index":0,"results":[${flight(
                    "A0",
                    "300",
                    ",\"leg_index\":0",
                )},${flight("B0", "450", ",\"leg_index\":0")},${flight("C0", "451", ",\"leg_index\":0")}]},
                {"leg_index":1,"results":[${flight("A1", "100", ",\"leg_index\":1")},${flight("B1", "160", ",\"leg_index\":1")}]},
                {"leg_index":2,"results":[${flight("U2", "\"n/a\"", ",\"leg_index\":2")}]}]}""",
            )
        val fit = BudgetPlanner.fitResults(TripResults(flights = legs), alloc(600.0, 1050.0, 100.0, 1150.0), search())
        assertEquals(3, fit.flights.count)
        assertEquals(5, fit.flights.total)
    }

    @Test
    fun `activities greedy by similarity, ties cheaper first, free always fit`() {
        val r =
            results(
                activities =
                    listOf(
                        activity("Big", "80", 0.9),
                        activity("TieDear", "30", 0.7),
                        activity("TieCheap", "15", 0.7),
                        activity("Free", "0", 0.1),
                        activity("Junk", "\"varies\"", 1.0),
                        activity("Late", "5", 0.2),
                    ),
            )
        val fit = BudgetPlanner.fitResults(r, alloc(700.0, 1050.0, 100.0, 1150.0), search())
        assertEquals(listOf("Big", "Free", "Late", "TieCheap"), fit.activities.picked.sorted())
        assertEquals(100.0, fit.activities.spent, 0.0)
    }

    @Test
    fun `fit comparisons tolerate floating point noise`() {
        val r =
            results(
                listOf(flight("F", (0.1 + 0.2).toString())),
                listOf(hotel("H", ((0.1 + 0.2) / 7).toString())),
                listOf(activity("A", (0.1 + 0.2).toString(), 1.0)),
            )
        val fit = BudgetPlanner.fitResults(r, alloc(0.3, 0.3, 0.3, 0.0), search())
        assertEquals(1, fit.flights.count)
        assertEquals(1, fit.hotels.count)
        assertEquals(setOf("A"), fit.activities.picked)
    }

    @Test
    fun `caps limit the best-value hotel and flight pools`() {
        val r =
            results(
                listOf(
                    flight("CHEAP", "400").replace("\"stops\":0,\"duration_minutes\":600", "\"stops\":2,\"duration_minutes\":900"),
                    flight("DIRECT", "450"),
                ),
                listOf(
                    """{"name":"Mid","price_per_night_usd":200,"star_rating":3,"review_score":9}""",
                    """{"name":"Budget","price_per_night_usd":80,"star_rating":2,"review_score":7.5}""",
                ),
            )
        val caps = BudgetPlanner.capsFromFit(BudgetPlanner.fitResults(r, alloc(420.0, 700.0, 0.0, 1880.0), search()))
        val pkg = BestValuePackage.build(r, search(), caps)!!
        assertEquals("Budget", pkg.selections.hotel?.name)
        assertEquals("CHEAP", pkg.selections.flight?.outbound?.flightNumber)
        val uncapped = BestValuePackage.build(r, search())!!
        assertEquals("Mid", uncapped.selections.hotel?.name)
        assertEquals("DIRECT", uncapped.selections.flight?.outbound?.flightNumber)
        val none = BudgetPlanner.capsFromFit(BudgetPlanner.fitResults(r, alloc(10.0, 10.0, 0.0, 2980.0), search()))
        assertEquals("Mid", BestValuePackage.build(r, search(), none)!!.selections.hotel?.name)
        assertFalse(none.hotel!!(pkg.selections.hotel!!))
    }
}
