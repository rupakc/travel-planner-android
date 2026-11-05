@file:Suppress("MaxLineLength", "ktlint:standard:max-line-length") // inline JSON fixtures

package io.github.rupakc.travelplanner.feature.results

import io.github.rupakc.travelplanner.core.domain.results.ActivityFit
import io.github.rupakc.travelplanner.core.domain.results.BudgetAllocation
import io.github.rupakc.travelplanner.core.domain.results.BudgetFit
import io.github.rupakc.travelplanner.core.domain.results.FitSet
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.feature.results.sections.FlightRow
import io.github.rupakc.travelplanner.feature.results.sections.HiddenRow
import io.github.rupakc.travelplanner.feature.results.sections.HotelRow
import io.github.rupakc.travelplanner.feature.results.sections.LegEmptyRow
import io.github.rupakc.travelplanner.feature.results.sections.LegHeaderRow
import io.github.rupakc.travelplanner.feature.results.sections.commerceRows
import io.github.rupakc.travelplanner.feature.results.ui.availabilityValid
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flat lazy rows of the commerce sections (I2) and the activity-date rule (I3). */
class CommerceRowsTest {
    private fun hotels(json: String) = SectionDecoder.decode(SectionId.HOTELS, TravelJson.parseToJsonElement(json).jsonObject) as HotelsData

    private fun flights(json: String) = SectionDecoder.decode(SectionId.FLIGHTS, TravelJson.parseToJsonElement(json).jsonObject) as FlightsData

    private val data =
        hotels(
            """{"results":[{"name":"Fits","price_per_night_usd":50},{"name":"Dear","price_per_night_usd":500},{"name":"Dear2","price_per_night_usd":600}]}""",
        )

    private fun budget(showOnlyFits: Boolean, cardsReact: Boolean = true) =
        BudgetUi(
            allocation = BudgetAllocation(0.0, 400.0, 0.0, 0.0),
            fit = BudgetFit(FitSet(setOf("Fits"), setOf("Dear", "Dear2")), FitSet(emptySet(), emptySet()), ActivityFit(emptySet(), 0.0), 7),
            showOnlyFits = showOnlyFits,
            cardsReact = cardsReact,
            findingCheaperHotels = false,
        )

    @Test
    fun `one row per hotel with unique stable keys, over-budget flagged`() {
        val rows = commerceRows(SectionId.HOTELS, data, budget(showOnlyFits = false), PlanSelections())
        assertEquals(3, rows.size)
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
        assertEquals(listOf(false, true, true), rows.map { (it as HotelRow).over })
        assertTrue(rows.all { it.contentType == "hotel" })
    }

    @Test
    fun `show only what fits hides over-budget cards except selected ones, with a hidden row`() {
        val selected = PlanSelectionRules.toggle(PlanSelections(), PlanItem.HotelItem(data.results[1]))
        val rows = commerceRows(SectionId.HOTELS, data, budget(showOnlyFits = true), selected)
        assertEquals(listOf("Fits", "Dear"), rows.filterIsInstance<HotelRow>().map { it.hotel.name })
        assertEquals(1, (rows.last() as HiddenRow).count)
    }

    @Test
    fun `budget is ignored until the planner is in use`() {
        val rows = commerceRows(SectionId.HOTELS, data, budget(showOnlyFits = true, cardsReact = false), PlanSelections())
        assertEquals(3, rows.size)
        assertTrue(rows.none { (it as HotelRow).over })
    }

    @Test
    fun `multi-city flights get a header per leg and an empty-leg row`() {
        val f =
            flights(
                """{"trip_type":"multi_city","legs":[{"leg_index":0,"from":"A","to":"B","results":[{"leg_index":0,"price_usd":100,"outbound":{"airline":"X","flight_number":"1"}}]},{"leg_index":1,"results":[]}]}""",
            )
        val rows = commerceRows(SectionId.FLIGHTS, f, null, PlanSelections())
        assertEquals(listOf(LegHeaderRow::class, FlightRow::class, LegHeaderRow::class, LegEmptyRow::class), rows.map { it::class })
        assertEquals(rows.size, rows.map { it.key }.toSet().size)
    }

    @Test
    fun `errored, missing and non-commerce sections have no rows`() {
        assertTrue(commerceRows(SectionId.HOTELS, HotelsData(error = "x"), null, PlanSelections()).isEmpty())
        assertTrue(commerceRows(SectionId.HOTELS, null, null, PlanSelections()).isEmpty())
    }

    @Test
    fun `activity availability dates - real dates, to never before from`() {
        assertTrue(availabilityValid("", ""))
        assertTrue(availabilityValid("2026-12-01", "2026-12-08"))
        assertTrue(availabilityValid("2026-12-01", ""))
        assertFalse(availabilityValid("2026-12-08", "2026-12-01"))
        assertFalse(availabilityValid("2026-02-30", ""))
        assertFalse(availabilityValid("2026-1", ""))
    }
}
