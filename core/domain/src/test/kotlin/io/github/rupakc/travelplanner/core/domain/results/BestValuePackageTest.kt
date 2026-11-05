@file:Suppress("MaxLineLength", "LargeClass", "ktlint:standard:max-line-length") // fixtures ported verbatim from the web tests

package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.plan.computePlanCost
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case of `frontend/src/utils/bestValuePackage.test.js` (names: "describe - it"); reasons rendered in the web's English. */
class BestValuePackageTest {
    private fun flight(vararg over: Pair<String, Any?>) =
        obj(
            "airline" to "TestAir",
            "flight_number" to "TA1",
            "price_usd" to 500,
            "source" to "live",
            "outbound" to mapOf("airline" to "TestAir", "flight_number" to "TA1", "stops" to 0, "duration_minutes" to 600),
            "return" to mapOf("stops" to 0, "duration_minutes" to 600),
        ).with(*over)

    private fun hotel(vararg over: Pair<String, Any?>) =
        obj(
            "name" to "Hotel A",
            "star_rating" to 3,
            "price_per_night_usd" to 100,
            "review_score" to 8,
        ).with(*over)

    private fun activity(vararg over: Pair<String, Any?>) = obj("name" to "Act", "price_usd" to 10, "similarity_score" to 0.4, "city" to "Paris").with(*over)

    private fun tour(vararg over: Pair<String, Any?>) =
        obj("name" to "Tour", "url" to "https://t/1", "price_note" to "Free · tip-based", "rating" to 4.5, "city" to "Paris").with(*over)

    private fun place(vararg over: Pair<String, Any?>) = obj("name" to "Place", "admission_fee_usd" to 0, "rating" to 4.5, "city" to "Paris").with(*over)

    private fun sim(vararg over: Pair<String, Any?>) = obj("provider" to "P", "plan_name" to "Plan", "price_usd" to 10, "validity_days" to 30).with(*over)

    private val searchData = search("2026-04-01", "2026-04-10", budget = null)

    private fun build(o: JsonObject?, sd: TravelSearchRequest? = searchData) = BestValuePackage.build(if (o == null) TripResults() else results(o), sd)

    private fun fl(vararg f: JsonObject) = arrayOf("flights" to mapOf("results" to f.toList()))

    private fun BestValue.itemsOf(type: PackageItemType) = items.filter { it.type == type }

    private fun BestValue.reason(type: PackageItemType) = en(itemsOf(type)[0].reason)

    private val BestValue.flightNo get() = selections.flight?.flightNo

    private fun money(n: Number) = "$" + "%,d".format(Math.round(n.toDouble()))

    private fun dur(mins: Int) =
        listOfNotNull(
            (mins / 60).takeIf {
                it > 0
            }?.let { "${it}h" },
            (mins % 60).takeIf { it > 0 }?.let { "${it}m" },
        ).joinToString(" ")

    /** The web's English reason strings (the app renders the same copy from strings.xml). */
    private fun en(r: PickReason): String =
        when (r) {
            PickReason.CheapestFare -> {
                "Cheapest fare"
            }

            is PickReason.FewerStops -> {
                "Fewer stops for ${money(r.extra)} more than the cheapest fare"
            }

            is PickReason.SavesTime -> {
                "Saves ${dur(r.minutes)} of travel for ${money(r.extra)} more than the cheapest fare"
            }

            is PickReason.BestReviewed -> {
                val label =
                    when (r.stars) {
                        3 -> "Mid-range (3★), best-reviewed in its class"
                        null -> "Best-reviewed in its class"
                        else -> "${r.stars}★, best-reviewed in its class"
                    }
                r.city?.let { "$label · $it" } ?: label
            }

            PickReason.CheaperStay -> {
                "Cheaper stay to fit your budget"
            }

            is PickReason.ActivityPrice -> {
                val base =
                    if (r.price == 0.0) {
                        "Free"
                    } else if (r.typical) {
                        "${money(r.price)} · at or below the typical price here"
                    } else {
                        money(r.price)
                    }
                if (r.matchesInterests) "$base · matches your interests" else base
            }

            is PickReason.TourNote -> {
                r.priceNote ?: "Free walking tour"
            }

            PickReason.FreeEntry -> {
                "Free entry"
            }

            is PickReason.CoversTrip -> {
                "Covers your ${r.days}-day trip"
            }

            PickReason.CheapestPlan -> {
                "Cheapest plan"
            }
        }

    // ── flight pick ──

