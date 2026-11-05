package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the web's `utils/stressTestPayload.test.js`. */
class StressTestPayloadsTest {
    private fun slot(tod: String, activity: String, cost: Int) =
        """{"time_of_day":"$tod","activity":"$activity","location":"$activity St","duration_hours":2,"notes":"About $activity","estimated_cost_usd":$cost}"""

    private val itin: ItineraryData =
        TravelJson.decodeFromString(
            ItineraryData.serializer(),
            """{"days":[
              {"day_number":1,"date":"2027-05-01","city":"Lisbon","slots":[${slot(
                "morning",
                "Castle",
                10,
            )},${slot("afternoon", "Tram 28", 5)},${slot("evening", "Fado", 30)}]},
              {"day_number":2,"date":"2027-05-02","city":"Lisbon","slots":[${slot("morning", "Belem", 15)},${slot("afternoon", "LX Factory", 0)}]}
            ],"total_estimated_cost_usd":60,"verification":{"level":"unverified"}}""",
        )

    private val search =
        TravelSearchRequest(
            origin = "LHR",
            destination = "Lisbon",
            departureDate = "2027-05-01",
            returnDate = "",
            interests = listOf("food"),
            nationality = "British",
            budgetUsd = 2000.0,
            numTravelers = 2,
        )

    private fun obj(text: String) = TravelJson.parseToJsonElement(text).jsonObject

    private val flights = obj("""{"results":[{"price_usd":120}],"verification":{"level":"partial","checks":[]}}""")
    private val visa = obj("""{"visa_required":false,"verification":{"level":"verified"}}""")
    private val weather = obj("""{"daily":[{"date":"2027-05-01"}],"verification":{"level":"unverified"}}""")

    private fun selections(layout: ItineraryLayout? = null, edits: Map<String, JsonObject> = emptyMap()) =
        PlanSelections(itineraryLayout = layout, itineraryEdits = edits)

    private fun build(sel: PlanSelections, it: ItineraryData = itin) = StressTestPayloads.build(search, it, sel, flights, visa, weather)

    private fun JsonObject.days() = getValue("days").jsonArray.map { it.jsonObject }

    private fun JsonObject.slots() = getValue("slots").jsonArray.map { it.jsonObject }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    private fun activities(p: JsonObject) = p.days().map { d -> d.slots().map { it.str("activity") } }

    private fun edit(vararg pairs: Pair<String, Any?>) =
        JsonObject(
            pairs.associate { (k, v) ->
                k to (
                    if (v is Number) {
                        JsonPrimitive(v)
                    } else if (v is String) {
                        JsonPrimitive(v)
                    } else {
                        JsonNull
                    }
                )
            },
        )

    private val moved get() = ItineraryLayouts.moveSlot(itin, null, "1-morning", 2, "evening")

    @Test
    fun `sends the search request with the ARRANGED days`() {
        val p = build(selections(moved))
        assertEquals("LHR", p.request.origin)
        assertEquals("Lisbon", p.request.destination)
        assertEquals(2, p.request.numTravelers)
        assertEquals(listOf(listOf("Tram 28", "Fado"), listOf("Belem", "LX Factory", "Castle")), activities(p.itinerary))
        assertEquals("evening", p.itinerary.days()[1].slots()[2].str("time_of_day"))
        assertEquals("35.0", p.itinerary.days()[0].str("daily_estimated_cost_usd"))
    }

    @Test
    fun `applies itinerary_edits to activity, location and cost`() {
        val edits = mapOf("1-morning" to edit("activity" to "Castle sunset", "location" to "Alfama", "cost" to 25), "2-morning" to edit("cost" to null))
        val p = build(selections(moved, edits))
        val castle = p.itinerary.days()[1].slots()[2]
        assertEquals("Castle sunset", castle.str("activity"))
        assertEquals("Alfama", castle.str("location"))
        assertEquals(25.0, castle.str("estimated_cost_usd")!!.toDouble(), 0.0)
        assertEquals("evening", castle.str("time_of_day"))
        assertFalse(castle.containsKey("notes"))
        val belem = p.itinerary.days()[1].slots()[0]
        assertEquals(15.0, belem.str("estimated_cost_usd")!!.toDouble(), 0.0)
        assertEquals("About Belem", belem.str("notes"))
    }

    @Test
    fun `keeps the original name and notes for an empty-string activity edit`() {
        val s = build(selections(edits = mapOf("1-morning" to edit("activity" to "")))).itinerary.days()[0].slots()[0]
        assertEquals("Castle", s.str("activity"))
        assertEquals("About Castle", s.str("notes"))
    }

    @Test
    fun `strips the client-only key from every slot`() {
        for (sel in listOf(selections(), selections(moved))) {
            val slots = build(sel).itinerary.days().flatMap { it.slots() }
            assertTrue(slots.isNotEmpty())
            slots.forEach { assertFalse(it.containsKey("key")) }
        }
    }

