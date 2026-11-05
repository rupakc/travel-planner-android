package io.github.rupakc.travelplanner.core.domain.plan

import io.github.rupakc.travelplanner.core.domain.results.obj
import io.github.rupakc.travelplanner.core.domain.results.sameFlight
import io.github.rupakc.travelplanner.core.domain.results.search
import io.github.rupakc.travelplanner.core.domain.results.selectionsOf
import io.github.rupakc.travelplanner.core.domain.results.with
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.core.model.plan.SelectionKeys
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Every case of `frontend/src/utils/planHelpers.test.js` (names: "describe - it"), plus budget status and plan name. */
class PlanHelpersTest {
    private fun legFlight(legIndex: Int, price: Any?, airline: String = "TestAir", flightNumber: String = "TA1") =
        obj("leg_index" to legIndex, "price_usd" to price, "outbound" to mapOf("airline" to airline, "flight_number" to flightNumber, "stops" to 0))

    private fun flights(vararg f: Any) = selectionsOf(obj("flights" to f.toList())).flights

    @Test
    fun `sameFlight - matches identical per-leg flights`() {
        val (a, b) = flights(legFlight(0, 100), legFlight(0, 100))
        assertTrue(sameFlight(a, b))
    }

    @Test
    fun `sameFlight - distinguishes legs, prices and airlines`() {
        val f = flights(legFlight(0, 100), legFlight(1, 100), legFlight(0, 200), legFlight(0, 100, "A"), legFlight(0, 100, "B"))
        assertFalse(sameFlight(f[0], f[1]))
        assertFalse(sameFlight(f[0], f[2]))
        assertFalse(sameFlight(f[3], f[4]))
    }

    @Test
    fun `sameFlight - handles flat legacy flights and null`() {
        val flat = obj("airline" to "X", "flight_number" to "1", "price_usd" to 50)
        val (a, b) = flights(flat, flat.with())
        assertTrue(sameFlight(a, b))
        assertFalse(sameFlight(null, a))
        assertFalse(sameFlight(a, null))
    }

    @Test
    fun `countSelections with multi-city flights - counts each selected leg`() {
        assertEquals(2, selectionsOf(obj("flights" to listOf(legFlight(0, 100), legFlight(1, 90)))).count)
    }

    @Test
    fun `countSelections with multi-city flights - EMPTY_SELECTIONS counts zero and includes flights key`() {
        assertEquals(emptyList<Any>(), PlanSelections.EMPTY.flights)
        assertEquals(0, PlanSelections.EMPTY.count)
        assertEquals(JsonArray(emptyList()), PlanSelectionsSerializer.toJson(PlanSelections.EMPTY)[SelectionKeys.FLIGHTS])
    }

    @Test
    fun `computePlanCost with multi-city flights - sums all leg prices`() {
        assertEquals(300.0, computePlanCost(selectionsOf(obj("flights" to listOf(legFlight(0, 100), legFlight(1, 90), legFlight(2, 110))))), 0.0)
    }

    @Test
    fun `computePlanCost with multi-city flights - ignores legs without prices and combines with hotel`() {
        val sel = selectionsOf(obj("flights" to listOf(legFlight(0, 100), mapOf("leg_index" to 1)), "hotel" to mapOf("total_price_usd" to 500)))
        assertEquals(600.0, computePlanCost(sel), 0.0)
    }

    private val fullPlan =
        selectionsOf(
            obj(
                "flight" to mapOf("price_usd" to 900),
                "flights" to listOf(legFlight(0, 150), legFlight(1, 50)),
                "hotel" to mapOf("total_price_usd" to 1400),
                "activities" to listOf(mapOf("price_usd" to 60), mapOf("price_usd" to 40), mapOf("name" to "Free walk")),
                "sim" to mapOf("price_usd" to 25),
            ),
        )

    @Test
    fun `computePlanCostBreakdown - splits the plan into categories, biggest cost first`() {
        assertEquals(
            listOf(
                CostPart(CostCategory.HOTEL, 1400.0),
                CostPart(CostCategory.FLIGHTS, 1100.0),
                CostPart(CostCategory.ACTIVITIES, 100.0),
                CostPart(CostCategory.SIM, 25.0),
            ),
            computePlanCostBreakdown(fullPlan),
        )
    }