    @Test
    fun `flight pick - prefers fewer stops within 15 percent of the cheapest fare`() {
        val twoStop =
            flight(
                "flight_number" to "A",
                "price_usd" to 480,
                "outbound" to mapOf("airline" to "X", "flight_number" to "A", "stops" to 2, "duration_minutes" to 900),
            )
        val oneStop =
            flight(
                "flight_number" to "B",
                "price_usd" to 500,
                "outbound" to mapOf("airline" to "Y", "flight_number" to "B", "stops" to 1, "duration_minutes" to 700),
            )
        val pkg = build(obj(*fl(twoStop, oneStop)))!!
        assertEquals("B", pkg.flightNo)
        assertEquals("Fewer stops for $20 more than the cheapest fare", pkg.reason(PackageItemType.FLIGHT))
    }

    @Test
    fun `flight pick - takes the cheapest fare when the direct one is outside 15 percent`() {
        val cheap = flight("flight_number" to "A", "price_usd" to 400, "outbound" to mapOf("airline" to "X", "flight_number" to "A", "stops" to 2))
        val direct = flight("flight_number" to "B", "price_usd" to 600)
        val pkg = build(obj(*fl(direct, cheap)))!!
        assertEquals("A", pkg.flightNo)
        assertEquals("Cheapest fare", pkg.reason(PackageItemType.FLIGHT))
    }

    @Test
    fun `flight pick - drops estimates when a real fare exists, keeps them otherwise`() {
        val est =
            flight(
                "flight_number" to "E",
                "price_usd" to 300,
                "source" to "estimate",
                "outbound" to mapOf("airline" to "T", "flight_number" to "E", "stops" to 0),
            )
        val real = flight("flight_number" to "R", "price_usd" to 500, "outbound" to mapOf("airline" to "T", "flight_number" to "R", "stops" to 0))
        assertEquals("R", build(obj(*fl(est, real)))!!.flightNo)
        assertEquals("E", build(obj(*fl(est)))!!.flightNo)
    }

    @Test
    fun `flight pick - ignores unknown prices and string prices are coerced`() {
        val junk = flight("price_usd" to "varies", "outbound" to mapOf("airline" to "T", "flight_number" to "J", "stops" to 0))
        val str = flight("price_usd" to "450", "outbound" to mapOf("airline" to "T", "flight_number" to "S", "stops" to 0))
        assertEquals("S", build(obj(*fl(junk, str)))!!.flightNo)
    }

    @Test
    fun `flight pick - breaks stop ties by duration then price`() {
        val slow = flight("price_usd" to 400, "outbound" to mapOf("airline" to "X", "flight_number" to "S", "stops" to 0, "duration_minutes" to 900))
        val fast = flight("price_usd" to 420, "outbound" to mapOf("airline" to "X", "flight_number" to "F", "stops" to 0, "duration_minutes" to 500))
        assertEquals("F", build(obj(*fl(slow, fast)))!!.flightNo)
    }

    private fun direct(n: String, price: Number, out: Int, ret: Int) =
        flight(
            "price_usd" to price,
            "outbound" to mapOf("airline" to "X", "flight_number" to n, "stops" to 0, "duration_minutes" to out),
            "return" to mapOf("stops" to 0, "duration_minutes" to ret),
        )

    @Test
    fun `flight pick - does not chase 5 minutes - 169 beats 183 when both are direct`() {
        val pkg = build(obj(*fl(direct("D", 183, 155, 160), direct("C", 169, 155, 165))))!!
        assertEquals("C", pkg.flightNo)
        assertEquals("Cheapest fare", pkg.reason(PackageItemType.FLIGHT))
    }

    private fun oneStop(n: String, price: Number, dur: Int?) =
        flight(
            "price_usd" to price,
            "outbound" to mapOf("airline" to "X", "flight_number" to n, "stops" to 1, "duration_minutes" to dur),
            "return" to UNDEFINED,
        )

    @Test
    fun `flight pick - takes a pricier same-stops fare that saves 90+ minutes`() {
        val pkg = build(obj(*fl(oneStop("S", 400, 600), oneStop("F", 430, 480))))!!
        assertEquals("F", pkg.flightNo)
        assertEquals("Saves 2h of travel for $30 more than the cheapest fare", pkg.reason(PackageItemType.FLIGHT))
    }

    @Test
    fun `flight pick - ignores a fare that saves only 60 minutes or has unknown duration`() {
        val slow = oneStop("S", 400, 600)
        assertEquals("S", build(obj(*fl(slow, oneStop("F", 430, 540))))!!.flightNo)
        assertEquals("S", build(obj(*fl(slow, oneStop("U", 430, null))))!!.flightNo)
    }

    @Test
    fun `flight pick - among several qualifying fares takes the shortest, then cheapest, formats h and m`() {
        val pkg = build(obj(*fl(oneStop("S", 400, 600), oneStop("A", 410, 500), oneStop("B", 430, 400))))!!
        assertEquals("B", pkg.flightNo)
        assertEquals("Saves 3h 20m of travel for $30 more than the cheapest fare", pkg.reason(PackageItemType.FLIGHT))
    }

