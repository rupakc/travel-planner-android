package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.domain.export.PlanIcs
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanItineraryTest {
    private fun selections(json: String) = TravelJson.decodeFromString(PlanSelections.serializer(), json)

    private val trip =
        """{"days":[
          {"day_number":1,"date":"2027-04-01","city":"Tokyo","slots":[
            {"time_of_day":"morning","activity":"Market","duration_hours":2},
            {"time_of_day":"evening","activity":"Shibuya"}]},
          {"day_number":2,"date":"2027-04-02","city":"Tokyo","slots":[
            {"time_of_day":"morning","activity":"Meiji Shrine"},{"time_of_day":"evening","activity":"Izakaya"}]}]}"""

    @Test
    fun `snapshot is arranged by the layout and scheduled, exactly like the export`() {
        val itinerary = TravelJson.decodeFromString(io.github.rupakc.travelplanner.core.model.section.ItineraryData.serializer(), trip)
        val fp = ItineraryLayouts.fingerprint(itinerary)
        val sel =
            selections(
                """{"itinerary":$trip,"itinerary_layout":{"fingerprint":"$fp","positions":{"1-morning":"2-evening","2-evening":"1-morning"}}}""",
            )
        val days = PlanItinerary.days(sel)
        assertEquals(listOf("Izakaya", "Shibuya"), days[0].slots.map { it.slot.activity })
        assertEquals("2-evening", days[0].slots[0].key)
        assertEquals("09:00", days[0].slots[0].startTime)
        assertTrue(days.all { it.scheduled })

        // Same days, same order and times as the .ics export's planItineraryDays.
        val exported = PlanIcs.planItineraryDays(PlanSelectionsSerializer.toJson(sel), null)
        assertEquals(
            exported.map { d ->
                d["slots"]!!.jsonArray.map { it.jsonObject["key"]!!.jsonPrimitive.content to it.jsonObject["start_time"]?.jsonPrimitive?.content }
            },
            days.map { d -> d.slots.map { it.key to it.startTime } },
        )
    }

    @Test
    fun `the live itinerary wins over the snapshot`() {
        val live = TravelJson.parseToJsonElement("""{"days":[{"day_number":1,"slots":[{"time_of_day":"afternoon","activity":"Live"}]}]}""") as JsonObject
        val days = PlanItinerary.days(selections("""{"itinerary":$trip}"""), live)
        assertEquals("Live", days.single().slots.single().slot.activity)
    }

    @Test
    fun `picked slots are grouped by day in time order and keep their stored times`() {
        val sel =
            selections(
                """{"itinerary_slots":[
                {"key":"2-evening","day_number":2,"time_of_day":"evening","activity":"B","start_time":"18:30","end_time":"20:30"},
                {"key":"1-afternoon","day_number":1,"time_of_day":"afternoon","activity":"A2","date":"2027-04-01"},
                {"key":"1-morning","day_number":1,"time_of_day":"morning","activity":"A1"}]}""",
            )
        val days = PlanItinerary.days(sel)
        assertEquals(listOf(1, 2), days.map { it.day.dayNumber })
        assertEquals(listOf("A1", "A2"), days[0].slots.map { it.slot.activity })
        // Web planItineraryDays: the day's date comes from the first picked slot of that day.
        assertEquals("2027-04-01", days[0].day.date)
        assertFalse(days[1].scheduled)
        assertEquals("18:30", days[1].slots.single().startTime)
        assertNull(days[0].slots[0].startTime)
    }

    @Test
    fun `a day that fails to decode keeps its slots, and the views match the export day for day`() {
        // link_extras must be a list: day 2 fails whole-day decoding but keeps its number and slots.
        val snapshot =
            """{"days":[{"day_number":1,"slots":[{"time_of_day":"morning","activity":"A"}]},
            {"day_number":2,"link_extras":"oops","slots":[{"time_of_day":"evening","activity":"B"}]}, "not a day"]}"""
        val sel = selections("""{"itinerary":$snapshot,"itinerary_slots":[{"key":"9-morning","day_number":9,"time_of_day":"morning","activity":"Picked"}]}""")
        val days = PlanItinerary.days(sel)
        assertEquals(listOf(1, 2), days.map { it.day.dayNumber })
        assertEquals(listOf("A", "B"), days.flatMap { d -> d.slots.map { it.slot.activity } })
        val exported = PlanIcs.planItineraryDays(PlanSelectionsSerializer.toJson(sel), null)
        assertEquals(exported.map { d -> d["slots"]!!.jsonArray.map { it.jsonObject["start_time"]?.jsonPrimitive?.content } }, days.map { d -> d.slots.map { it.startTime } })
    }

    @Test
    fun `no itinerary at all is empty`() {
        assertTrue(PlanItinerary.days(PlanSelections()).isEmpty())
    }
}
