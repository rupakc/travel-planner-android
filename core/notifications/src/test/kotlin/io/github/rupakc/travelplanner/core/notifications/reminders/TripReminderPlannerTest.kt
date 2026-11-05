package io.github.rupakc.travelplanner.core.notifications.reminders

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripReminderPlannerTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val now = ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, berlin)

    @Test
    fun `a week before, the day before, each trip morning and the last day`() {
        val r = TripReminderPlanner.plan(1, LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-13"), now)
        assertEquals(
            listOf(
                ReminderType.WEEK_BEFORE,
                ReminderType.DAY_BEFORE,
                ReminderType.TRIP_START,
                ReminderType.TRIP_DAY,
                ReminderType.TRIP_DAY,
                ReminderType.LAST_DAY,
            ),
            r.map { it.type },
        )
        assertEquals(ZonedDateTime.of(2026, 12, 3, 9, 0, 0, 0, berlin), r[0].fireAt)
        assertEquals(ZonedDateTime.of(2026, 12, 9, 18, 0, 0, 0, berlin), r[1].fireAt)
        assertEquals(LocalTime.of(8, 0), r[2].fireAt.toLocalTime())
        assertEquals(listOf(0, 0, 1, 2, 3, 4), r.map { it.dayNumber })
        assertEquals("trip-reminder-1-TRIP_START-2026-12-10", r[2].uniqueName)
    }

    @Test
    fun `only reminders still ahead are planned`() {
        val r = TripReminderPlanner.plan(1, LocalDate.parse("2026-11-08"), LocalDate.parse("2026-11-09"), now)
        assertEquals(listOf(ReminderType.DAY_BEFORE, ReminderType.TRIP_START, ReminderType.LAST_DAY), r.map { it.type })
        val today = TripReminderPlanner.plan(1, LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-06"), now)
        // 08:00 today has passed by 4 h at noon (beyond the 2 h grace); only tomorrow's last-day reminder is left.
        assertEquals(listOf(ReminderType.LAST_DAY), today.map { it.type })
        val justAfter = ZonedDateTime.of(2026, 11, 5, 9, 30, 0, 0, berlin)
        val grace = TripReminderPlanner.plan(1, LocalDate.parse("2026-11-05"), LocalDate.parse("2026-11-06"), justAfter)
        assertEquals(listOf(ReminderType.TRIP_START, ReminderType.LAST_DAY), grace.map { it.type })
        assertTrue(TripReminderPlanner.plan(1, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"), now).isEmpty())
    }

    @Test
    fun `one-way trips get a start-day reminder only and long trips are capped`() {
        val oneWay = TripReminderPlanner.plan(1, LocalDate.parse("2026-12-10"), null, now)
        assertEquals(listOf(ReminderType.WEEK_BEFORE, ReminderType.DAY_BEFORE, ReminderType.TRIP_START), oneWay.map { it.type })
        val long = TripReminderPlanner.plan(1, LocalDate.parse("2026-12-10"), LocalDate.parse("2027-03-10"), now)
        assertEquals(TripReminderPlanner.MAX_TRIP_DAYS, long.count { it.dayNumber in 1..TripReminderPlanner.MAX_TRIP_DAYS })
        // The checkout reminder survives the cap.
        assertEquals(LocalDate.parse("2027-03-10"), long.last { it.type == ReminderType.LAST_DAY }.date)
    }

    @Test
    fun `mornings stay at 08 00 local across a DST change`() {
        val march = ZonedDateTime.of(2026, 3, 1, 12, 0, 0, 0, berlin)
        // Clocks go forward on 29 March 2026 in Berlin.
        val r = TripReminderPlanner.plan(2, LocalDate.parse("2026-03-28"), LocalDate.parse("2026-03-30"), march).filter { it.dayNumber > 0 }
        assertTrue(r.all { it.fireAt.toLocalTime() == LocalTime.of(8, 0) })
        assertEquals(Duration.ofHours(23), Duration.between(r[0].fireAt, r[1].fireAt))
        assertEquals(Duration.ofHours(24), Duration.between(r[1].fireAt, r[2].fireAt))
    }

    @Test
    fun `a wall time in a DST gap moves forward, never earlier`() {
        // 02:30 doesn't exist on 8 March 2026 in New York; ZonedDateTime.of shifts it to 03:30.
        val ny = ZoneId.of("America/New_York")
        val shifted = ZonedDateTime.of(LocalDateTime.of(2026, 3, 8, 2, 30), ny)
        assertEquals(LocalTime.of(3, 30), shifted.toLocalTime())
        // Our times (08:00 / 09:00 / 18:00) are outside every real-world gap, so they are kept as-is.
        val r = TripReminderPlanner.plan(3, LocalDate.parse("2026-03-08"), LocalDate.parse("2026-03-09"), ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ny))
        assertTrue(r.filter { it.dayNumber > 0 }.all { it.fireAt.toLocalTime() == LocalTime.of(8, 0) })
    }

    @Test
    fun `the same trip gets 08 00 local wherever the phone is`() {
        val tokyo = ZoneId.of("Asia/Tokyo")

        fun plan(json: String) = Plan(id = 1, name = "Trip", searchData = TravelJson.parseToJsonElement(json) as JsonObject)
        val p = plan("""{"origin":"BER","destination":"Tokyo","departure_date":"2026-12-10","return_date":"2026-12-11","nationality":""}""")
        val inBerlin = remindersFor(listOf(p), now).first { it.second.type == ReminderType.TRIP_START }.second.fireAt
        val inTokyo = remindersFor(listOf(p), now.withZoneSameInstant(tokyo)).first { it.second.type == ReminderType.TRIP_START }.second.fireAt
        assertEquals(LocalTime.of(8, 0), inBerlin.toLocalTime())
        assertEquals(LocalTime.of(8, 0), inTokyo.toLocalTime())
        assertEquals(Duration.ofHours(8), Duration.between(inTokyo, inBerlin))
    }

    @Test
    fun `reminders for saved plans skip plans without dates`() {
        fun plan(id: Long, json: String) = Plan(id = id, name = "Trip $id", searchData = TravelJson.parseToJsonElement(json) as JsonObject)
        val dated = plan(1, """{"origin":"BER","destination":"Lisbon, Portugal","departure_date":"2026-12-10","return_date":"2026-12-11","nationality":""}""")
        val undated = plan(2, """{"origin":"BER"}""")
        val all = remindersFor(listOf(dated, undated), now)
        assertTrue(all.all { it.first.id == 1L })
        assertEquals(4, all.size)
        assertEquals("Lisbon", destinationOf(dated))
        assertEquals("Trip 2", destinationOf(undated))
    }
}
