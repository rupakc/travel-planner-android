package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness.PartId
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.Hotel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases ported from the web's `planCompleteness.test.js`. */
class PlanCompletenessTest {
    private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val search = obj("""{"departure_date":"2026-05-01","return_date":"2026-05-08"}""")

    private fun itinerary(vararg slotsPerDay: Int): JsonObject {
        val days =
            slotsPerDay.mapIndexed { i, n ->
                val slots = (1..n).joinToString(",") { """{"time_of_day":"morning","activity":"x$it"}""" }
                """{"day_number":${i + 1},"slots":[$slots]}"""
            }
        return obj("""{"days":[${days.joinToString(",")}]}""")
    }

    private fun part(r: PlanCompleteness.Result, id: PartId) = r.parts.single { it.id == id }

    @Test
    fun `empty is 0 with the five parts in order`() {
        val r = PlanCompleteness.of(PlanSelections(), search, visaChecked = false)
        assertEquals(0, r.percent)
        assertEquals(listOf(PartId.Flights, PartId.Stay, PartId.Days, PartId.Visa, PartId.Packing), r.parts.map { it.id })
    }

    @Test
    fun `everything done is 100`() {
        val sel =
            PlanSelections(
                flight = Flight(),
                hotel = Hotel(),
                itinerarySnapshot = itinerary(1, 1, 1, 1, 1, 1, 1),
                packingList = obj("""{"categories":[{"name":"Clothes"}],"total_items":4,"checked_count":1}"""),
            )
        assertEquals(100, PlanCompleteness.of(sel, search, visaChecked = true).percent)
    }

    @Test
    fun `3 of 7 days filled counts as 3 sevenths`() {
        val sel =
            PlanSelections(
                itinerarySnapshot =
                    obj(
                        """{"days":[{"day_number":1,"slots":[{"activity":"a"}]},{"day_number":2,"slots":[{"activity":"a"}]},""" +
                            """{"day_number":3,"slots":[{"activity":"a"}]},{"day_number":4,"slots":[]}]}""",
                    ),
            )
        val r = PlanCompleteness.of(sel, search)
        assertEquals(3.0 / 7, part(r, PartId.Days).fraction, 1e-9)
        assertEquals(9, r.percent)
        assertFalse(part(r, PartId.Days).done)
    }

    @Test
    fun `null or empty packing is not done`() {
        assertFalse(part(PlanCompleteness.of(PlanSelections(packingList = null), search), PartId.Packing).done)
        assertFalse(part(PlanCompleteness.of(PlanSelections(packingList = obj("""{"categories":[],"total_items":0}""")), search), PartId.Packing).done)
        assertTrue(part(PlanCompleteness.of(PlanSelections(packingList = obj("""{"total_items":2}""")), search), PartId.Packing).done)
    }

    @Test
    fun `flights array counts and missing search data does not throw`() {
        assertEquals(20, PlanCompleteness.of(PlanSelections(flights = listOf(Flight())), null).percent)
        assertEquals(0, PlanCompleteness.of(PlanSelections(), null).percent)
    }

    @Test
    fun `nights come from destination_nights first, then dates, else 1`() {
        val sel = PlanSelections(itinerarySnapshot = itinerary(1, 1))
        val multi = obj("""{"destination_nights":[1,{"nights":3}],"departure_date":"2026-05-01","return_date":"2026-05-02"}""")
        assertEquals(2.0 / 4, part(PlanCompleteness.of(sel, multi), PartId.Days).fraction, 1e-9)
        assertEquals(1.0, part(PlanCompleteness.of(sel, obj("""{"departure_date":"2026-05-01","return_date":"2026-05-01"}""")), PartId.Days).fraction, 1e-9)
    }

    @Test
    fun `days fraction is capped at 1`() {
        val sel = PlanSelections(itinerarySnapshot = itinerary(1, 1, 1, 1, 1, 1, 1, 1, 1, 1))
        assertEquals(1.0, part(PlanCompleteness.of(sel, search), PartId.Days).fraction, 1e-9)
    }
}