    @Test
    fun `excludes removed activities and layout bookkeeping`() {
        val p = build(selections(ItineraryLayouts.removeSlot(itin, null, "1-afternoon")))
        assertEquals(listOf(listOf("Castle", "Fado"), listOf("Belem", "LX Factory")), activities(p.itinerary))
        assertFalse(p.itinerary.toString().contains("Tram 28"))
        assertFalse(p.itinerary.containsKey("removed"))
        assertFalse(p.itinerary.containsKey("changed"))
        assertEquals(55.0, p.itinerary.str("total_estimated_cost_usd")!!.toDouble(), 0.0)
    }

    @Test
    fun `passes flights, visa and weather without verification objects`() {
        val p = build(selections())
        assertEquals(obj("""{"results":[{"price_usd":120}]}"""), p.flights)
        assertEquals(obj("""{"visa_required":false}"""), p.visa)
        assertEquals(obj("""{"daily":[{"date":"2027-05-01"}]}"""), p.weather)
        assertFalse(p.itinerary.containsKey("verification"))
        assertTrue(flights.containsKey("verification"))
    }

    @Test
    fun `sends null for missing context sections`() {
        val p = StressTestPayloads.build(search, itin, selections())
        assertNull(p.flights)
        assertNull(p.visa)
        assertNull(p.weather)
    }

    @Test
    fun `normalises return_date`() {
        assertNull(build(selections()).request.returnDate)
        assertEquals("2027-05-03", StressTestPayloads.build(search.copy(returnDate = "2027-05-03"), itin, selections()).request.returnDate)
    }

    @Test
    fun `sends each slot's estimated start, end and travel from the previous stop`() {
        val p = build(selections(moved))
        assertEquals(
            listOf(listOf("Tram 28", "13:30", "15:30"), listOf("Fado", "18:30", "20:30")),
            p.itinerary.days()[0].slots().map { listOf(it.str("activity"), it.str("start_time"), it.str("end_time")) },
        )
        val castle = p.itinerary.days()[1].slots()[2]
        assertEquals("18:30", castle.str("start_time"))
        assertEquals("20:30", castle.str("end_time"))
        assertEquals(JsonNull, castle["transit_from_prev"])
        assertEquals(JsonArray(emptyList()), p.itinerary.days()[0]["schedule_warnings"])
    }

    @Test
    fun `recheckFingerprint changes when the layout or an edit changes, and only then`() {
        val base = StressTestPayloads.recheckFingerprint(itin, selections(moved))
        assertEquals(base, StressTestPayloads.recheckFingerprint(itin, selections(moved)))
        assertNotEquals(base, StressTestPayloads.recheckFingerprint(itin, selections(ItineraryLayouts.removeSlot(itin, moved, "1-evening"))))
        assertNotEquals(base, StressTestPayloads.recheckFingerprint(itin, selections(moved, mapOf("1-evening" to edit("location" to "Bairro Alto")))))
    }

    private val outOfOrder: ItineraryData =
        TravelJson.decodeFromString(
            ItineraryData.serializer(),
            """{"days":[
              {"day_number":1,"date":"2027-05-01","city":"Lisbon","slots":[
                {"time_of_day":"afternoon","activity":"Gulbenkian","duration_hours":2,"estimated_cost_usd":10,"lat":38.737,"lng":-9.154},
                {"time_of_day":"morning","activity":"Oceanario","duration_hours":2,"estimated_cost_usd":20,"lat":38.7635,"lng":-9.0937}]},
              {"day_number":2,"date":"2027-05-02","city":"Lisbon","slots":[${slot("morning", "Belem", 15)},${slot("afternoon", "LX Factory", 0)}]}
            ]}""",
        )

    @Test
    fun `lists an out-of-order day as scheduled, so travel is from the stop listed just before`() {
        val day1 = build(selections(), outOfOrder).itinerary.days()[0]
        assertEquals(listOf("Oceanario", "Gulbenkian"), day1.slots().map { it.str("activity") })
        assertEquals("09:00", day1.slots()[0].str("start_time"))
        assertEquals(JsonNull, day1.slots()[0]["transit_from_prev"])
        assertEquals("transit", day1.slots()[1]["transit_from_prev"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
        assertEquals("13:30", day1.slots()[1].str("start_time"))
    }

    @Test
    fun `fingerprints the scheduled order`() {
        val reordered = outOfOrder.copy(days = listOf(outOfOrder.days[0].copy(slots = outOfOrder.days[0].slots.reversed()), outOfOrder.days[1]))
        assertEquals(StressTestPayloads.recheckFingerprint(outOfOrder, selections()), StressTestPayloads.recheckFingerprint(reordered, selections()))
    }
}
