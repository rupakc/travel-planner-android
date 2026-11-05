package io.github.rupakc.travelplanner.core.domain.plan

import io.github.rupakc.travelplanner.core.domain.results.sameFlight
import io.github.rupakc.travelplanner.core.domain.results.search
import io.github.rupakc.travelplanner.core.domain.results.selectionsOf
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from `frontend/src/utils/planHelpers.test.js`. */
class PlanCostTest {
    private fun leg(i: Int, price: Int?, airline: String = "TestAir", n: String = "TA1") =
        """{"leg_index":$i${price?.let { ",\"price_usd\":$it" }.orEmpty()},"outbound":{"airline":"$airline","flight_number":"$n","stops":0}}"""

    @Test
    fun `sameFlight matches identical legs and distinguishes leg, price and airline`() {
        val s = selectionsOf("""{"flights":[${leg(0, 100)},${leg(0, 100)},${leg(1, 100)},${leg(0, 200)},${leg(0, 100, "B")}]}""").flights
        assertTrue(sameFlight(s[0], s[1]))
        assertFalse(sameFlight(s[0], s[2]))
        assertFalse(sameFlight(s[0], s[3]))
        assertFalse(sameFlight(s[0], s[4]))
        assertFalse(sameFlight(null, s[0]))
        val flat =
            selectionsOf(
                """{"flights":[{"airline":"X","flight_number":"1","price_usd":50},{"airline":"X","flight_number":"1","price_usd":50}]}""",
            ).flights
        assertTrue(sameFlight(flat[0], flat[1]))
    }

    @Test
    fun `multi-city legs sum and combine with hotel`() {
        assertEquals(300.0, computePlanCost(selectionsOf("""{"flights":[${leg(0, 100)},${leg(1, 90)},${leg(2, 110)}]}""")), 0.0)
        assertEquals(600.0, computePlanCost(selectionsOf("""{"flights":[${leg(0, 100)},{"leg_index":1}],"hotel":{"total_price_usd":500}}""")), 0.0)
    }

    private val fullPlan =
        selectionsOf(
            """{"flight":{"price_usd":900},"flights":[${leg(0, 150)},${leg(1, 50)}],"hotel":{"total_price_usd":1400},
            "activities":[{"price_usd":60},{"price_usd":40},{"name":"Free walk"}],"sim":{"price_usd":25}}""",
        )

    @Test
    fun `breakdown is biggest first and adds up to the total`() {
        val parts = computePlanCostBreakdown(fullPlan)
        assertEquals(
            listOf(
                CostPart(CostCategory.HOTEL, 1400.0),
                CostPart(CostCategory.FLIGHTS, 1100.0),
                CostPart(CostCategory.ACTIVITIES, 100.0),
                CostPart(CostCategory.SIM, 25.0),
            ),
            parts,
        )
        assertEquals(computePlanCost(fullPlan), parts.sumOf { it.amount }, 0.0)
    }

    @Test
    fun `breakdown leaves out unpriced categories and handles empty plans`() {
        assertEquals(
            listOf(CostPart(CostCategory.HOTEL, 300.0)),
            computePlanCostBreakdown(selectionsOf("""{"hotel":{"total_price_usd":300},"activities":[{"name":"Free walk"}]}""")),
        )
        assertEquals(emptyList<CostPart>(), computePlanCostBreakdown(PlanSelections()))
        assertEquals(emptyList<CostPart>(), computePlanCostBreakdown(null))
    }

    @Test
    fun `nightly hotel priced by trip length, 7 nights when unknown`() {
        val sel = selectionsOf("""{"hotel":{"price_per_night_usd":100}}""")
        assertEquals(400.0, computePlanCostBreakdown(sel, search(departure = "2027-04-01", returnDate = "2027-04-05"))[0].amount, 0.0)
        assertEquals(700.0, computePlanCostBreakdown(sel)[0].amount, 0.0)
    }

    @Test
    fun `numeric strings count and junk prices do not`() {
        val sel = selectionsOf("""{"activities":[{"price_usd":"30"},{"price_usd":"varies"}],"sim":{"price_usd":"n/a"}}""")
        assertEquals(listOf(CostPart(CostCategory.ACTIVITIES, 30.0)), computePlanCostBreakdown(sel))
    }

    @Test
    fun `budget status`() {
        assertEquals(BudgetStatus(over = false, amount = 500), getBudgetStatus(2500.0, 3000.0))
        assertEquals(BudgetStatus(over = true, amount = 250), getBudgetStatus(3250.4, 3000.0))
        assertNull(getBudgetStatus(0.0, 3000.0))
        assertNull(getBudgetStatus(100.0, null))
    }

    @Test
    fun `plan name uses the interest theme, else the season`() {
        val name = generatePlanName("Lisbon", listOf("food"), random = Random(1))
        assertTrue(name, Regex("^The (Foodie|Culinary|Gourmet|Tasty) Lisbon \\w+$").matches(name))
        val winter = generatePlanName(null, emptyList(), "2026-12-24", Random(2))
        assertTrue(winter, Regex("^The (Frosty|Cozy|Snowbound) Mystery \\w+$").matches(winter))
    }
}
