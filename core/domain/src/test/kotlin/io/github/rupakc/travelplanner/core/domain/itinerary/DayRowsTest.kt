package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import org.junit.Assert.assertEquals
import org.junit.Test

class DayRowsTest {
    private val original =
        ItineraryDay(
            dayNumber = 1,
            slots =
                listOf(
                    ItinerarySlot(timeOfDay = "morning", activity = "A"),
                    ItinerarySlot(timeOfDay = "late night", activity = "Club"),
                    ItinerarySlot(timeOfDay = "evening", activity = "C"),
                ),
        )
    private val itinerary = ItineraryData(days = listOf(original))

    private fun labels(rows: List<DayRow>) =
        rows.map {
            when (it) {
                is DayRow.Slot -> it.slot.slot.activity
                is DayRow.Free -> "free:${it.timeOfDay}"
            }
        }

    private fun rows(withPlaceholders: Boolean, layoutKeyRemoved: String? = null): List<DayRow> {
        val layout = layoutKeyRemoved?.let { ItineraryLayouts.removeSlot(itinerary, null, it) }
        val day = ItinerarySchedule.scheduleDays(ItineraryLayouts.arrange(itinerary, layout).days).single()
        val movable = { key: String? -> key != null && ItineraryLayouts.moveTargets(itinerary, layout, key).isNotEmpty() }
        return dayRows(day, original, movable, withPlaceholders)
    }

    @Test
    fun `without placeholders rows are the slots`() {
        assertEquals(listOf("A", "Club", "C"), labels(rows(withPlaceholders = false)))
    }

    @Test
    fun `free time sits where its time ranks among odd slots`() {
        assertEquals(listOf("A", "Club", "free:afternoon", "C"), labels(rows(withPlaceholders = true)))
        assertEquals(listOf("free:morning", "Club", "free:afternoon", "C"), labels(rows(withPlaceholders = true, layoutKeyRemoved = "1-morning")))
    }

    @Test
    fun `days without a standard slot get no placeholders`() {
        val odd = ItineraryDay(dayNumber = 2, slots = listOf(ItinerarySlot(timeOfDay = "dawn", activity = "Run")))
        val day = ItinerarySchedule.scheduleDays(listOf(odd)).single()
        assertEquals(listOf("Run"), labels(dayRows(day, odd, { false }, withPlaceholders = true)))
    }
}
