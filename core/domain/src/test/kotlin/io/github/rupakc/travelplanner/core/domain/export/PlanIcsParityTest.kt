package io.github.rupakc.travelplanner.core.domain.export

import java.io.File
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Golden .ics files for three realistic plans (saved snapshot with a layout, a multi-city live itinerary
 * with odd values, chat-picked slots only). They were recorded with the export's original raw-JSON
 * itinerary arrangement before it moved to the shared typed itinerary port, so the export stays byte-for-byte
 * identical. A missing golden file is recorded (and the test fails) so a new fixture never passes silently.
 */
class PlanIcsParityTest {
    private val now = Instant.parse("2027-01-15T08:30:00Z")

    private fun resource(name: String): String? = javaClass.getResource("/ics-parity/$name")?.readText()

    private fun check(name: String) {
        val plan = Json.parseToJsonElement(resource("$name.json")!!).jsonObject
        val input =
            PlanExportInput(
                planName = (plan["name"] as JsonPrimitive).content,
                searchData = plan["search_data"]?.jsonObject,
                selections = plan["selections"]?.jsonObject,
                itinerary = plan["live_itinerary"] as? JsonObject,
                planId = (plan["id"] as? JsonPrimitive)?.longOrNull,
                now = now,
            )
        val ics = PlanIcs.build(input)
        val golden = resource("$name.ics")
        if (golden == null) {
            File("src/test/resources/ics-parity/$name.ics").writeText(ics)
            fail("Recorded src/test/resources/ics-parity/$name.ics; review it and re-run")
        }
        assertEquals(golden, ics)
    }

    @Test
    fun `saved snapshot with a layout, edits, notes and link picks`() = check("lisbon")

    @Test
    fun `multi-city live itinerary with duplicate and non-standard times`() = check("rome-florence")

    @Test
    fun `chat-picked slots only, stale layout ignored`() = check("tokyo-chat")
}