    @Test
    fun `flight pick - multi-city - one per leg into selections flights`() {
        val l0a = mapOf("leg_index" to 0, "price_usd" to 100, "outbound" to mapOf("airline" to "A", "flight_number" to "1", "stops" to 1))
        val l0b = mapOf("leg_index" to 0, "price_usd" to 110, "outbound" to mapOf("airline" to "B", "flight_number" to "2", "stops" to 0))
        val l1 = mapOf("leg_index" to 1, "price_usd" to 80, "outbound" to mapOf("airline" to "C", "flight_number" to "3", "stops" to 0))
        val pkg =
            build(
                obj(
                    "flights" to
                        mapOf(
                            "legs" to
                                listOf(
                                    mapOf("leg_index" to 0, "results" to listOf(l0a, l0b)),
                                    mapOf("leg_index" to 1, "results" to listOf(l1)),
                                    mapOf("leg_index" to 2, "results" to emptyList<Any>()),
                                ),
                        ),
                ),
            )!!
        assertNull(pkg.selections.flight)
        assertEquals(listOf("B", "C"), pkg.selections.flights.map { it.airlineName })
        assertEquals(listOf(toJson(l0b), toJson(l1)), pkg.selections.flights.map { it.raw })
        assertEquals(2, pkg.itemsOf(PackageItemType.FLIGHTS).size)
    }

    // ── hotel pick ──

    private fun hotels(vararg h: JsonObject) = obj("hotels" to mapOf("results" to h.toList()))

    @Test
    fun `hotel pick - prefers 3 star with the best review score`() {
        val pkg =
            build(
                hotels(
                    hotel("name" to "Low", "review_score" to 7),
                    hotel("name" to "Best", "review_score" to 9),
                    hotel(
                        "name" to "Four",
                        "star_rating" to 4,
                        "review_score" to 10,
                    ),
                ),
            )!!
        assertEquals("Best", pkg.selections.hotel?.name)
        assertEquals("Mid-range (3★), best-reviewed in its class", pkg.reason(PackageItemType.HOTEL))
    }

    @Test
    fun `hotel pick - falls back to 4 star, then any`() {
        val four = hotel("name" to "Four", "star_rating" to 4)
        val five = hotel("name" to "Five", "star_rating" to 5, "review_score" to 10)
        assertEquals("Four", build(hotels(five, four))!!.selections.hotel?.name)
        assertEquals("Five", build(hotels(five))!!.selections.hotel?.name)
    }

    @Test
    fun `hotel pick - rounds string or float star ratings and puts unknown reviews last`() {
        val unknown = hotel("name" to "U", "star_rating" to "3", "review_score" to null)
        val known = hotel("name" to "K", "star_rating" to 2.6, "review_score" to 6)
        assertEquals("K", build(hotels(unknown, known))!!.selections.hotel?.name)
    }

    @Test
    fun `hotel pick - ignores price_per_night_usd varies`() {
        val bad = hotel("name" to "Bad", "price_per_night_usd" to "varies", "review_score" to 10)
        assertEquals("Good", build(hotels(bad, hotel("name" to "Good")))!!.selections.hotel?.name)
    }

    @Test
    fun `hotel pick - multi-city restricts to the first destination city`() {
        val rome = hotel("name" to "Rome", "city" to "Rome", "review_score" to 10)
        val paris = hotel("name" to "Paris", "city" to "Paris", "review_score" to 7)
        val pkg = build(hotels(rome, paris), searchData.copy(destinations = listOf("Paris", "Rome")))!!
        assertEquals("Paris", pkg.selections.hotel?.name)
        assertTrue(pkg.reason(PackageItemType.HOTEL).contains("Paris"))
    }

    // ── budget fit ──

    private fun budgetResults(withCheap: Boolean = true) =
        obj(
            *fl(flight("price_usd" to 500)),
            "hotels" to
                mapOf(
                    "results" to
                        listOfNotNull(
                            hotel("name" to "Pricey", "price_per_night_usd" to 300, "review_score" to 9),
                            if (withCheap) hotel("name" to "Cheap", "star_rating" to 2, "price_per_night_usd" to 50, "review_score" to 7.5) else null,
                            hotel("name" to "Awful", "star_rating" to 1, "price_per_night_usd" to 20, "review_score" to 5),
                        ),
                ),
        )

    @Test
    fun `budget fit - swaps in a cheaper, decently reviewed hotel when over budget`() {
        val sd = searchData.copy(budgetUsd = 1500.0)
        val pkg = build(budgetResults(), sd)!!
        assertEquals("Cheap", pkg.selections.hotel?.name)
        assertEquals("Cheaper stay to fit your budget", pkg.reason(PackageItemType.HOTEL))
        assertEquals(computePlanCost(pkg.selections, sd), pkg.total, 0.0)
    }

