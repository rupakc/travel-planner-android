@file:Suppress("MaxLineLength") // inline JSON fixtures ported from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from `frontend/src/utils/bestValuePackage.test.js`. */
class BestValuePackageTest {
    private val sd = search(departure = "2026-04-01", returnDate = "2026-04-10", budget = null)

    private fun flight(n: String, price: String, stops: Int = 0, out: Int? = 600, ret: Int? = 600, source: String = "live") =
        """{"airline":"TestAir","flight_number":"$n","price_usd":$price,"source":"$source",
           "outbound":{"airline":"TestAir","flight_number":"$n","stops":$stops,"duration_minutes":$out}
           ${if (ret != null) ",\"return\":{\"stops\":0,\"duration_minutes\":$ret}" else ""}}"""

    private fun hotel(name: String, stars: String = "3", nightly: String = "100", review: String = "8", city: String? = null) =
        """{"name":"$name","star_rating":$stars,"price_per_night_usd":$nightly,"review_score":$review${city?.let { ",\"city\":\"$it\"" }.orEmpty()}}"""

    private fun fl(vararg f: String) = TripResults(flights = flightsOf("""{"results":[${f.joinToString()}]}"""))

    private fun build(r: TripResults, s: io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest = sd) = BestValuePackage.build(r, s)

    private fun BestValue.reasons(type: PackageItemType) = items.filter { it.type == type }.map { it.reason }

    @Test
    fun `prefers fewer stops within 15 percent of the cheapest fare`() {
        val pkg = build(fl(flight("A", "480", stops = 2, out = 900), flight("B", "500", stops = 1, out = 700)))!!
        assertEquals("B", pkg.selections.flight?.outbound?.flightNumber)
        assertEquals(listOf(PickReason.FewerStops(20)), pkg.reasons(PackageItemType.FLIGHT))
    }

    @Test
    fun `takes the cheapest fare when the direct one is outside 15 percent`() {
        val pkg = build(fl(flight("B", "600"), flight("A", "400", stops = 2)))!!
        assertEquals("A", pkg.selections.flight?.outbound?.flightNumber)
        assertEquals(listOf(PickReason.CheapestFare), pkg.reasons(PackageItemType.FLIGHT))
    }

    @Test
    fun `drops estimates when a real fare exists, keeps them otherwise`() {
        assertEquals("R", build(fl(flight("E", "300", source = "estimate"), flight("R", "500")))!!.selections.flight?.outbound?.flightNumber)
        assertEquals("E", build(fl(flight("E", "300", source = "estimate")))!!.selections.flight?.outbound?.flightNumber)
    }

    @Test
    fun `ignores unknown prices and coerces string prices`() {
        assertEquals("S", build(fl(flight("J", "\"varies\""), flight("S", "\"450\"")))!!.selections.flight?.outbound?.flightNumber)
    }

    @Test
    fun `takes a pricier same-stops fare that saves 90 plus minutes`() {
        val pkg = build(fl(flight("S", "400", 1, 600, null), flight("F", "430", 1, 480, null)))!!
        assertEquals("F", pkg.selections.flight?.outbound?.flightNumber)
        assertEquals(listOf(PickReason.SavesTime(120, 30)), pkg.reasons(PackageItemType.FLIGHT))
        val shortest = build(fl(flight("S", "400", 1, 600, null), flight("A", "410", 1, 500, null), flight("B", "430", 1, 400, null)))!!
        assertEquals(listOf(PickReason.SavesTime(200, 30)), shortest.reasons(PackageItemType.FLIGHT))
    }

    @Test
    fun `does not chase 5 minutes`() {
        val pkg = build(fl(flight("D", "183", 0, 155, 160), flight("C", "169", 0, 155, 165)))!!
        assertEquals("C", pkg.selections.flight?.outbound?.flightNumber)
    }

    @Test
    fun `multi-city picks one per leg`() {
        val r =
            TripResults(
                flights =
                    flightsOf(
                        """{"legs":[{"leg_index":0,"results":[
                        {"leg_index":0,"price_usd":100,"outbound":{"airline":"A","flight_number":"1","stops":1}},
                        {"leg_index":0,"price_usd":110,"outbound":{"airline":"B","flight_number":"2","stops":0}}]},
                        {"leg_index":1,"results":[{"leg_index":1,"price_usd":80,"outbound":{"airline":"C","flight_number":"3","stops":0}}]},
                        {"leg_index":2,"results":[]}]}""",
                    ),
            )
        val pkg = build(r)!!
        assertNull(pkg.selections.flight)
        assertEquals(listOf("B", "C"), pkg.selections.flights.map { it.outbound?.airline })
        assertEquals(2, pkg.items.count { it.type == PackageItemType.FLIGHTS })
    }

