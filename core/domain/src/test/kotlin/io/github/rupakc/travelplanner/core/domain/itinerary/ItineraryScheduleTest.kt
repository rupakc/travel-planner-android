package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.listedInScheduleOrder
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.pickedTimes
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.scheduleDays
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.slotWhen
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.syncPlannedSlotTimes
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.timeRange
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule.transitLabel
import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the web's `utils/schedule.test.js` and `utils/geo.test.js`. */
class ItineraryScheduleTest {
    // A point `km` north of `lat` (1° of latitude ≈ 111.19 km)
    private fun north(lat: Double, km: Double) = lat + km / (6371 * Math.PI / 180)

    private fun slot(
        tod: String,
        activity: String,
        hours: Double? = null,
        lat: Double? = null,
        lng: Double? = null,
        cost: Double? = null,
        location: String? = null,
    ) = ItinerarySlot(timeOfDay = tod, activity = activity, durationHours = hours, lat = lat, lng = lng, estimatedCostUsd = cost, location = location)

    private fun day(n: Int, slots: List<ItinerarySlot>, theme: String? = null) = ItineraryDay(dayNumber = n, city = "Lisbon", slots = slots, theme = theme)

    private fun arranged(it: ItineraryData, layout: ItineraryLayout? = null, edits: Map<String, SlotEdit> = emptyMap()) =
        ItineraryLayouts.arrange(it, layout, edits).days

    private fun one(vararg days: ItineraryDay) = ItineraryData(days = days.toList())

    private fun times(d: ScheduledDay) = d.slots.map { listOf(it.startTime, it.endTime) }

    private fun at(km: Double) = north(38.7, km / 1.3) to -9.14

    @Test
    fun `haversine is zero for the same point and measures great-circle km`() {
        assertEquals(0.0, haversineKm(35.68, 139.76, 35.68, 139.76), 0.0)
        assertEquals(343.5, haversineKm(51.5074, -0.1278, 48.8566, 2.3522), 0.5)
        assertEquals(111.19, haversineKm(0.0, 0.0, 1.0, 0.0), 0.05)
    }