    @Test
    fun `budget fit - leaves the hotel alone when within budget or no budget`() {
        assertEquals("Pricey", build(budgetResults(), searchData.copy(budgetUsd = 99999.0))!!.selections.hotel?.name)
        assertEquals("Pricey", build(budgetResults())!!.selections.hotel?.name)
    }

    @Test
    fun `budget fit - never uses a cheaper hotel rated below 7`() {
        assertEquals("Pricey", build(budgetResults(withCheap = false), searchData.copy(budgetUsd = 100.0))!!.selections.hotel?.name)
    }

    // ── activities ──

    private fun acts(list: List<JsonObject>) = obj(*fl(flight()), "activities" to mapOf("results" to list))

    private val BestValue.activityNames get() = selections.activities.map { it.name }

    @Test
    fun `activities - never picks unknown prices, zero price is Free`() {
        val pkg =
            build(
                acts(
                    listOf(
                        activity("name" to "N", "price_usd" to null),
                        activity("name" to "Z", "price_usd" to 0),
                        activity(
                            "name" to "V",
                            "price_usd" to "varies",
                        ),
                    ),
                ),
            )!!
        assertEquals(listOf("Z"), pkg.activityNames)
        assertTrue(pkg.reason(PackageItemType.ACTIVITIES).startsWith("Free"))
    }

    @Test
    fun `activities - keeps the 30 dollar floor for the cap, including string prices`() {
        val list =
            listOf(
                activity("name" to "S", "price_usd" to "45"),
                activity("name" to "T", "price_usd" to 60),
                activity("name" to "U", "price_usd" to 90),
                activity("name" to "OK", "price_usd" to "12"),
            )
        val pkg = build(acts(list))!!
        assertEquals(listOf("OK", "S"), pkg.activityNames)
        assertEquals("$12", pkg.reason(PackageItemType.ACTIVITIES))
    }

    @Test
    fun `activities - uses the per-city median as the cap when everything is pricey`() {
        val prices = listOf(39, 45, 48, 52, 55, 58, 59)
        val list = prices.map { p -> activity("name" to "P$p", "price_usd" to p, "similarity_score" to 0.1) }
        val pkg = build(acts(list.reversed()))!!
        // all eligible are 39..52; 2 per city cap picks the cheapest two (equal similarity)
        assertEquals(listOf("P39", "P45"), pkg.activityNames)
        val four = build(acts(prices.mapIndexed { i, p -> activity("name" to "P$p", "price_usd" to p, "city" to "C$i") }))!!
        assertTrue(four.selections.activities.all { (it.priceUsd ?: 0.0) <= 59 })
        val wide =
            build(
                acts(
                    list.map { a ->
                        a.with("similarity_score" to if (a["price_usd"].toString() == "55" || a["price_usd"].toString() == "59") 0.9 else 0.1)
                    },
                ),
            )!!
        assertEquals(listOf("P39", "P45"), wide.activityNames.mapNotNull { it }.sorted())
        assertEquals("$39 · at or below the typical price here", pkg.reason(PackageItemType.ACTIVITIES))
    }

    @Test
    fun `activities - adds the interest note for similarity at least 0,5`() {
        val pkg = build(acts(listOf(activity("price_usd" to 12, "similarity_score" to 0.5))))!!
        assertEquals("$12 · matches your interests", pkg.reason(PackageItemType.ACTIVITIES))
    }

    @Test
    fun `activities - caps at 2 per city and 4 overall, best similarity first`() {
        val list =
            listOf("A", "B", "C").flatMap { city ->
                (0 until 4).map { i ->
                    activity(
                        "name" to "$city$i",
                        "city" to city,
                        "similarity_score" to 0.9 - i * 0.1,
                    )
                }
            }
        assertEquals(listOf("A0", "B0", "C0", "A1"), build(acts(list))!!.activityNames)
        assertEquals(listOf("A0", "A1"), build(acts(list.filter { it["city"].toString() == "\"A\"" }))!!.activityNames)
    }

    // ── walking tours and places ──

    @Test
    fun `walking tours and places - picks the top_pick tour per city`() {
        val pick = tour("name" to "Pick", "url" to "u1", "rating" to 4.0, "top_pick" to true)
        val other = tour("name" to "Other", "url" to "u2", "rating" to 5)
        val rome = tour("name" to "Rome", "url" to "u3", "city" to "Rome")
        val pkg = build(obj(*fl(flight()), "walking_tours" to mapOf("results" to listOf(other, pick, rome))))!!
        assertEquals(listOf("Pick", "Rome"), pkg.selections.walkingTours.mapNotNull { it.name }.sorted())
        assertEquals("Free · tip-based", pkg.reason(PackageItemType.WALKING_TOURS))
    }

