package io.github.rupakc.travelplanner.core.notifications.reminders

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Kinds of trip reminder (spec §7). */
enum class ReminderType {
    /** 7 days before departure: visa and packing check. */
    WEEK_BEFORE,

    /** The day before departure: flight and weather. */
    DAY_BEFORE,

    /** Morning of the first day: "Your <destination> trip starts today". */
    TRIP_START,

    /** Morning of each later trip day: that day's itinerary. */
    TRIP_DAY,

    /** Morning of the return day: checkout. */
    LAST_DAY,
}

/** One reminder of one saved plan. [dayNumber] is 1-based for trip days (0 before the trip). */
data class TripReminder(val planId: Long, val type: ReminderType, val date: LocalDate, val dayNumber: Int, val fireAt: ZonedDateTime) {
    /** Unique WorkManager name: rescheduling the same reminder replaces it. */
    val uniqueName: String get() = "trip-reminder-$planId-${type.name}-$date"
}

/**
 * Pure date math for trip reminders. Times are wall-clock in the device's zone (the traveller's
 * phone moves with them): 09:00 a week before, 18:00 the day before, 08:00 each trip morning.
 * A wall time that doesn't exist on a DST spring-forward day moves forward by the gap
 * (`ZonedDateTime.of` rules); an ambiguous one takes the earlier offset.
 */
object TripReminderPlanner {
    val WEEK_BEFORE_TIME: LocalTime = LocalTime.of(9, 0)
    val DAY_BEFORE_TIME: LocalTime = LocalTime.of(18, 0)
    val MORNING_TIME: LocalTime = LocalTime.of(8, 0)

    /** Longest trip we schedule daily reminders for (the backend caps stays at 30 nights). */
    const val MAX_TRIP_DAYS = 31

    /**
     * A reminder due earlier today but at most this long ago is still planned (it fires at once):
     * Doze may have deferred it, or the phone was off at 08:00. The worker's posted log stops repeats.
     */
    val GRACE: Duration = Duration.ofHours(2)

    private const val WEEK = 7L

    /** Reminders still ahead of [now] (or due within [GRACE] today) for a trip departing [departure] and returning [returnDate] (null = one way). */
    fun plan(planId: Long, departure: LocalDate, returnDate: LocalDate?, now: ZonedDateTime): List<TripReminder> {
        val zone = now.zone

        fun at(date: LocalDate, time: LocalTime) = ZonedDateTime.of(date, time, zone)
        val all = mutableListOf<TripReminder>()
        all += TripReminder(planId, ReminderType.WEEK_BEFORE, departure.minusDays(WEEK), 0, at(departure.minusDays(WEEK), WEEK_BEFORE_TIME))
        all += TripReminder(planId, ReminderType.DAY_BEFORE, departure.minusDays(1), 0, at(departure.minusDays(1), DAY_BEFORE_TIME))
        val last = returnDate?.takeIf { it.isAfter(departure) }
        val tripDays = if (last == null) 1 else ChronoUnit.DAYS.between(departure, last).toInt() + 1
        val days = minOf(tripDays, MAX_TRIP_DAYS)
        for (i in 0 until days) {
            val date = departure.plusDays(i.toLong())
            val type =
                when {
                    i == 0 -> ReminderType.TRIP_START
                    date == last -> ReminderType.LAST_DAY
                    else -> ReminderType.TRIP_DAY
                }
            all += TripReminder(planId, type, date, i + 1, at(date, MORNING_TIME))
        }
        // The checkout reminder is kept even when a long trip's daily reminders are capped.
        if (last != null && tripDays > MAX_TRIP_DAYS) all += TripReminder(planId, ReminderType.LAST_DAY, last, tripDays, at(last, MORNING_TIME))
        return all.filter { it.fireAt.isAfter(now) || isWithinGrace(it.fireAt, now) }
    }

    private fun isWithinGrace(fireAt: ZonedDateTime, now: ZonedDateTime): Boolean =
        !fireAt.isAfter(now) && fireAt.toLocalDate() == now.toLocalDate() && Duration.between(fireAt, now) <= GRACE
}
