package io.github.rupakc.travelplanner.feature.plan.model

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.PlaceToSee
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun searchData(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

internal fun plan(
    id: Long,
    name: String,
    destination: String? = null,
    departure: String? = null,
    returnDate: String? = null,
    updatedAt: String? = null,
    shareToken: String? = null,
    selections: PlanSelections = PlanSelections(),
) = Plan(
    id = id,
    name = name,
    searchData =
        searchData(
            buildString {
                append("{")
                append(
                    listOfNotNull(
                        destination?.let { "\"destination\":\"$it\"" },
                        departure?.let { "\"departure_date\":\"$it\"" },
                        returnDate?.let { "\"return_date\":\"$it\"" },
                        "\"num_travelers\":2",
                    ).joinToString(","),
                )
                append("}")
            },
        ),
    selections = selections,
    updatedAt = updatedAt,
    shareToken = shareToken,
)

class PlanModelsTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun `summary reads destination, dates, travellers and timing from search data`() {
        val s = TripSummary.from(plan(1, "Japan", "Tokyo, Japan", "2027-04-01", "2027-04-08", "2026-11-01 10:00:00"), today)
        assertEquals("Tokyo, Japan", s.destination)
        assertEquals(LocalDate.of(2027, 4, 1), s.startDate)
        assertEquals(2, s.travellers)
        assertEquals(TripTiming.UPCOMING, s.timing)
        assertEquals(2026, s.updatedAt!!.year)
    }

    @Test
    fun `airport codes show as the cities the lookup found`() {
        val names = mapOf("LIS" to "Lisbon", "JFK" to "New York", "OPO" to "Porto")
        val p = plan(1, "Weekend", "LIS", "2027-04-01").let { it.copy(searchData = searchData("""{"origin":"JFK","destination":"LIS"}""")) }
        val s = TripSummary.from(p, today, names)
        assertEquals("Lisbon", s.destination)
        assertEquals("New York", s.origin)
        assertEquals("Porto → Lisbon", TripSummary.destinationLabel(searchData("""{"destination":"LIS","destinations":["OPO","LIS"]}"""), names))
        assertEquals("FAO", TripSummary.destinationLabel(searchData("""{"destination":"FAO"}"""), names))
        assertEquals(listOf("JFK", "LIS"), TripSummary.places(listOf(p, plan(2, "x", "Tokyo"))))
    }

    @Test
    fun `multi-city destinations join with arrows`() {
        val sd = searchData("""{"destination":"Rome","destinations":["Paris","Rome"]}""")
        assertEquals("Paris → Rome", TripSummary.destinationLabel(sd))
    }

    @Test
    fun `timing covers ongoing, past and undated`() {
        assertEquals(TripTiming.ONGOING, TripSummary.timing(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 9), today))
        assertEquals(TripTiming.PAST, TripSummary.timing(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9), today))
        assertEquals(TripTiming.UNDATED, TripSummary.timing(null, null, today))
    }

    private val trips =
        listOf(
            plan(1, "Spring in Japan", "Tokyo", "2027-04-01", "2027-04-08", "2026-10-01 10:00:00"),
            plan(2, "Andalucía", "Málaga, Spain", "2026-12-01", "2026-12-05", "2026-11-04 10:00:00", shareToken = "t"),
            plan(3, "Old trip", "Lisbon", "2026-03-01", "2026-03-05", "2026-03-06 10:00:00"),
            plan(4, "Someday", null, null, null, "2026-09-01 10:00:00"),
        ).map { TripSummary.from(it, today) }

    @Test
    fun `upcoming filter sorts soonest first`() {
        assertEquals(listOf(2L, 1L), TripQuery.apply(trips, "", TripFilter.UPCOMING, false, TripSort.TRIP_DATE).map { it.id })
    }

    @Test
    fun `past and all filters`() {
        assertEquals(listOf(3L), TripQuery.apply(trips, "", TripFilter.PAST, false, TripSort.TRIP_DATE).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), TripQuery.apply(trips, "", TripFilter.ALL, false, TripSort.TRIP_DATE).map { it.id })
    }

    @Test
    fun `search is case and accent insensitive over name and destination`() {
        assertEquals(listOf(2L), TripQuery.apply(trips, "malaga", TripFilter.ALL, false, TripSort.TRIP_DATE).map { it.id })
        assertEquals(listOf(1L), TripQuery.apply(trips, "JAPAN", TripFilter.ALL, false, TripSort.TRIP_DATE).map { it.id })
        assertTrue(TripQuery.apply(trips, "nowhere", TripFilter.ALL, false, TripSort.TRIP_DATE).isEmpty())
    }

    @Test
    fun `shared only and sort by updated`() {
        assertEquals(listOf(2L), TripQuery.apply(trips, "", TripFilter.ALL, true, TripSort.TRIP_DATE).map { it.id })
        assertEquals(listOf(2L, 1L, 4L, 3L), TripQuery.apply(trips, "", TripFilter.ALL, false, TripSort.UPDATED).map { it.id })
    }

    @Test
    fun `selection groups follow the drawer order and only include non-empty groups`() {
        val sel =
            TravelJson.decodeFromString(
                PlanSelections.serializer(),
                """{"flight":{"price_usd":900,"source":"estimate","outbound":{"airline":"ANA","flight_number":"NH9","origin":"JFK","destination":"HND"}},
                "hotel":{"name":"Park Hyatt","price_per_night_usd":450},
                "places_to_see":[{"name":"Senso-ji","admission_fee_usd":0}],
                "itinerary_notes":{"2-afternoon":"Bring cash"},
                "packing_list":{"checked_count":3,"total_items":10}}""",
            )
        val groups = SelectionGroups.of(sel)
        assertEquals(
            listOf(PlanCategory.FLIGHT, PlanCategory.HOTEL, PlanCategory.PLACES, PlanCategory.NOTES, PlanCategory.PACKING_LIST),
            groups.map { it.category },
        )
        val flight = groups[0].entries.single()
        assertEquals("ANA NH9", flight.title)
        assertEquals(listOf("JFK → HND"), flight.details)
        assertTrue(flight.isEstimate)
        val hotel = groups[1].entries.single()
        assertTrue(hotel.perNight)
        assertEquals(450.0, hotel.priceUsd!!, 0.0)
        assertTrue(groups[2].entries.single().isFree)
        assertNull(groups[2].entries.single().priceUsd)
        val note = groups[3].entries.single()
        assertEquals(2, note.dayNumber)
        assertEquals("afternoon", note.timeOfDay)
        assertEquals(3, groups[4].entries.single().packedCount)
    }

    @Test
    fun `remove takes out exactly the entry`() {
        val a = Activity(name = "A")
        val b = Activity(name = "B")
        val sel =
            PlanSelections(
                activities = listOf(a, b),
                hotel = Hotel(name = "H"),
                placesToSee = listOf(PlaceToSee(name = "P")),
                itineraryNotes = mapOf("1-morning" to "x"),
            )
        val groups = SelectionGroups.of(sel)
        val removeB = groups.first { it.category == PlanCategory.ACTIVITIES }.entries[1].removal!!
        assertEquals(listOf("A"), SelectionGroups.remove(sel, removeB).activities.map { it.name })
        assertNull(SelectionGroups.remove(sel, groups.first { it.category == PlanCategory.HOTEL }.entries[0].removal!!).hotel)
        assertTrue(SelectionGroups.remove(sel, Removal.Note("1-morning")).itineraryNotes.isEmpty())
        // Removing something already gone is a no-op, never a re-add
        val without = SelectionGroups.remove(sel, removeB)
        assertEquals(without, SelectionGroups.remove(without, removeB))
    }

    @Test
    fun `slot keys parse with duplicates`() {
        assertEquals(2 to "afternoon", SelectionGroups.parseSlotKey("2-afternoon#2"))
        assertEquals(null to null, SelectionGroups.parseSlotKey("junk"))
    }

    @Test
    fun `itinerary ui applies edits, notes and scheduled times`() {
        val sel =
            TravelJson.decodeFromString(
                PlanSelections.serializer(),
                """{"itinerary":{"days":[{"day_number":1,"date":"2027-04-01","theme":"Arrival","slots":[
                {"time_of_day":"morning","activity":"Market","duration_hours":2,"estimated_cost_usd":40}]}]},
                "itinerary_edits":{"1-morning":{"activity":"Toyosu"}},"itinerary_notes":{"1-morning":"Go early"}}""",
            )
        val itinerary = PlanItineraryUi.of(sel)
        val day = itinerary.days.single()
        assertEquals(1, day.day.dayNumber)
        assertEquals("Arrival", day.day.theme)
        val slot = day.slots.single()
        assertEquals("1-morning", slot.key)
        assertEquals("Toyosu", itinerary.edits.getValue("1-morning").activityOr(slot.slot.activity))
        assertEquals("09:00", slot.startTime)
        assertEquals("11:00", slot.endTime)
        assertEquals("Go early", itinerary.notes["1-morning"])
        assertEquals(40.0, slot.slot.estimatedCostUsd!!, 0.0)
        assertFalse(slot.slot.fromLink == true)
    }
}