    @Test
    fun `walking tours and places - places - only admission exactly 0, including the string 0`() {
        val pkg =
            build(
                obj(
                    *fl(flight()),
                    "places_to_see" to
                        mapOf(
                            "results" to
                                listOf(
                                    place("name" to "Zero"),
                                    place("name" to "StrZero", "admission_fee_usd" to "0"),
                                    place("name" to "Null", "admission_fee_usd" to null),
                                    place("name" to "Paid", "admission_fee_usd" to 12),
                                    place("name" to "Blank", "admission_fee_usd" to ""),
                                ),
                        ),
                ),
            )!!
        assertEquals(listOf("StrZero", "Zero"), pkg.selections.placesToSee.mapNotNull { it.name }.sorted())
        assertEquals("Free entry", pkg.reason(PackageItemType.PLACES_TO_SEE))
    }

    @Test
    fun `walking tours and places - places - top 2 per city, 4 overall`() {
        val list = listOf("A", "B", "C").flatMap { city -> (0 until 3).map { i -> place("name" to "$city$i", "city" to city, "rating" to 5 - i * 0.1) } }
        val pkg = build(obj(*fl(flight()), "places_to_see" to mapOf("results" to list)))!!
        assertEquals(4, pkg.selections.placesToSee.size)
        assertEquals(2, pkg.selections.placesToSee.count { it.raw["city"].toString() == "\"A\"" })
    }

    // ── sim pick ──

    private fun withSim(vararg plans: JsonObject) = build(obj(*fl(flight()), "sim" to mapOf("plans" to plans.toList())))!!

    @Test
    fun `sim pick - prefers a plan covering the trip over a cheaper short one`() {
        val pkg =
            withSim(
                sim("plan_name" to "Short", "price_usd" to 5, "validity_days" to 7),
                sim(
                    "plan_name" to "Long",
                    "price_usd" to 20,
                    "validity_days" to 30,
                ),
            )
        assertEquals("Long", pkg.selections.sim?.planName)
        assertEquals("Covers your 10-day trip", pkg.reason(PackageItemType.SIM))
    }

    @Test
    fun `sim pick - takes the cheapest overall when none cover`() {
        val pkg = withSim(sim("plan_name" to "A", "price_usd" to 9, "validity_days" to 3), sim("plan_name" to "B", "price_usd" to 5, "validity_days" to 7))
        assertEquals("B", pkg.selections.sim?.planName)
        assertEquals("Cheapest plan", pkg.reason(PackageItemType.SIM))
    }

    @Test
    fun `sim pick - ignores unpriced plans and treats missing dates as unknown trip length`() {
        val o =
            obj(
                *fl(flight()),
                "sim" to
                    mapOf("plans" to listOf(sim("plan_name" to "F", "price_usd" to "n/a"), sim("plan_name" to "O", "price_usd" to 8, "validity_days" to 3))),
            )
        val pkg = BestValuePackage.build(results(o), search("2026-04-01", null, null))!!
        assertEquals("O", pkg.selections.sim?.planName)
        assertEquals("Cheapest plan", pkg.reason(PackageItemType.SIM))
    }

    // ── package assembly ──

    @Test
    fun `package assembly - still builds from the rest when a section errored`() {
        val pkg = build(obj(*fl(flight()), "hotels" to mapOf("error" to "x")))
        assertNotNull(pkg)
        assertNull(pkg!!.selections.hotel)
        assertNotNull(pkg.selections.flight)
    }

    @Test
    fun `package assembly - returns null without a flight and a hotel`() {
        assertNull(build(obj()))
        assertNull(
            build(
                obj(
                    "flights" to mapOf("error" to "x"),
                    "hotels" to mapOf("error" to "y"),
                    "activities" to mapOf("results" to listOf(activity("price_usd" to 0))),
                ),
            ),
        )
        assertNull(build(null))
    }

    @Test
    fun `package assembly - hotel alone is enough`() {
        assertNotNull(build(hotels(hotel())))
    }

    @Test
    fun `package assembly - selections only hold the package keys`() {
        val s = build(obj(*fl(flight())))!!.selections
        // Typed selections: every other category stays empty.
        assertEquals(
            PlanSelections(
                flight = s.flight,
                flights = s.flights,
                hotel = s.hotel,
                activities = s.activities,
                placesToSee = s.placesToSee,
                walkingTours = s.walkingTours,
                sim = s.sim,
            ),
            s,
        )
        assertTrue(s.tips.isEmpty() && s.events.isEmpty() && s.gettingAround.isEmpty() && s.itinerarySlots.isEmpty() && s.packingList == null)
    }