    @Test
    fun `computePlanCostBreakdown - parts always add up to computePlanCost`() {
        assertEquals(computePlanCost(fullPlan), computePlanCostBreakdown(fullPlan).sumOf { it.amount }, 0.0)
    }

    @Test
    fun `computePlanCostBreakdown - leaves out categories with nothing priced`() {
        val sel = selectionsOf(obj("hotel" to mapOf("total_price_usd" to 300), "activities" to listOf(mapOf("name" to "Free walk"))))
        assertEquals(listOf(CostPart(CostCategory.HOTEL, 300.0)), computePlanCostBreakdown(sel))
    }

    @Test
    fun `computePlanCostBreakdown - is empty for an empty plan or missing selections`() {
        assertEquals(emptyList<CostPart>(), computePlanCostBreakdown(PlanSelections.EMPTY))
        assertEquals(emptyList<CostPart>(), computePlanCostBreakdown(null))
    }

    @Test
    fun `computePlanCostBreakdown - prices a nightly-rate hotel by the trip length`() {
        val sel = selectionsOf(obj("hotel" to mapOf("price_per_night_usd" to 100)))
        assertEquals(listOf(CostPart(CostCategory.HOTEL, 400.0)), computePlanCostBreakdown(sel, search("2027-04-01", "2027-04-05")))
    }

    @Test
    fun `computePlanCostBreakdown - assumes 7 nights for a nightly-rate hotel when dates are unknown`() {
        assertEquals(700.0, computePlanCostBreakdown(selectionsOf(obj("hotel" to mapOf("price_per_night_usd" to 100))))[0].amount, 0.0)
    }

    @Test
    fun `computePlanCostBreakdown - accepts numeric strings and ignores prices that are not numbers`() {
        val sel = selectionsOf(obj("activities" to listOf(mapOf("price_usd" to "30"), mapOf("price_usd" to "varies")), "sim" to mapOf("price_usd" to "n/a")))
        assertEquals(listOf(CostPart(CostCategory.ACTIVITIES, 30.0)), computePlanCostBreakdown(sel))
        assertEquals(30.0, computePlanCost(sel), 0.0)
    }

    @Test
    fun `walking tours selections - countSelections counts walking tours`() {
        val sel = selectionsOf(obj("walking_tours" to listOf(mapOf("url" to "https://a.example/1"), mapOf("url" to "https://a.example/2"))))
        assertEquals(2, sel.count)
    }

    @Test
    fun `walking tours selections - EMPTY_SELECTIONS has walking_tours empty`() {
        assertEquals(emptyList<Any>(), PlanSelections.EMPTY.walkingTours)
        assertEquals(JsonArray(emptyList()), PlanSelectionsSerializer.toJson(PlanSelections.EMPTY)[SelectionKeys.WALKING_TOURS])
    }

    // Beyond the web test file: getBudgetStatus and generatePlanName (same rules as planHelpers.js).

    @Test
    fun `getBudgetStatus - under, over and unknown`() {
        assertEquals(BudgetStatus(over = false, amount = 500), getBudgetStatus(2500.0, 3000.0))
        assertEquals(BudgetStatus(over = true, amount = 250), getBudgetStatus(3250.4, 3000.0))
        assertNull(getBudgetStatus(0.0, 3000.0))
        assertNull(getBudgetStatus(100.0, null))
    }

    @Test
    fun `generatePlanName - interest theme, else season, else generic`() {
        val name = generatePlanName("Lisbon", listOf("food"), random = Random(1))
        assertTrue(name, Regex("^The (Foodie|Culinary|Gourmet|Tasty) Lisbon \\w+$").matches(name))
        val winter = generatePlanName(null, emptyList(), "2026-12-24", Random(2))
        assertTrue(winter, Regex("^The (Frosty|Cozy|Snowbound) Mystery \\w+$").matches(winter))
    }
}
