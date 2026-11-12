package io.github.rupakc.travelplanner.core.domain.journey

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden cases from the shared fixture `trip_phase_cases.json` (Trips-API, Phases). */
class TripPhaseRulesTest {
    private fun JsonObject.str(key: String): String? = (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content

    private fun JsonObject.intOrNull(key: String): Int? = (get(key) as? JsonPrimitive)?.takeUnless { it is JsonNull }?.int

    private fun cases(): JsonArray =
        Json.parseToJsonElement(
            checkNotNull(javaClass.getResourceAsStream("/journey/trip_phase_cases.json")) { "fixture missing" }
                .bufferedReader().readText(),
        ).jsonArray

    @Test
    fun `every golden case passes`() {
        val all = cases()
        assertTrue(all.size >= 24)
        for (el in all) {
            val c = el.jsonObject
            val name = c.str("name")
            val start = c.str("start")?.let(LocalDate::parse)
            val end = c.str("end")?.let(LocalDate::parse)
            val override = c.str("override")?.let { TripPhase.fromWire(it) }
            val today = LocalDate.parse(c.str("today"))
            val prep = c["prep_complete"]!!.jsonPrimitive.boolean
            assertEquals(name, TripPhase.fromWire(c.str("phase")!!), TripPhaseRules.effective(start, end, override, today, prep))
            assertEquals(name, c.intOrNull("day_index"), TripPhaseRules.dayIndex(start, end, today))
            assertEquals(name, c.intOrNull("days_to_start"), TripPhaseRules.daysToStart(start, today))
        }
    }

    @Test
    fun `wire names round trip and unknown is null`() {
        TripPhase.entries.forEach { assertEquals(it, TripPhase.fromWire(it.wire)) }
        assertEquals("on_the_road", TripPhase.OnTheRoad.wire)
        assertEquals(null, TripPhase.fromWire("nope"))
    }
}