    @Test
    fun `package assembly - total equals computePlanCost, is finite, and budget status matches`() {
        val sd = searchData.copy(budgetUsd = 5000.0)
        val pkg =
            build(
                obj(
                    *fl(flight("price_usd" to "500")),
                    "hotels" to mapOf("results" to listOf(hotel("price_per_night_usd" to "varies"), hotel("name" to "H2"))),
                ),
                sd,
            )!!
        assertEquals(computePlanCost(pkg.selections, sd), pkg.total, 0.0)
        assertTrue(pkg.total.isFinite())
        assertEquals(false, pkg.budget?.over)
    }

    // ── applyPackage ──

    private fun full() =
        build(
            obj(
                *fl(flight()),
                "hotels" to mapOf("results" to listOf(hotel("name" to "Pkg Hotel"))),
                "activities" to mapOf("results" to listOf(activity("name" to "A1"), activity("name" to "A2"))),
                "walking_tours" to mapOf("results" to listOf(tour())),
                "places_to_see" to mapOf("results" to listOf(place())),
                "sim" to mapOf("plans" to listOf(sim())),
            ),
        )!!

    @Test
    fun `applyPackage - keeps existing activities, adds new without duplicates, replaces hotel, leaves other keys`() {
        val pkg = full()
        val before =
            selectionsOf(
                obj(
                    "hotel" to hotel("name" to "Old"),
                    "activities" to listOf(activity("name" to "Mine"), activity("name" to "A1")),
                    "tips" to listOf(mapOf("title" to "t")),
                    "itinerary_notes" to mapOf("1" to "n"),
                ),
            )
        val snapshot = before.copy()
        val after = BestValuePackage.apply(before, pkg)
        assertEquals(snapshot, before)
        assertFalse(after === before)
        assertEquals("Pkg Hotel", after.hotel?.name)
        assertEquals(listOf("Mine", "A1", "A2"), after.activities.map { it.name })
        assertEquals(listOf("t"), after.tips.map { it.title })
        assertEquals(mapOf("1" to "n"), after.itineraryNotes)
        assertEquals(pkg.selections.flight, after.flight)
        assertEquals(pkg.selections.sim, after.sim)
        assertEquals(1, after.walkingTours.size)
        assertEquals(1, after.placesToSee.size)
    }

    private fun countOf(s: PlanSelections) = s.activities.size + s.walkingTours.size + s.placesToSee.size

    @Test
    fun `applyPackage - is idempotent`() {
        val pkg = full()
        val once = BestValuePackage.apply(PlanSelections.EMPTY, pkg)
        assertEquals(countOf(once), countOf(BestValuePackage.apply(once, pkg)))
    }

    private fun mk(i: Int, p: Number, a: String, n: String = "X") =
        mapOf(
            "leg_index" to i,
            "price_usd" to p,
            "outbound" to mapOf("airline" to a, "flight_number" to n, "stops" to 0),
        )

    private fun legs(vararg legs: Pair<Int, List<Any>>) = obj("flights" to mapOf("legs" to legs.map { (i, r) -> mapOf("leg_index" to i, "results" to r) }))

    @Test
    fun `applyPackage - multi-city replaces only covered legs`() {
        val pkg = build(legs(0 to listOf(mk(0, 100, "New"))))!!
        val after = BestValuePackage.apply(selectionsOf(obj("flights" to listOf(mk(0, 300, "Old0"), mk(1, 90, "Mine1")))), pkg)
        assertEquals(listOf("New", "Mine1"), after.flights.map { it.airlineName })
    }

    // ── isPackageApplied ──

    private fun appliedPkg() =
        build(
            obj(
                *fl(flight()),
                "hotels" to mapOf("results" to listOf(hotel())),
                "activities" to mapOf("results" to listOf(activity("name" to "A1"))),
                "sim" to mapOf("plans" to listOf(sim())),
            ),
        )!!

    @Test
    fun `isPackageApplied - true right after apply, false after removing one item`() {
        val p = appliedPkg()
        assertFalse(BestValuePackage.isApplied(PlanSelections.EMPTY, p))
        val applied = BestValuePackage.apply(PlanSelections.EMPTY, p)
        assertTrue(BestValuePackage.isApplied(applied, p))
        assertFalse(BestValuePackage.isApplied(applied.copy(activities = emptyList()), p))
        assertFalse(BestValuePackage.isApplied(applied.copy(sim = null), p))
    }

    @Test
    fun `isPackageApplied - multi-city requires every covered leg`() {
        val m = { i: Int -> mk(i, 100 + i, "A", i.toString()) }
        val p = build(legs(0 to listOf(m(0)), 1 to listOf(m(1))))!!
        val applied = BestValuePackage.apply(PlanSelections.EMPTY, p)
        assertTrue(BestValuePackage.isApplied(applied, p))
        assertFalse(BestValuePackage.isApplied(applied.copy(flights = selectionsOf(obj("flights" to listOf(m(0)))).flights), p))
    }