    @Test
    fun `starts each bucket at its anchor with default durations`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A"), slot("afternoon", "B"), slot("evening", "C")))))).single()
        assertEquals(listOf(listOf("09:00", "12:00"), listOf("13:30", "16:30"), listOf("18:30", "21:00")), times(d))
        assertTrue(d.warnings.isEmpty())
    }

    @Test
    fun `uses duration_hours when finite and between 0_25 and 12`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 1.5), slot("afternoon", "B", 0.25), slot("evening", "C", 4.0)))))).single()
        assertEquals(listOf(listOf("09:00", "10:30"), listOf("13:30", "13:45"), listOf("18:30", "22:30")), times(d))
    }

    @Test
    fun `falls back to the default for invalid durations`() {
        // web also lists '2' (string) — lenient decoding reads it as 2 h on Android (documented deviation)
        for (hours in listOf(0.0, 0.1, 13.0, -2.0, Double.NaN, Double.POSITIVE_INFINITY, null)) {
            val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", hours)))))).single()
            assertEquals("12:00", d.slots[0].endTime)
        }
    }

    @Test
    fun `keeps every other slot and day field`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", cost = 5.0, location = "X")), theme = "T")))).single()
        assertEquals(1, d.day.dayNumber)
        assertEquals("Lisbon", d.day.city)
        assertEquals("T", d.day.theme)
        assertEquals("1-morning", d.slots[0].key)
        assertEquals("X", d.slots[0].slot.location)
        assertEquals(5.0, d.slots[0].slot.estimatedCostUsd)
    }

    @Test
    fun `handles missing or empty input`() {
        assertTrue(scheduleDays(emptyList()).isEmpty())
    }

    @Test
    fun `gives the first slot of a day no transit`() {
        val (lat, lng) = at(0.0)
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", lat = lat, lng = lng)))))).single()
        assertNull(d.slots[0].transitFromPrev)
    }

    @Test
    fun `walks short hops at 4_5 km per hour, rounded up to 5 min`() {
        val (a1, a2) = at(0.0)
        val (b1, b2) = at(0.8)
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 3.5, a1, a2), slot("afternoon", "B", lat = b1, lng = b2)))))).single()
        assertEquals(Transit(15, Transit.Mode.WALK, 0.8), d.slots[1].transitFromPrev)
        assertEquals("13:30", d.slots[1].startTime)
    }

    @Test
    fun `takes transit for longer hops at 20 km per hour plus 10 min`() {
        val (a1, a2) = at(0.0)
        val (b1, b2) = at(4.0)
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", lat = a1, lng = a2), slot("afternoon", "B", lat = b1, lng = b2)))))).single()
        assertEquals(Transit(25, Transit.Mode.TRANSIT, 4.0), d.slots[1].transitFromPrev)
    }

    @Test
    fun `pushes a start back when the previous stop plus travel runs past the anchor`() {
        val (a1, a2) = at(0.0)
        val (b1, b2) = at(4.0)
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 4.5, a1, a2), slot("afternoon", "B", lat = b1, lng = b2)))))).single()
        assertEquals(listOf("13:55", "16:55"), times(d)[1])
    }

    @Test
    fun `assumes a 20-minute buffer when either stop lacks coordinates`() {
        val (a1, a2) = at(0.0)
        val d =
            scheduleDays(
                arranged(one(day(1, listOf(slot("morning", "A", 4.5, a1, a2), slot("afternoon", "B", lng = -9.1), slot("evening", "C", 0.5, lng = 1.0))))),
            ).single()
        assertNull(d.slots[1].transitFromPrev)
        assertEquals("13:50", d.slots[1].startTime)
        assertNull(d.slots[2].transitFromPrev)
    }

    @Test
    fun `ignores out-of-range coordinates`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", lat = 95.0, lng = 0.0), slot("afternoon", "B", lat = 0.0, lng = 0.0)))))).single()
        assertNull(d.slots[1].transitFromPrev)
    }

    @Test
    fun `warns when a start is pushed more than 45 min past its anchor`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 5.0), slot("afternoon", "B")))))).single()
        assertEquals("14:20", d.slots[1].startTime)
        assertEquals(listOf("B starts at 14:20 — later than planned"), d.warnings.map { it.english })
    }

    @Test
    fun `does not warn at exactly 45 minutes late`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 4.9166666667), slot("afternoon", "B")))))).single()
        assertEquals("14:15", d.slots[1].startTime)
        assertTrue(d.warnings.isEmpty())
    }

    @Test
    fun `warns when the day runs past 23_00, not at exactly 23_00`() {
        val d = scheduleDays(arranged(one(day(3, listOf(slot("evening", "Fado", 5.0)))))).single()
        assertEquals(listOf(listOf("18:30", "23:30")), times(d))
        assertEquals(listOf(ScheduleWarning.RunsLate(3)), d.warnings)
        assertEquals("Day 3 runs past 23:00", d.warnings.single().english)
        val ok = scheduleDays(arranged(one(day(1, listOf(slot("evening", "Fado", 4.5)))))).single()
        assertEquals("23:00", ok.slots[0].endTime)
        assertTrue(ok.warnings.isEmpty())
    }

    @Test
    fun `wraps clock times past midnight`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("evening", "Night tour", 8.0)))))).single()
        assertEquals(listOf(listOf("18:30", "02:30")), times(d))
    }

    @Test
    fun `names the EDITED activity in warnings and ignores edits for timing`() {
        val itinerary = one(day(1, listOf(slot("morning", "A", 5.0), slot("afternoon", "B"))))
        val edits = mapOf("1-afternoon" to SlotEdit(activity = "Tile Museum"))
        val d = scheduleDays(arranged(itinerary, null, edits), edits).single()
        assertEquals(listOf("Tile Museum starts at 14:20 — later than planned"), d.warnings.map { it.english })
        assertEquals("B", d.slots[1].slot.activity)
    }

    private val lisbon =
        one(
            day(1, listOf(slot("morning", "Castle", 2.0), slot("afternoon", "Tram", 1.0), slot("evening", "Fado", 2.0))),
            day(2, listOf(slot("morning", "Belem", 4.0), slot("afternoon", "LX Factory", 2.0))),
        )

    @Test
    fun `times a moved activity at its new position, keeping its own duration`() {
        val layout = ItineraryLayouts.moveSlot(lisbon, null, "1-morning", 2, "evening")
        val days = scheduleDays(arranged(lisbon, layout))
        val castle = days[1].slots.first { it.key == "1-morning" }
        assertEquals("evening", castle.slot.timeOfDay)
        assertEquals(listOf("18:30", "20:30"), listOf(castle.startTime, castle.endTime))
        assertEquals(listOf("1-afternoon", "1-evening"), days[0].slots.map { it.key })
        assertEquals(listOf(listOf("13:30", "14:30"), listOf("18:30", "20:30")), times(days[0]))
    }

    @Test
    fun `swaps - both activities take their new anchors`() {
        val d = scheduleDays(arranged(lisbon, ItineraryLayouts.moveSlot(lisbon, null, "1-morning", 1, "evening")))[0]
        assertEquals(
            listOf(listOf("Fado", "09:00", "11:00"), listOf("Tram", "13:30", "14:30"), listOf("Castle", "18:30", "20:30")),
            d.slots.map { listOf(it.slot.activity, it.startTime, it.endTime) },
        )
    }

    @Test
    fun `does not schedule removed activities`() {
        val days = scheduleDays(arranged(lisbon, ItineraryLayouts.removeSlot(lisbon, null, "2-morning")))
        assertEquals(listOf("LX Factory"), days[1].slots.map { it.slot.activity })
        assertNull(days[1].slots[0].transitFromPrev)
        assertEquals("13:30", days[1].slots[0].startTime)
    }

    @Test
    fun `times slots in rank order even when listed out of order`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("evening", "C", 1.0), slot("morning", "A", 12.0)))))).single()
        assertEquals(listOf(listOf("C", "21:20"), listOf("A", "09:00")), d.slots.map { listOf(it.slot.activity, it.startTime) })
        assertNull(d.slots[0].transitFromPrev)
        assertEquals(listOf("C starts at 21:20 — later than planned"), d.warnings.map { it.english })
    }

    @Test
    fun `times a duplicate standard slot right after the stop before it`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 1.0), slot("morning", "A2", 1.0), slot("afternoon", "B")))))).single()
        assertEquals(listOf(listOf("09:00", "10:00"), listOf("10:20", "11:20"), listOf("13:30", "16:30")), times(d))
        assertTrue(d.warnings.isEmpty())
    }

    @Test
    fun `times a non-standard slot after the stop before it, untimed when nothing precedes it`() {
        val d =
            scheduleDays(
                arranged(
                    one(
                        day(
                            1,
                            listOf(
                                slot("dawn", "Sunrise", lat = 10.0, lng = 10.0),
                                slot("morning", "A", 4.5, 10.0, 10.0),
                                slot("late night", "Club"),
                                slot("afternoon", "B"),
                            ),
                        ),
                    ),
                ),
            ).single()
        assertFalse(d.slots[0].timed)
        assertNull(d.slots[0].startTime)
        assertNull(d.slots[1].transitFromPrev)
        assertEquals(listOf(listOf("09:00", "13:30"), listOf("13:50", "15:50"), listOf("16:10", "19:10")), times(d).drop(1))
        assertEquals(listOf("B starts at 16:10 — later than planned"), d.warnings.map { it.english })
    }

    @Test
    fun `leaves days with no standard slots untouched`() {
        val days = arranged(one(day(1, listOf(slot("dawn", "Sunrise"))), day(2, emptyList())))
        val out = scheduleDays(days)
        assertSame(days[0], out[0].day)
        assertFalse(out[0].scheduled)
        assertFalse(out[1].scheduled)
    }

    @Test
    fun `counts the days past the trip day each start and end falls on`() {
        val d = scheduleDays(arranged(one(day(1, listOf(slot("morning", "A", 12.0), slot("afternoon", "B", 12.0), slot("evening", "C", 12.0)))))).single()
        assertEquals(
            listOf(listOf("09:00", 0, "21:00", 0), listOf("21:20", 0, "09:20", 1), listOf("09:40", 1, "21:40", 1)),
            d.slots.map { listOf(it.startTime, it.startDayOffset, it.endTime, it.endDayOffset) },
        )
    }

    @Test
    fun `listedInScheduleOrder`() {
        assertTrue(listedInScheduleOrder(day(1, listOf(slot("morning", "A"), slot("lunch", "L"), slot("evening", "C")))))
        assertTrue(listedInScheduleOrder(day(1, emptyList())))
        assertFalse(listedInScheduleOrder(day(1, listOf(slot("evening", "C"), slot("morning", "A")))))
    }

    @Test
    fun `timeRange and transitLabel`() {
        assertEquals("09:00–12:00", timeRange("09:00", "12:00"))
        assertEquals("", timeRange("09:00", null))
        assertEquals("", timeRange(null, null))
        assertEquals("🚶 15 min walk · 0.8 km", transitLabel(Transit(15, Transit.Mode.WALK, 0.8)))
        assertEquals("🚶 5 min walk", transitLabel(Transit(5, Transit.Mode.WALK, 0.0)))
        assertEquals("🚶 5 min walk · 0.1 km", transitLabel(Transit(5, Transit.Mode.WALK, 0.1)))
        assertEquals("🚇 ~25 min by transit", transitLabel(Transit(25, Transit.Mode.TRANSIT, 4.0)))
        assertEquals("", transitLabel(null))
    }

    private val it0 = one(day(1, listOf(slot("morning", "A", 1.5), slot("afternoon", "B"))))

    private fun sched(layout: ItineraryLayout? = null) = scheduleDays(arranged(it0, layout))

    @Test
    fun `pickedTimes snapshots the scheduled times and duration, empty when untimed`() {
        val expected =
            buildJsonObject {
                put("start_time", JsonPrimitive("09:00"))
                put("end_time", JsonPrimitive("10:30"))
                put("start_day_offset", JsonPrimitive(0))
                put("end_day_offset", JsonPrimitive(0))
                put("duration_hours", JsonPrimitive(1.5))
            }
        assertEquals(expected, pickedTimes(sched()[0].slots[0]))
        assertTrue(pickedTimes(ScheduledSlot(ItinerarySlot(timeOfDay = "late night"))).isEmpty())
    }

    @Test
    fun `syncPlannedSlotTimes refreshes times after a move and keeps identity when unchanged`() {
        val first = sched()[0].slots[0]
        val picked = listOf(ItinerarySlot(key = "1-morning", dayNumber = 1, timeOfDay = "morning", activity = "A").patched(pickedTimes(first)))
        assertSame(picked, syncPlannedSlotTimes(picked, sched()))
        val moved = sched(ItineraryLayouts.moveSlot(it0, null, "1-morning", 1, "afternoon"))
        val out = syncPlannedSlotTimes(picked, moved)
        assertNotSame(picked, out)
        assertEquals("13:30", out[0].startTime)
        assertEquals("15:00", out[0].endTime)
    }

    @Test
    fun `syncPlannedSlotTimes leaves entries with no scheduled counterpart alone`() {
        val picked = listOf(ItinerarySlot(key = "9-morning", dayNumber = 9, timeOfDay = "morning", activity = "X"))
        assertSame(picked, syncPlannedSlotTimes(picked, sched()))
    }

    @Test
    fun `slotWhen reads Day N, time of day and times`() {
        assertEquals("Day 2 · Afternoon · 13:30–16:00", slotWhen(ItinerarySlot(dayNumber = 2, timeOfDay = "afternoon", startTime = "13:30", endTime = "16:00")))
        assertEquals("Day 2 · Afternoon", slotWhen(ItinerarySlot(dayNumber = 2, timeOfDay = "afternoon")))
    }
}
