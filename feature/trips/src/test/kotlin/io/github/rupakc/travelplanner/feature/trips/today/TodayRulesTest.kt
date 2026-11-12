package io.github.rupakc.travelplanner.feature.trips.today

import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledDay
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayRulesTest {
    private fun slot(name: String, start: String?, end: String?, location: String? = null, city: String? = null) =
        ScheduledSlot(ItinerarySlot(activity = name, location = location, city = city, startTime = start, endTime = end))

    private fun day(n: Int, date: String?) = ScheduledDay(ItineraryDay(dayNumber = n, date = date), emptyList(), emptyList(), scheduled = false)

    @Test
    fun `the day is matched by local date first, then by the server day index`() {
        val days = listOf(day(1, "2027-04-01"), day(2, "2027-04-02"))
        assertEquals(2, TodayRules.dayFor(days, LocalDate.parse("2027-04-02"), 1)?.day?.dayNumber)
        assertEquals(2, TodayRules.dayFor(listOf(day(1, null), day(2, null)), LocalDate.parse("2027-04-09"), 2)?.day?.dayNumber)
        assertNull(TodayRules.dayFor(days, LocalDate.parse("2027-04-09"), null))
    }

    @Test
    fun `next up is the first slot still ahead`() {
        val slots = listOf(slot("Museum", "09:00", "11:00"), slot("Lunch", "12:00", "13:30"), slot("Tram", "15:00", "17:00"))
        assertEquals("Museum", TodayRules.nextUp(slots, LocalTime.of(8, 0))?.slot?.activity)
        assertEquals("Lunch", TodayRules.nextUp(slots, LocalTime.of(11, 30))?.slot?.activity)
        assertEquals("Tram", TodayRules.nextUp(slots, LocalTime.of(14, 0))?.slot?.activity)
        assertNull(TodayRules.nextUp(slots, LocalTime.of(20, 0)))
    }

    @Test
    fun `an untimed slot is always ahead`() {
        assertEquals("Stroll", TodayRules.nextUp(listOf(slot("Stroll", null, null)), LocalTime.of(23, 0))?.slot?.activity)
    }

    @Test
    fun `directions search the place name plus the city`() {
        assertEquals("Belem Tower, Lisbon", TodayRules.directionsQuery(slot("Visit", null, null, location = "Belem Tower"), "Lisbon"))
        assertEquals("Alfama, Lisbon", TodayRules.directionsQuery(slot("Alfama", null, null), "Lisbon"))
        assertEquals("Lisbon Cathedral", TodayRules.directionsQuery(slot("Lisbon Cathedral", null, null), "Lisbon"))
        assertEquals("Sintra Palace", TodayRules.directionsQuery(slot("x", null, null, "Sintra Palace", city = "Sintra"), "Lisbon"))
        assertNull(TodayRules.directionsQuery(ScheduledSlot(ItinerarySlot()), "Lisbon"))
    }

    @Test
    fun `server codes map to problems`() {
        assertEquals(TodayRules.CheckinProblem.OutOfTrip, TodayRules.problemOf("checkin_out_of_trip"))
        assertEquals(TodayRules.CheckinProblem.DayInvalid, TodayRules.problemOf("checkin_day_invalid"))
        assertEquals(TodayRules.CheckinProblem.Other, TodayRules.problemOf(null))
    }
}
