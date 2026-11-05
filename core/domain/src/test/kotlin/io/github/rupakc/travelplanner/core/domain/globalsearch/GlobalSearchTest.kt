package io.github.rupakc.travelplanner.core.domain.globalsearch

import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind.CHAT
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind.FEATURE
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind.RECENT_SEARCH
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind.SETTING
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind.TRIP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalSearchTest {
    private val items =
        listOf(
            SearchableItem(TRIP, "1", "Tokyo spring trip", subtitle = "Tokyo · 2027-04-01"),
            SearchableItem(TRIP, "2", "Lisbon weekend", subtitle = "Lisbon"),
            SearchableItem(CHAT, "10", "Ramen in Tokyo", subtitle = "Where is the best ramen near Shinjuku?"),
            SearchableItem(RECENT_SEARCH, "rs-1", "New York → Tokyo", subtitle = "2027-04-01 – 2027-04-08"),
            SearchableItem(FEATURE, "compare", "Compare destinations", keywords = listOf("side by side")),
            SearchableItem(SETTING, "theme", "Theme", subtitle = "Appearance", keywords = listOf("dark mode")),
        )

    @Test
    fun `blank query returns nothing`() {
        val result = GlobalSearch.search("   ", items)
        assertTrue(result.groups.isEmpty())
        assertEquals(0, result.total)
    }

    @Test
    fun `matches across kinds, grouped in kind order with counts`() {
        val result = GlobalSearch.search("tokyo", items)
        assertEquals(listOf(TRIP, CHAT, RECENT_SEARCH), result.groups.map { it.kind })
        assertEquals(mapOf(TRIP to 1, CHAT to 1, RECENT_SEARCH to 1), result.counts)
        assertEquals(3, result.total)
    }

    @Test
    fun `title prefix ranks above subtitle-only match`() {
        val list =
            listOf(
                SearchableItem(TRIP, "a", "Weekend away", subtitle = "Paris"),
                SearchableItem(TRIP, "b", "Paris in May"),
            )
        val hits = GlobalSearch.search("paris", list).groups.single().hits
        assertEquals(listOf("b", "a"), hits.map { it.item.id })
        assertTrue(hits[0].score > hits[1].score)
    }

    @Test
    fun `every token must match somewhere`() {
        assertEquals(1, GlobalSearch.search("tokyo spring", items).total)
        assertEquals(0, GlobalSearch.search("tokyo autumn", items).total)
    }

    @Test
    fun `accents and case are ignored`() {
        val list = listOf(SearchableItem(TRIP, "z", "Zürich & Málaga"))
        assertEquals(1, GlobalSearch.search("ZURICH malaga", list).total)
        assertEquals(1, GlobalSearch.search("málaga", listOf(SearchableItem(TRIP, "m", "Malaga"))).total)
    }

    @Test
    fun `keywords are searchable`() {
        val result = GlobalSearch.search("dark", items)
        assertEquals("theme", result.groups.single().hits.single().item.id)
    }

    @Test
    fun `filter limits groups but counts keep every kind`() {
        val result = GlobalSearch.search("tokyo", items, filter = setOf(CHAT))
        assertEquals(listOf(CHAT), result.groups.map { it.kind })
        assertEquals(3, result.counts.values.sum())
        assertEquals(1, result.total)
    }

    @Test
    fun `ties sort by title then id and per-kind limit applies`() {
        val list = (1..30).map { SearchableItem(TRIP, "$it", "Trip $it") }
        val hits = GlobalSearch.search("trip", list, limitPerKind = 5).groups.single().hits
        assertEquals(5, hits.size)
        assertEquals(listOf("Trip 1", "Trip 10", "Trip 11", "Trip 12", "Trip 13"), hits.map { it.item.title })
    }

    @Test
    fun `exact title beats prefix`() {
        val list = listOf(SearchableItem(SETTING, "a", "Theme colours"), SearchableItem(SETTING, "b", "Theme"))
        assertEquals("b", GlobalSearch.search("theme", list).groups.single().hits.first().item.id)
    }

    @Test
    fun `settings entries resolve by key`() {
        assertEquals(SettingsEntry.THEME, SettingsEntry.fromKey("theme"))
        assertNull(SettingsEntry.fromKey("nope"))
        assertEquals(SettingsEntry.entries.size, SettingsEntry.entries.map { it.key }.toSet().size)
    }
}
