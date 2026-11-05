package io.github.rupakc.travelplanner.feature.home

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.preferences.TasteSignal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLogicTest {
    private val today = LocalDate.of(2027, 3, 10)

    @Test
    fun `reasons come only from search fields`() {
        val reasons =
            HomePersonalisation.reasonsFor(
                recent("1", "Tokyo", budget = 3000.0, children = 1, access = listOf("wheelchair"), stops = listOf("Seoul")),
            )
        assertEquals(
            mapOf(
                FeatureId.BUDGET_PLANNER to TileReason.SEARCHED_WITH_BUDGET,
                FeatureId.ACCESSIBILITY_INFO to TileReason.SEARCHED_WITH_ACCESSIBILITY,
                FeatureId.PACKING_LIST to TileReason.SEARCHED_WITH_KIDS,
                FeatureId.ITINERARY_EDITOR to TileReason.SEARCHED_MULTI_CITY,
                FeatureId.DEAL_ALERTS to TileReason.SEARCHED_DESTINATION,
            ),
            reasons,
        )
        assertEquals(mapOf(FeatureId.DEAL_ALERTS to TileReason.SEARCHED_DESTINATION), HomePersonalisation.reasonsFor(recent("2", "Rome")))
    }

    @Test
    fun `two destinations boost compare and taste interests boost events and tours`() {
        val taste = TasteProfile(signals = mapOf("interest" to listOf(TasteSignal("Culture", 3))))
        val reasons = HomePersonalisation.reasons(listOf(recent("1", "Tokyo"), recent("2", "Rome")), taste)
        assertEquals(TileReason.SEARCHED_SEVERAL_DESTINATIONS, reasons[FeatureId.COMPARE])
        assertEquals(TileReason.TASTE_EVENTS, reasons[FeatureId.EVENTS])
        assertEquals(TileReason.TASTE_WALKING, reasons[FeatureId.WALKING_TOURS])
        assertNull(HomePersonalisation.reasons(listOf(recent("1", "Tokyo"), recent("2", "tokyo ")), null)[FeatureId.COMPARE])
    }

    @Test
    fun `boosted tiles lead, the rest keep catalogue order, hidden features never show`() {
        val visible = setOf(FeatureId.CHAT, FeatureId.COMPARE, FeatureId.BUDGET_PLANNER, FeatureId.DEAL_ALERTS)
        val tiles =
            HomePersonalisation.orderTiles(
                visible,
                mapOf(
                    FeatureId.COMPARE to TileReason.SEARCHED_SEVERAL_DESTINATIONS,
                    FeatureId.BUDGET_PLANNER to TileReason.SEARCHED_WITH_BUDGET,
                    FeatureId.EVENTS to TileReason.TASTE_EVENTS,
                ),
            )
        assertEquals(listOf(FeatureId.BUDGET_PLANNER, FeatureId.COMPARE, FeatureId.DEAL_ALERTS, FeatureId.CHAT), tiles.map { it.feature })
    }

    @Test
    fun `because you searched uses the latest search and visible features only`() {
        val row =
            HomePersonalisation.becauseYouSearched(
                listOf(recent("9", "Lisbon", budget = 900.0), recent("1", "Tokyo", children = 2)),
                setOf(FeatureId.BUDGET_PLANNER),
            )
        assertEquals("Lisbon", row?.destination)
        assertEquals("9", row?.recentSearchId)
        assertEquals(listOf(FeatureId.BUDGET_PLANNER), row?.tiles?.map { it.feature })
        assertNull(HomePersonalisation.becauseYouSearched(listOf(recent("9", "Lisbon")), emptySet()))
        assertNull(HomePersonalisation.becauseYouSearched(emptyList(), FeatureId.entries.toSet()))
    }

    @Test
    fun `taste highlights are the top values by count`() {
        val taste =
            TasteProfile(
                signals =
                    mapOf(
                        "interest" to listOf(TasteSignal("food", 5), TasteSignal("art", 1)),
                        "destination" to listOf(TasteSignal("Tokyo", 3), TasteSignal("Food", 9), TasteSignal(null, 10)),
                    ),
            )
        assertEquals(listOf("Food", "Tokyo"), HomePersonalisation.tasteHighlights(taste, limit = 2))
        assertTrue(HomePersonalisation.tasteHighlights(null).isEmpty())
    }

    @Test
    fun `upcoming trip prefers a trip under way, then the nearest departure`() {
        val plans =
            listOf(
                plan(1, "Past", "Rome", "2027-01-01", "2027-01-05"),
                plan(2, "Later", "Oslo", "2027-06-01"),
                plan(3, "Soon", "Tokyo", "2027-04-01", "2027-04-08"),
            )
        val trip = UpcomingTrips.nearest(plans, today)
        assertEquals(3L, trip?.planId)
        assertEquals(22L, trip?.daysUntil)
        val ongoing = UpcomingTrips.nearest(plans + plan(4, "", "Lima", "2027-03-08", "2027-03-12"), today)
        assertEquals(4L, ongoing?.planId)
        assertTrue(ongoing!!.ongoing)
        assertEquals("Lima", ongoing.name)
        assertNull(UpcomingTrips.nearest(listOf(plans[0]), today))
    }

    @Test
    fun `plans without a decodable search are skipped`() {
        val broken = io.github.rupakc.travelplanner.core.model.plan.Plan(id = 9, name = "Web plan")
        assertNull(UpcomingTrips.nearest(listOf(broken), today))
    }
}