    @Test
    fun `hotel prefers 3 star best reviewed, then 4 star, then any`() {
        val pkg =
            build(
                TripResults(
                    hotels = hotelsOf("""{"results":[${hotel("Low", review = "7")},${hotel("Best", review = "9")},${hotel("Four", "4", review = "10")}]}"""),
                ),
            )!!
        assertEquals("Best", pkg.selections.hotel?.name)
        assertEquals(listOf(PickReason.BestReviewed(3, null)), pkg.reasons(PackageItemType.HOTEL))
        assertEquals(
            "Four",
            build(TripResults(hotels = hotelsOf("""{"results":[${hotel("Five", "5", review = "10")},${hotel("Four", "4")}]}""")))!!.selections.hotel?.name,
        )
        assertEquals(
            "K",
            build(
                TripResults(hotels = hotelsOf("""{"results":[${hotel("U", "\"3\"", review = "null")},${hotel("K", "2.6", review = "6")}]}""")),
            )!!.selections.hotel?.name,
        )
    }

    @Test
    fun `multi-city hotel restricted to the first destination`() {
        val r =
            TripResults(hotels = hotelsOf("""{"results":[${hotel("Rome", city = "Rome", review = "10")},${hotel("Paris", city = "Paris", review = "7")}]}"""))
        val pkg = build(r, search(departure = "2026-04-01", returnDate = "2026-04-10", budget = null, destinations = listOf("Paris", "Rome")))!!
        assertEquals("Paris", pkg.selections.hotel?.name)
        assertEquals(listOf(PickReason.BestReviewed(3, "Paris")), pkg.reasons(PackageItemType.HOTEL))
    }

    @Test
    fun `budget fit swaps in a cheaper decently reviewed hotel`() {
        fun results(withCheap: Boolean = true) =
            TripResults(
                flights = flightsOf("""{"results":[${flight("TA1", "500")}]}"""),
                hotels =
                    hotelsOf(
                        """{"results":[${hotel(
                            "Pricey",
                            nightly = "300",
                            review = "9",
                        )}${if (withCheap) "," + hotel("Cheap", "2", "50", "7.5") else ""},${hotel("Awful", "1", "20", "5")}]}""",
                    ),
            )
        val budgeted = sd.copy(budgetUsd = 1500.0)
        val pkg = build(results(), budgeted)!!
        assertEquals("Cheap", pkg.selections.hotel?.name)
        assertEquals(listOf(PickReason.CheaperStay), pkg.reasons(PackageItemType.HOTEL))
        assertEquals(computePlanCost(pkg.selections, budgeted), pkg.total, 0.0)
        assertEquals("Pricey", build(results(), sd.copy(budgetUsd = 99999.0))!!.selections.hotel?.name)
        assertEquals("Pricey", build(results(withCheap = false), sd.copy(budgetUsd = 100.0))!!.selections.hotel?.name)
    }

    private fun acts(vararg a: String) =
        TripResults(flights = flightsOf("""{"results":[${flight("TA1", "500")}]}"""), activities = activitiesOf("""{"results":[${a.joinToString()}]}"""))

    private fun act(name: String, price: String, score: Double = 0.4, city: String = "Paris") =
        """{"name":"$name","price_usd":$price,"similarity_score":$score,"city":"$city"}"""

    @Test
    fun `activities never pick unknown prices and keep the 30 dollar floor`() {
        val pkg = build(acts(act("N", "null"), act("Z", "0"), act("V", "\"varies\"")))!!
        assertEquals(listOf("Z"), pkg.selections.activities.map { it.name })
        val free = PickReason.ActivityPrice(0.0, typical = false, matchesInterests = false)
        assertEquals(listOf(free), pkg.reasons(PackageItemType.ACTIVITIES))
        val floor = build(acts(act("S", "\"45\""), act("T", "60"), act("U", "90"), act("OK", "\"12\"")))!!
        assertEquals(listOf("OK", "S"), floor.selections.activities.map { it.name })
    }

    @Test
    fun `activities cap at 2 per city and 4 overall`() {
        val list = listOf("A", "B", "C").flatMap { c -> (0..2).map { i -> act("$c$i", "10", 0.9 - i * 0.1, c) } }
        val pkg = build(acts(*list.toTypedArray()))!!
        assertEquals(listOf("A0", "B0", "C0", "A1").sorted(), pkg.selections.activities.mapNotNull { it.name }.sorted())
        assertEquals(4, pkg.selections.activities.size)
    }

