package io.github.rupakc.travelplanner.feature.results

import io.github.rupakc.travelplanner.core.model.section.SectionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultsListIndexTest {
    // 8 header items (0..7), then FLIGHTS header 8 + 3 cards (9..11), WEATHER 12 (collapsed), HOTELS 13 + 2 cards (14, 15).
    private val index =
        ResultsListIndex(
            sections = listOf(SectionId.FLIGHTS, SectionId.WEATHER, SectionId.HOTELS),
            rowCounts = mapOf(SectionId.FLIGHTS to 3, SectionId.HOTELS to 2),
            headerItems = 8,
        )

    @Test
    fun `section headers follow the header items and the cards of the sections before them`() {
        assertEquals(8, index.indexOf(SectionId.FLIGHTS))
        assertEquals(12, index.indexOf(SectionId.WEATHER))
        assertEquals(13, index.indexOf(SectionId.HOTELS))
    }

    @Test
    fun `sections hidden by the text filter have no index and do not shift the others`() {
        val filtered = ResultsListIndex(listOf(SectionId.HOTELS, SectionId.VISA), mapOf(SectionId.HOTELS to 1), headerItems = 8)
        assertNull(filtered.indexOf(SectionId.FLIGHTS))
        assertEquals(8, filtered.indexOf(SectionId.HOTELS))
        assertEquals(10, filtered.indexOf(SectionId.VISA))
        assertEquals(SectionId.HOTELS, filtered.sectionAt(9))
    }

    @Test
    fun `every item maps to its section, nothing above the first`() {
        assertNull(index.sectionAt(0))
        assertNull(index.sectionAt(7))
        assertEquals(SectionId.FLIGHTS, index.sectionAt(8))
        assertEquals(SectionId.FLIGHTS, index.sectionAt(11))
        assertEquals(SectionId.WEATHER, index.sectionAt(12))
        assertEquals(SectionId.HOTELS, index.sectionAt(15))
        assertEquals(SectionId.HOTELS, index.sectionAt(99))
    }

    @Test
    fun `empty list has no sections`() {
        val empty = ResultsListIndex(emptyList(), emptyMap(), headerItems = 8)
        assertNull(empty.indexOf(SectionId.FLIGHTS))
        assertNull(empty.sectionAt(8))
    }

    @Test
    fun `spy takes the item that reaches below the spy line`() {
        val line = spyLine(viewportStart = -40, viewportEnd = 1960)
        assertEquals(460, line)
        // Card 9 ends at 130 px (just leaving the top): the item crossing the line, 10, is the one in view.
        val items = listOf(ItemSpan(9, -300, 130), ItemSpan(10, 142, 900), ItemSpan(11, 912, 1400))
        assertEquals(10, firstVisibleIndex(items, line))
        assertEquals(9, firstVisibleIndex(items, line = 0))
        assertNull(firstVisibleIndex(emptyList(), line))
    }

    @Test
    fun `a tapped section stays active until it leaves the list, otherwise the spy decides`() {
        val visible = listOf(SectionId.FLIGHTS, SectionId.PACKING_LIST)
        assertEquals(SectionId.PACKING_LIST, activeSection(SectionId.PACKING_LIST, SectionId.FLIGHTS, visible))
        assertEquals(SectionId.FLIGHTS, activeSection(null, SectionId.FLIGHTS, visible))
        assertEquals(SectionId.FLIGHTS, activeSection(SectionId.HOTELS, SectionId.FLIGHTS, visible))
        assertNull(activeSection(null, null, visible))
    }
}
