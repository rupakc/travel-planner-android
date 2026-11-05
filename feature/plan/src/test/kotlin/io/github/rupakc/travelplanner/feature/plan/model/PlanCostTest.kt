package io.github.rupakc.travelplanner.feature.plan.model

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.SimPlan
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as the web's `planHelpers.test.js` (cost, budget, name). */
class PlanCostTest {
    private fun leg(index: Int, price: Double?) = Flight(legIndex = index, priceUsd = price, outbound = FlightLeg(airline = "TestAir", flightNumber = "TA1"))

    private val fullPlan =
        PlanSelections(
            flight = Flight(priceUsd = 900.0),
            flights = listOf(leg(0, 150.0), leg(1, 50.0)),
            hotel = Hotel(totalPriceUsd = 1400.0),
            activities = listOf(Activity(priceUsd = 60.0), Activity(priceUsd = 40.0), Activity(name = "Free walk")),
            sim = SimPlan(priceUsd = 25.0),
        )

    @Test
    fun `sums all leg prices`() {
        assertEquals(300.0, PlanCost.total(PlanSelections(flights = listOf(leg(0, 100.0), leg(1, 90.0), leg(2, 110.0))), null, null), 0.0)
    }

    @Test
    fun `ignores legs without prices and combines with hotel`() {
        val sel = PlanSelections(flights = listOf(leg(0, 100.0), leg(1, null)), hotel = Hotel(totalPriceUsd = 500.0))
        assertEquals(600.0, PlanCost.total(sel, null, null), 0.0)
    }

    @Test
    fun `splits the plan into categories, biggest cost first, adding up to the total`() {
        val parts = PlanCost.breakdown(fullPlan, null, null)
        assertEquals(
            listOf(
                CostPart(CostCategory.HOTEL, 1400.0),
                CostPart(CostCategory.FLIGHTS, 1100.0),
                CostPart(CostCategory.ACTIVITIES, 100.0),
                CostPart(CostCategory.SIM, 25.0),
            ),
            parts,
        )
        assertEquals(parts.sumOf { it.amount }, PlanCost.total(fullPlan, null, null), 0.0)
    }

    @Test
    fun `leaves out categories with nothing priced and is empty for an empty plan`() {
        val sel = PlanSelections(hotel = Hotel(totalPriceUsd = 300.0), activities = listOf(Activity(name = "Free walk")))
        assertEquals(listOf(CostPart(CostCategory.HOTEL, 300.0)), PlanCost.breakdown(sel, null, null))
        assertTrue(PlanCost.breakdown(PlanSelections(), null, null).isEmpty())
    }

    @Test
    fun `prices a nightly-rate hotel by the trip length, else 7 nights`() {
        val sel = PlanSelections(hotel = Hotel(pricePerNightUsd = 100.0))
        assertEquals(400.0, PlanCost.breakdown(sel, "2027-04-01", "2027-04-05")[0].amount, 0.0)
        assertEquals(700.0, PlanCost.breakdown(sel, null, null)[0].amount, 0.0)
    }

    @Test
    fun `accepts numeric strings and ignores prices that are not numbers`() {
        val sel =
            TravelJson.decodeFromString(
                PlanSelections.serializer(),
                """{"activities":[{"price_usd":"30"},{"price_usd":"varies"}],"sim":{"price_usd":"n/a"}}""",
            )
        assertEquals(listOf(CostPart(CostCategory.ACTIVITIES, 30.0)), PlanCost.breakdown(sel, null, null))
    }

    @Test
    fun `budget status under, over and none`() {
        assertEquals(BudgetStatus(over = false, amount = 500), PlanCost.budgetStatus(2500.0, 3000.0))
        assertEquals(BudgetStatus(over = true, amount = 250), PlanCost.budgetStatus(3250.4, 3000.0))
        assertNull(PlanCost.budgetStatus(0.0, 3000.0))
        assertNull(PlanCost.budgetStatus(100.0, null))
    }

    @Test
    fun `generated name uses the first interest's theme, else the season`() {
        val name = PlanCost.generateName("Tokyo", listOf("food"), "2027-04-01", Random(1))
        assertTrue(name, Regex("^The (Foodie|Culinary|Gourmet|Tasty) Tokyo \\w+$").matches(name))
        val spring = PlanCost.generateName("Lisbon", emptyList(), "2027-04-01", Random(1))
        assertTrue(spring, Regex("^The (Blossom|Fresh|Springtime) Lisbon \\w+$").matches(spring))
        assertTrue(PlanCost.generateName(null, emptyList(), null, Random(1)).contains(" Mystery "))
    }
}
