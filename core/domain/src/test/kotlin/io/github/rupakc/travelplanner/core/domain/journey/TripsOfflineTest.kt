package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PrepSummary
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripsOfflineTest {
    private val today = LocalDate.parse("2026-11-12")

    private fun trip(
        id: Long,
        phase: Phase?,
        start: String? = null,
        end: String? = start,
        updated: String = "2026-11-01T00:00:00Z",
        source: String = "auto",
        prepComplete: Boolean = false,
        recap: String? = null,
    ) = Trip(
        id = id,
        phase = phase,
        startDate = start,
        endDate = end,
        updatedAt = updated,
        phaseSource = source,
        prep = PrepSummary(complete = prepComplete),
        recapCompletedAt = recap,
    )

    @Test
    fun sortOrderGolden() {
        val trips =
            listOf(
                trip(1, Phase.MEMORIES, "2026-01-01"),
                trip(2, Phase.DREAMING, updated = "2026-11-01T00:00:00Z"),
                trip(3, Phase.PLANNING, "2027-06-01"),
                trip(4, Phase.READY, "2026-11-20"),
                trip(5, Phase.ON_THE_ROAD, "2026-11-10", "2026-11-14"),
                trip(6, Phase.MEMORIES, "2026-09-01"),
                trip(7, Phase.DREAMING, updated = "2026-11-05T00:00:00Z"),
                trip(8, Phase.PLANNING, "2027-02-01"),
            )
        assertEquals(listOf(5L, 4L, 8L, 3L, 7L, 2L, 6L, 1L), TripsOffline.sort(trips).map { it.id })
    }

    @Test
    fun recomputesPhaseDaysAndDayIndexFromCachedDates() {
        val cached = trip(1, Phase.PLANNING, "2026-12-30", "2027-01-05")
        // 48 days out: planning.
        assertEquals(Phase.PLANNING, TripsOffline.recompute(cached, today).phase)
        assertEquals(48, TripsOffline.recompute(cached, today).daysToStart)
        // 10 days out: ready; on the road; memories.
        assertEquals(Phase.READY, TripsOffline.recompute(cached, LocalDate.parse("2026-12-20")).phase)
        val road = TripsOffline.recompute(cached, LocalDate.parse("2026-12-31"))
        assertEquals(Phase.ON_THE_ROAD, road.phase)
        assertEquals(2, road.dayIndex)
        assertNull(road.daysToStart)
        assertEquals(Phase.MEMORIES, TripsOffline.recompute(cached, LocalDate.parse("2027-01-06")).phase)
    }

    @Test
    fun manualOverrideAndPrepCompleteAreRespected() {
        val manual = trip(1, Phase.DREAMING, "2026-11-10", "2026-11-14", source = "manual")
        assertEquals(Phase.DREAMING, TripsOffline.recompute(manual, today).phase)
        val prepared = trip(2, Phase.PLANNING, "2027-03-01", prepComplete = true)
        assertEquals(Phase.READY, TripsOffline.recompute(prepared, today).phase)
        assertEquals(Phase.DREAMING, TripsOffline.recompute(trip(3, Phase.PLANNING), today).phase)
    }

    @Test
    fun pinnedFollowsTheServerRule() {
        val road = trip(1, Phase.ON_THE_ROAD, "2026-11-10", "2026-11-20")
        val soon = trip(2, Phase.READY, "2026-11-15")
        val sooner = trip(3, Phase.READY, "2026-11-13")
        val recent = trip(4, Phase.MEMORIES, "2026-11-01", "2026-11-05")
        assertEquals(1L, TripsOffline.pinnedId(listOf(soon, road, sooner, recent), today))
        assertEquals(3L, TripsOffline.pinnedId(listOf(soon, sooner, recent), today))
        assertEquals(4L, TripsOffline.pinnedId(listOf(recent), today))
        assertNull(TripsOffline.pinnedId(listOf(recent.copy(recapCompletedAt = "2026-11-06T00:00:00Z")), today))
        assertNull(TripsOffline.pinnedId(listOf(trip(5, Phase.MEMORIES, "2026-01-01", "2026-01-05")), today))
    }

    @Test
    fun responseIsResortedAndRepinned() {
        val response = TripsResponse(listOf(trip(1, Phase.PLANNING, "2026-11-20"), trip(2, Phase.PLANNING, "2026-12-30")), pinnedId = 2, today = "2026-10-01")
        val out = TripsOffline.recompute(response, today)
        assertEquals(listOf(1L, 2L), out.trips.map { it.id })
        assertEquals(Phase.READY, out.trips.first().phase)
        assertEquals(1L, out.pinnedId)
        assertEquals("2026-11-12", out.today)
    }
}
