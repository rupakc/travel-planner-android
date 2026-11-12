package io.github.rupakc.travelplanner.feature.trips.journal

import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.domain.journey.PlaceKey
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.trips.Entry
import io.github.rupakc.travelplanner.core.model.trips.TripPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalRulesTest {
    private fun day(vararg names: String): ScheduledDay {
        val slots = names.map { ItinerarySlot(activity = it, timeOfDay = "morning", dayNumber = 1, date = "2026-11-13") }
        return ScheduledDay(ItineraryDay(dayNumber = 1, date = "2026-11-13", slots = slots), slots.map { ScheduledSlot(it) }, emptyList(), scheduled = false)
    }

    @Test
    fun `an itinerary place key comes from kind and name through the shared rule`() {
        val rows = JournalRules.placeRows(listOf(day("Pastéis de Belém!", "東京タワー")), emptyList(), emptySet())
        assertEquals(listOf("activity:pasteis-de-belem", PlaceKey.of("activity", "東京タワー")), rows.map { it.key })
        assertTrue(rows.last().key.matches(Regex("^[a-z]+:[a-z0-9-]{1,60}$")))
    }

    @Test
    fun `server places win over the itinerary and nearby ones are added`() {
        val server =
            listOf(
                TripPlace("activity:senso-ji", "Senso-ji", "activity", visited = true, stars = 4, visitedAt = "2026-11-13T01:00:00Z"),
                TripPlace("nearby:ramen-bar", "Ramen bar", "nearby", visited = true),
            )
        val rows = JournalRules.placeRows(listOf(day("Senso-ji", "Meiji Shrine")), server, setOf("activity:meiji-shrine"))
        assertEquals(listOf("activity:senso-ji", "activity:meiji-shrine", "nearby:ramen-bar"), rows.map { it.key })
        assertTrue(rows[0].visited)
        assertEquals(4, rows[0].stars)
        assertFalse(rows[1].visited)
        assertTrue(rows[1].pending)
    }

    @Test
    fun `an unvisited place keeps reading as not visited even with a first-visit marker`() {
        val rows = JournalRules.placeRows(emptyList(), listOf(TripPlace("place:x", "X", visited = false, visitedAt = "2026-11-13T01:00:00Z")), emptySet())
        assertFalse(rows.single().visited)
    }

    @Test
    fun `a long name is cut to 120 before keying and blanks are skipped`() {
        val rows = JournalRules.placeRows(listOf(day("a".repeat(200), "  ")), emptyList(), emptySet())
        assertEquals(1, rows.size)
        assertEquals(120, rows.single().name.length)
    }

    @Test
    fun `queued entries sort after synced ones of the same day, nulls last`() {
        val synced = Entry(1, "c1", "2026-11-13", "a", createdAt = "2026-11-13T10:00:00Z")
        val queued = Entry(-1, "c2", "2026-11-13", "b")
        val earlier = Entry(2, "c3", "2026-11-12", "c", createdAt = "2026-11-12T10:00:00Z")
        val undated = Entry(3, "c4", null, "d", createdAt = "2026-11-01T10:00:00Z")
        val rows = JournalRules.sortEntries(listOf(queued, undated, synced, earlier), setOf("c2"))
        assertEquals(listOf(2L, 1L, -1L, 3L), rows.map { it.entry.id })
        assertEquals(listOf(false, false, true, false), rows.map { it.pending })
    }

    @Test
    fun `stars stay within 1 to 5 and the same star clears`() {
        assertTrue(JournalRules.validStars(null))
        assertTrue(JournalRules.validStars(1))
        assertTrue(JournalRules.validStars(5))
        assertFalse(JournalRules.validStars(0))
        assertFalse(JournalRules.validStars(6))
        assertEquals(3, JournalRules.starsAfterTap(null, 3))
        assertEquals(5, JournalRules.starsAfterTap(3, 5))
        assertNull(JournalRules.starsAfterTap(3, 3))
        assertEquals(3, JournalRules.starsAfterTap(3, 9))
        assertEquals(3, JournalRules.starsAfterTap(3, 0))
    }

    @Test
    fun `body limits`() {
        assertNull(JournalRules.cleanBody("   "))
        assertEquals("hi", JournalRules.cleanBody(" hi "))
        assertEquals(4000, JournalRules.cleanBody("x".repeat(4000))?.length)
        assertNull(JournalRules.cleanBody("x".repeat(4001)))
    }

    @Test
    fun `limit codes map to problems`() {
        assertEquals(JournalRules.Problem.JournalLimit, JournalRules.problemOf("journal_limit"))
        assertEquals(JournalRules.Problem.PlacesLimit, JournalRules.problemOf("places_limit"))
        assertEquals(JournalRules.Problem.Other, JournalRules.problemOf("x"))
        assertEquals(JournalRules.Problem.Other, JournalRules.problemOf(null))
    }
}
