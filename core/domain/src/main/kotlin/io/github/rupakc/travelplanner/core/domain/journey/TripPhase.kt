package io.github.rupakc.travelplanner.core.domain.journey

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Journey phase of a trip. Journey-local on purpose (Task 1 ships before the model module's `Phase`);
 * map to it at the data layer once it exists. [wire] is the server spelling.
 */
enum class TripPhase(val wire: String) {
    Dreaming("dreaming"),
    Planning("planning"),
    Ready("ready"),
    OnTheRoad("on_the_road"),
    Memories("memories"),
    ;

    companion object {
        /** The phase for a server value, or null when unknown. */
        fun fromWire(value: String?): TripPhase? = entries.firstOrNull { it.wire == value }
    }
}

/** Trips-API "Phases": the effective phase and the two derived day counters, all pure. */
object TripPhaseRules {
    /** Days before the start from which a trip counts as ready without a complete prep list. */
    const val READY_WINDOW_DAYS: Long = 14

    /** End as the contract defines it: missing means the start, and an end before the start is the start. */
    private fun endOf(start: LocalDate, end: LocalDate?): LocalDate = if (end == null || end.isBefore(start)) start else end

    /** Effective phase; precedence override, no start, after the end, in the trip, ready window or prep done, planning. */
    fun effective(start: LocalDate?, end: LocalDate?, override: TripPhase?, today: LocalDate, prepComplete: Boolean): TripPhase {
        if (override != null) return override
        if (start == null) return TripPhase.Dreaming
        val e = endOf(start, end)
        return when {
            today.isAfter(e) -> TripPhase.Memories
            !today.isBefore(start) -> TripPhase.OnTheRoad
            prepComplete || ChronoUnit.DAYS.between(today, start) <= READY_WINDOW_DAYS -> TripPhase.Ready
            else -> TripPhase.Planning
        }
    }

    /** 1-based day of the trip while [today] is within it (independent of any override), else null. */
    fun dayIndex(start: LocalDate?, end: LocalDate?, today: LocalDate): Int? {
        if (start == null || today.isBefore(start) || today.isAfter(endOf(start, end))) return null
        return (ChronoUnit.DAYS.between(start, today) + 1).toInt()
    }

    /** Days until the start while [today] is before it, else null. */
    fun daysToStart(start: LocalDate?, today: LocalDate): Int? {
        if (start == null || !today.isBefore(start)) return null
        return ChronoUnit.DAYS.between(today, start).toInt()
    }
}