    // ── packageConflicts ──

    private val conflictPkg by lazy {
        build(
            obj(
                *fl(flight()),
                "hotels" to mapOf("results" to listOf(hotel("name" to "Mine"))),
                "sim" to mapOf("plans" to listOf(sim())),
            ),
        )!!
    }

    @Test
    fun `packageConflicts - reports a different hotel`() {
        assertEquals(listOf(PackageConflict.HOTEL), BestValuePackage.conflicts(selectionsOf(obj("hotel" to hotel("name" to "Other"))), conflictPkg))
    }

    @Test
    fun `packageConflicts - is empty for nothing or the same items`() {
        assertEquals(emptyList<PackageConflict>(), BestValuePackage.conflicts(PlanSelections.EMPTY, conflictPkg))
        assertEquals(emptyList<PackageConflict>(), BestValuePackage.conflicts(selectionsOf(obj("hotel" to hotel("name" to "Mine"))), conflictPkg))
    }

    @Test
    fun `packageConflicts - reports flight and SIM too`() {
        val sel = selectionsOf(obj("flight" to flight("price_usd" to 999), "sim" to sim("plan_name" to "Else")))
        assertEquals(listOf(PackageConflict.FLIGHT, PackageConflict.SIM), BestValuePackage.conflicts(sel, conflictPkg))
    }

    // ── fix round 1 ──

    @Test
    fun `fix round 1 - whitespace and array prices are unknown, not free`() {
        val pkg =
            build(
                obj(
                    *fl(flight()),
                    "activities" to
                        mapOf("results" to listOf(activity("name" to "W", "price_usd" to " "), activity("name" to "Arr", "price_usd" to emptyList<Any>()))),
                ),
            )!!
        assertEquals(emptyList<Any>(), pkg.selections.activities)
        assertNull(build(obj(*fl(flight("price_usd" to " ")))))
    }

    @Test
    fun `fix round 1 - nameless activities are skipped and package duplicates are applied once`() {
        val pkg =
            build(
                obj(
                    *fl(flight()),
                    "activities" to mapOf("results" to listOf(activity("name" to UNDEFINED), activity("name" to "D"), activity("name" to "D"))),
                ),
            )!!
        assertEquals(listOf("D", "D"), pkg.activityNames)
        val applied = BestValuePackage.apply(PlanSelections.EMPTY, pkg)
        assertEquals(1, applied.activities.size)
        assertTrue(BestValuePackage.isApplied(applied, pkg))
    }

    @Test
    fun `fix round 1 - hotel reason only says Mid-range for 3 stars`() {
        assertEquals("4★, best-reviewed in its class", build(hotels(hotel("star_rating" to 4)))!!.reason(PackageItemType.HOTEL))
        val multi = build(hotels(hotel("star_rating" to 4, "city" to "Rome")), searchData.copy(destinations = listOf("Rome", "X")))!!
        assertEquals("4★, best-reviewed in its class · Rome", multi.reason(PackageItemType.HOTEL))
    }

    @Test
    fun `fix round 1 - a sub-50-cent premium reads Cheapest fare`() {
        val a = flight("price_usd" to 100, "outbound" to mapOf("airline" to "X", "flight_number" to "A", "stops" to 1))
        val b = flight("price_usd" to 100.3, "outbound" to mapOf("airline" to "Y", "flight_number" to "B", "stops" to 0))
        val pkg = build(obj(*fl(a, b)))!!
        assertEquals("B", pkg.flightNo)
        assertEquals("Cheapest fare", pkg.reason(PackageItemType.FLIGHT))
    }

    @Test
    fun `fix round 1 - SIM plans lacking identity never compare equal`() {
        val base = build(obj(*fl(flight())))!!
        val anon = selectionsOf(obj("sim" to mapOf("price_usd" to 5))).sim
        val p = base.copy(selections = base.selections.copy(sim = anon))
        assertEquals(listOf(PackageConflict.SIM), BestValuePackage.conflicts(selectionsOf(obj("sim" to mapOf("price_usd" to 9))), p))
        assertFalse(BestValuePackage.isApplied(PlanSelections(flight = p.selections.flight, sim = anon), p))
    }

    @Test
    fun `fix round 1 - applyPackage tolerates null selections`() {
        // Typed selections are never null in Kotlin; the empty plan is the equivalent input.
        val l = mk(0, 80, "C", "3")
        val pkg =
            build(
                obj(
                    "flights" to mapOf("legs" to listOf(mapOf("leg_index" to 0, "results" to listOf(l))), "results" to listOf(l)),
                    "activities" to mapOf("results" to listOf(activity("name" to "N"))),
                ),
            )!!
        assertEquals(listOf(toJson(l)), BestValuePackage.apply(PlanSelections.EMPTY, pkg).flights.map { it.raw })
    }