    @Test
    fun `places only with admission exactly 0, tours top pick per city, sim covering the trip`() {
        val r =
            TripResults(
                hotels = hotelsOf("""{"results":[${hotel("H")}]}"""),
                placesToSee =
                    placesOf(
                        """{"results":[{"name":"Zero","admission_fee_usd":0,"rating":4},{"name":"StrZero","admission_fee_usd":"0"},{"name":"Paid","admission_fee_usd":5},{"name":"Unknown"}]}""",
                    ),
                walkingTours =
                    toursOf(
                        """{"results":[{"name":"Pick","url":"https://t/1","top_pick":true,"rating":4,"city":"Paris","price_note":"Free · tip-based"},
                        {"name":"Other","url":"https://t/2","rating":5,"city":"Paris"},{"name":"Rome","url":"https://t/3","rating":4,"city":"Rome"}]}""",
                    ),
                sim =
                    simOf(
                        """{"plans":[{"provider":"P","plan_name":"Short","price_usd":5,"validity_days":7},{"provider":"P","plan_name":"Long","price_usd":15,"validity_days":30}]}""",
                    ),
            )
        val pkg = build(r)!!
        assertEquals(listOf("StrZero", "Zero"), pkg.selections.placesToSee.mapNotNull { it.name }.sorted())
        assertEquals(listOf("Pick", "Rome"), pkg.selections.walkingTours.mapNotNull { it.name }.sorted())
        assertEquals(PickReason.TourNote("Free · tip-based"), pkg.reasons(PackageItemType.WALKING_TOURS).first())
        assertEquals("Long", pkg.selections.sim?.planName)
        assertEquals(listOf(PickReason.CoversTrip(10)), pkg.reasons(PackageItemType.SIM))
    }

    @Test
    fun `returns null without a flight and a hotel, hotel alone is enough`() {
        assertNull(build(TripResults()))
        assertNull(build(TripResults(flights = flightsOf("""{"error":"x"}"""), activities = activitiesOf("""{"results":[${act("A", "0")}]}"""))))
        assertNotNull(build(TripResults(hotels = hotelsOf("""{"results":[${hotel("H")}]}"""))))
    }

    @Test
    fun `apply keeps existing items, adds without duplicates, is idempotent, applied and conflicts`() {
        val r =
            TripResults(
                flights = flightsOf("""{"results":[${flight("TA1", "500")}]}"""),
                hotels = hotelsOf("""{"results":[${hotel("Pkg Hotel")}]}"""),
                activities = activitiesOf("""{"results":[${act("A1", "10")},${act("A2", "12", city = "Lyon")}]}"""),
            )
        val pkg = build(r)!!
        val before = selectionsOf("""{"hotel":{"name":"Mine"},"activities":[{"name":"Mine"}],"tips":[{"title":"t"}],"itinerary_notes":{"1":"n"}}""")
        assertEquals(listOf(PackageConflict.HOTEL), BestValuePackage.conflicts(before, pkg))
        val after = BestValuePackage.apply(before, pkg)
        assertEquals("Pkg Hotel", after.hotel?.name)
        assertEquals(listOf("Mine", "A1", "A2"), after.activities.map { it.name })
        assertEquals(listOf("t"), after.tips.map { it.title })
        assertEquals(mapOf("1" to "n"), after.itineraryNotes)
        assertEquals(after.count, BestValuePackage.apply(after, pkg).count)
        assertTrue(BestValuePackage.isApplied(after, pkg))
        assertFalse(BestValuePackage.isApplied(after.copy(activities = emptyList()), pkg))
        assertFalse(BestValuePackage.isApplied(PlanSelections(), pkg))
        assertEquals(emptyList<PackageConflict>(), BestValuePackage.conflicts(PlanSelections(), pkg))
    }

    @Test
    fun `multi-city apply replaces only covered legs`() {
        val r =
            TripResults(
                flights =
                    flightsOf(
                        """{"legs":[{"leg_index":0,"results":[{"leg_index":0,"price_usd":100,"outbound":{"airline":"New","flight_number":"1"}}]}]}""",
                    ),
            )
        val pkg = build(r)!!
        val mine =
            selectionsOf(
                """{"flights":[{"leg_index":0,"price_usd":90,"outbound":{"airline":"Mine0"}},{"leg_index":1,"price_usd":90,"outbound":{"airline":"Mine1"}}]}""",
            )
        assertEquals(listOf(PackageConflict.FLIGHT), BestValuePackage.conflicts(mine, pkg))
        assertEquals(listOf("New", "Mine1"), BestValuePackage.apply(mine, pkg).flights.map { it.outbound?.airline })
    }
}
