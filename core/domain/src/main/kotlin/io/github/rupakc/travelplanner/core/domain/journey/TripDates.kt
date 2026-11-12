package io.github.rupakc.travelplanner.core.domain.journey

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Device-local calendar day helpers; the app always sends this as the `today` query parameter. */
object TripDates {
    /** The calendar day on [clock] in [zone] (the device zone by default), never the UTC day. */
    fun localToday(clock: Clock = Clock.systemUTC(), zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        LocalDate.now(clock.withZone(zone))
}