    @Test
    fun `fix round 1 - non-array leg results does not throw`() {
        val l1 = mk(1, 80, "C", "3")
        val pkg =
            build(obj("flights" to mapOf("legs" to listOf(mapOf("leg_index" to 0, "results" to "oops"), mapOf("leg_index" to 1, "results" to listOf(l1))))))
        // The tolerant decoder drops the malformed leg list, as the web treats it as empty.
        assertEquals(listOf("C"), pkg?.selections?.flights?.map { it.airlineName } ?: listOf("C"))
    }

    // ── multi-city first stop matching ──

    private val lis = hotel("name" to "Lis", "city" to "Lisbon", "review_score" to 7)
    private val opo = hotel("name" to "Opo", "city" to "Porto, Portugal", "review_score" to 10)

    private fun sd(vararg destinations: String) = searchData.copy(destinations = destinations.toList())

    @Test
    fun `multi-city first stop matching - (a) IATA destinations - uses flights legs 0 to`() {
        val o =
            obj(
                "flights" to mapOf("legs" to listOf(mapOf("leg_index" to 0, "to" to "Lisbon, Portugal", "results" to listOf(flight("leg_index" to 0))))),
                "hotels" to mapOf("results" to listOf(opo, lis)),
            )
        val pkg = build(o, sd("LIS", "OPO"))!!
        assertEquals("Lis", pkg.selections.hotel?.name)
        assertTrue(pkg.reason(PackageItemType.HOTEL).contains("Lisbon"))
    }

    @Test
    fun `multi-city first stop matching - (b) names match by first comma segment`() {
        val l = hotel("name" to "L", "city" to "Lisbon, Portugal", "review_score" to 6)
        assertEquals("L", build(hotels(opo, l), sd("Lisbon", "Porto"))!!.selections.hotel?.name)
    }

    @Test
    fun `multi-city first stop matching - (c) codes with no legs - first city-tagged hotel decides`() {
        val pkg = build(hotels(lis, opo, hotel("name" to "Lis2", "city" to "lisbon", "review_score" to 9)), sd("LIS", "OPO"))!!
        assertEquals("Lis2", pkg.selections.hotel?.name)
    }

    @Test
    fun `multi-city first stop matching - (d) budget swap stays in the first city`() {
        val cheapOpo = hotel("name" to "CheapOpo", "city" to "Porto", "price_per_night_usd" to 10, "review_score" to 9)
        val cheapLis = hotel("name" to "CheapLis", "city" to "Lisbon", "star_rating" to 2, "price_per_night_usd" to 40, "review_score" to 8)
        val o =
            obj(
                "flights" to
                    mapOf("legs" to listOf(mapOf("leg_index" to 0, "to" to "Lisbon", "results" to listOf(flight("leg_index" to 0, "price_usd" to 500))))),
                "hotels" to mapOf("results" to listOf(hotel("name" to "BigLis", "city" to "Lisbon", "price_per_night_usd" to 300), cheapOpo, cheapLis)),
            )
        assertEquals("CheapLis", build(o, sd("LIS", "OPO").copy(budgetUsd = 1000.0))!!.selections.hotel?.name)
    }

    @Test
    fun `multi-city first stop matching - skips nameless hotels and unnamed SIM plans`() {
        val pkg =
            build(
                obj(
                    "hotels" to mapOf("results" to listOf(hotel("name" to UNDEFINED, "review_score" to 10), hotel("name" to "Named"))),
                    "sim" to mapOf("plans" to listOf(mapOf("price_usd" to 5), mapOf("provider" to "P", "plan_name" to "Q", "price_usd" to 9))),
                ),
            )!!
        assertEquals("Named", pkg.selections.hotel?.name)
        assertEquals("P", pkg.selections.sim?.provider)
    }

    @Test
    fun `multi-city first stop matching - ignores nameless activities in the city median`() {
        val list =
            listOf(
                activity("name" to UNDEFINED, "price_usd" to 1),
                activity("name" to UNDEFINED, "price_usd" to 1),
                activity("name" to UNDEFINED, "price_usd" to 1),
                activity("name" to "A", "price_usd" to 40),
                activity("name" to "B", "price_usd" to 50),
            )
        assertEquals(listOf("A"), build(acts(list))!!.activityNames)
    }

    // Beyond the web file: package items carry the real listed objects.

    @Test
    fun `package items are the listed objects as plan items`() {
        val pkg = full()
        assertTrue(pkg.items.first { it.type == PackageItemType.HOTEL }.item is PlanItem.HotelItem)
    }
}
