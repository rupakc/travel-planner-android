package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapSpend
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapRulesTest {
    private fun recap(spend: RecapSpend = RecapSpend(), end: String? = "2027-04-08", completed: String? = null) =
        Recap(spend = spend, endDate = end, completedAt = completed)

    @Test
    fun `null or zero distance is none`() {
        assertNull(RecapRules.distanceKm(recap()))
        assertNull(RecapRules.distanceKm(recap().copy(distanceKm = 0.0)))
        assertEquals(41.2, RecapRules.distanceKm(recap().copy(distanceKm = 41.2))!!, 0.0)
    }

    @Test
    fun `stars need a rating`() {
        assertNull(RecapRules.averageStars(recap().copy(avgStars = 4.3, ratedCount = 0)))
        assertEquals(4.3, RecapRules.averageStars(recap().copy(avgStars = 4.3, ratedCount = 2))!!, 0.0)
    }

    @Test
    fun `spend is over only past a positive budget`() {
        val over = RecapRules.spend(recap(RecapSpend(budgetUsd = 2000.0, spentUsd = "2140.50")))!!
        assertTrue(over.over)
        assertEquals(141, over.amount)
        val under = RecapRules.spend(recap(RecapSpend(budgetUsd = 3000.0, spentUsd = "2140.50")))!!
        assertFalse(under.over)
        assertEquals(860, under.amount)
        assertFalse(RecapRules.spend(recap(RecapSpend(budgetUsd = null, spentUsd = "10.00")))!!.over)
        assertFalse(RecapRules.spend(recap(RecapSpend(budgetUsd = 0.0, spentUsd = "10.00")))!!.over)
        assertNull(RecapRules.spend(recap(RecapSpend(spentUsd = null))))
        assertNull(RecapRules.spend(recap(RecapSpend(spentUsd = "abc"))))
    }

    @Test
    fun `complete is offered only for a dated trip not yet completed`() {
        assertTrue(RecapRules.showComplete(recap()))
        assertFalse(RecapRules.showComplete(recap(end = null)))
        assertFalse(RecapRules.showComplete(recap(end = " ")))
        assertFalse(RecapRules.showComplete(recap(completed = "2027-04-09T10:00:00Z")))
    }

    @Test
    fun `the walked route wins, the planned places are the fallback`() {
        val place = { lat: Double, lng: Double -> TripPlace("k", lat = lat, lng = lng) }
        val planned = recap().copy(places = listOf(place(1.0, 1.0), place(2.0, 2.0), TripPlace("none")))
        assertEquals(listOf(1.0 to 1.0, 2.0 to 2.0), RecapRules.routePoints(planned))
        val walked = planned.copy(route = listOf(listOf(38.7, -9.1), listOf(38.8, -9.2), listOf(999.0, 0.0)))
        assertEquals(listOf(38.7 to -9.1, 38.8 to -9.2), RecapRules.routePoints(walked))
        assertTrue(RecapRules.routePoints(recap()).isEmpty())
    }

    @Test
    fun `projection fits the unit square with north up`() {
        val pts = RecapRules.project(listOf(0.0 to 0.0, 10.0 to 10.0))
        assertEquals(2, pts.size)
        assertTrue(pts.all { it.x in 0f..1f && it.y in 0f..1f })
        assertTrue(pts[1].y < pts[0].y)
        assertTrue(RecapRules.project(emptyList()).isEmpty())
        assertEquals(RecapRules.MapPoint(0.5f, 0.5f), RecapRules.project(listOf(5.0 to 5.0)).single())
    }
}
