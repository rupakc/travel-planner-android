package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.section.ItineraryDay

/** One row of a displayed itinerary day: a slot, or a "Free time" placeholder for an empty standard time. */
sealed interface DayRow {
    data class Slot(val slot: ScheduledSlot) : DayRow

    data class Free(val timeOfDay: String) : DayRow
}

/**
 * Rows for one arranged, scheduled day (web ItinerarySection `dayRows`): its slots plus, when
 * [withPlaceholders] (editing on), a [DayRow.Free] for every standard time no movable activity
 * occupies. Placeholders exist only on days whose [originalDay] had a standard-time slot, and sit
 * where their time ranks relative to odd/duplicate slots.
 */
fun dayRows(day: ScheduledDay, originalDay: ItineraryDay?, isMovable: (String?) -> Boolean, withPlaceholders: Boolean): List<DayRow> {
    val rows: MutableList<DayRow> = day.slots.mapTo(mutableListOf()) { DayRow.Slot(it) }
    if (!withPlaceholders || originalDay == null || originalDay.slots.none { it.timeOfDay in ItineraryLayouts.TIMES }) return rows
    val ranks = ItineraryLayouts.slotRanks(originalDay)
    val rankOf = { s: ScheduledSlot ->
        if (isMovable(s.key)) ItineraryLayouts.TIMES.indexOf(s.slot.timeOfDay).toDouble() else ranks[s.key] ?: NO_RANK
    }
    val occupied = day.slots.filter { isMovable(it.key) }.mapNotNull { it.slot.timeOfDay }.toSet()
    ItineraryLayouts.TIMES.forEachIndexed { rank, tod ->
        if (tod in occupied) return@forEachIndexed
        val at = rows.indexOfFirst { it is DayRow.Slot && rankOf(it.slot) > rank }
        rows.add(if (at < 0) rows.size else at, DayRow.Free(tod))
    }
    return rows
}

private const val NO_RANK = -0.5
