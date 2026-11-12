package io.github.rupakc.travelplanner.feature.trips.trip

import io.github.rupakc.travelplanner.core.model.trips.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class TripTabsTest {
    private val all = TripTab.entries.toSet()

    @Test
    fun `the tab table matches the web`() {
        fun tabs(p: Phase) = TripTabs.forPhase(p).tabs.map { it.id }
        assertEquals(listOf("overview"), tabs(Phase.DREAMING))
        assertEquals(listOf("overview", "prep", "money", "offline"), tabs(Phase.PLANNING))
        assertEquals(listOf("prep", "overview", "offline", "money"), tabs(Phase.READY))
        assertEquals(listOf("today", "offline", "money", "journal", "overview"), tabs(Phase.ON_THE_ROAD))
        assertEquals(listOf("recap", "journal", "money", "overview"), tabs(Phase.MEMORIES))
    }

    @Test
    fun `default tab per phase`() {
        assertEquals(TripTab.Overview, TripTabs.forPhase(Phase.PLANNING).default)
        assertEquals(TripTab.Prep, TripTabs.forPhase(Phase.READY).default)
        assertEquals(TripTab.Today, TripTabs.forPhase(Phase.ON_THE_ROAD).default)
        assertEquals(TripTab.Recap, TripTabs.forPhase(Phase.MEMORIES).default)
    }

    @Test
    fun `tabs without content are not shown and the default falls back`() {
        val builtIn = setOf(TripTab.Overview, TripTab.Prep)
        val road = TripTabs.resolve(Phase.ON_THE_ROAD, null, builtIn)
        assertEquals(listOf(TripTab.Overview), road.visible)
        assertEquals(TripTab.Overview, road.selected)
        val withToday = TripTabs.resolve(Phase.ON_THE_ROAD, null, builtIn + TripTab.Today)
        assertEquals(listOf(TripTab.Today, TripTab.Overview), withToday.visible)
        assertEquals(TripTab.Today, withToday.selected)
    }

    @Test
    fun `a requested tab outside the phase stays reachable`() {
        val r = TripTabs.resolve(Phase.DREAMING, TripTab.Prep, all)
        assertEquals(listOf(TripTab.Overview, TripTab.Prep), r.visible)
        assertEquals(TripTab.Prep, r.selected)
    }

    @Test
    fun `a requested tab that does not exist is ignored`() {
        val r = TripTabs.resolve(Phase.PLANNING, TripTab.Journal, setOf(TripTab.Overview, TripTab.Prep))
        assertEquals(TripTab.Overview, r.selected)
        assertEquals(listOf(TripTab.Overview, TripTab.Prep), r.visible)
    }

    @Test
    fun `route ids round trip`() {
        TripTab.entries.forEach { assertEquals(it, TripTab.fromId(it.id)) }
        assertEquals(null, TripTab.fromId("nope"))
    }
}
