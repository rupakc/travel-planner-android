package io.github.rupakc.travelplanner.feature.trips.today

import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import java.time.LocalDate
import java.time.LocalTime

/** Pure rules of the Today tab (Trips-API "Check-in"). */
object TodayRules {
    /** Why a check-in was refused, from the server's error code. */
    enum class CheckinProblem { OutOfTrip, DayInvalid, Other }

    fun problemOf(code: String?): CheckinProblem =
        when (code) {
            "checkin_out_of_trip" -> CheckinProblem.OutOfTrip
            "checkin_day_invalid" -> CheckinProblem.DayInvalid
            else -> CheckinProblem.Other
        }

    /**
     * The itinerary day for [today]: the one dated [today], else the one numbered [dayIndex] (the server's
     * `day_index`, 1-based, null outside the trip).
     */
    fun dayFor(days: List<ScheduledDay>, today: LocalDate, dayIndex: Int?): ScheduledDay? =
        days.firstOrNull { it.day.date == today.toString() } ?: dayIndex?.let { n -> days.firstOrNull { it.day.dayNumber == n } }

    /**
     * The slot to do next at [now]: the first whose end is still ahead (an untimed slot counts as always ahead).
     * Null when every slot is over.
     */
    fun nextUp(slots: List<ScheduledSlot>, now: LocalTime): ScheduledSlot? =
        slots.firstOrNull { s ->
            val end = parse(s.endTime) ?: parse(s.startTime)?.plusHours(1)
            end == null || s.endDayOffset?.let { it > 0 } == true || end.isAfter(now)
        }

    private fun parse(value: String?): LocalTime? = value?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    /** The text a maps app searches for a slot: its place (else activity) name plus the [city]; null when it has no name. */
    fun directionsQuery(slot: ScheduledSlot, city: String?): String? {
        val name = (slot.slot.location ?: slot.slot.activity)?.trim().orEmpty()
        if (name.isEmpty()) return null
        val c = (slot.slot.city ?: city)?.trim().orEmpty()
        return if (c.isEmpty() || name.contains(c, ignoreCase = true)) name else "$name, $c"
    }
}
