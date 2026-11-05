package io.github.rupakc.travelplanner.core.domain.export

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the web's `frontend/src/utils/planExport.test.js` (same fixtures and expectations). */
class PlanIcsTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z")

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val searchData =
        obj(
            """{"origin":"New York, USA (JFK)","destination":"Tokyo, Japan (HND)","departure_date":"2027-04-01","return_date":"2027-04-04"}""",
        )

    private val itinerary =
        obj(
            """
            {"days":[
              {"day_number":1,"date":"2027-04-01","city":"Tokyo","theme":"Arrival","slots":[
                {"time_of_day":"morning","activity":"Tsukiji Outer Market","location":"Tsukiji, Tokyo","duration_hours":2,"estimated_cost_usd":40},
                {"time_of_day":"evening","activity":"Shibuya Crossing","location":"Shibuya"}]},
              {"day_number":2,"slots":[{"time_of_day":"afternoon","activity":"Meiji Shrine"}]}
            ]}
            """.trimIndent(),
        )

    /** Unfold continuation lines and split into content lines. */
    private fun lines(ics: String) = ics.replace(Regex("\r\n[ \t]"), "").split("\r\n")

    private fun events(ics: String): List<List<String>> =
        ics.split("BEGIN:VEVENT").drop(1).map { lines("BEGIN:VEVENT" + it.split("END:VEVENT")[0]) }

    private fun List<String>.field(name: String): String? = firstOrNull { it.startsWith("$name:") || it.startsWith("$name;") }

    private fun build(
        selections: JsonObject = JsonObject(emptyMap()),
        planName: String? = "Tokyo Trip",
        search: JsonObject? = searchData,
        itinerary: JsonObject? = this.itinerary,
        planId: Long? = null,
    ) = PlanIcs.build(PlanExportInput(planName, search, selections, itinerary, planId, now))

    @Test
    fun `icsEscape escapes backslashes, semicolons, commas and newlines`() {
        assertEquals("a\\\\b\\;c\\,d\\ne", PlanIcs.escape("a\\b;c,d\ne"))
    }

    @Test
    fun `icsEscape treats CRLF and a lone CR as newlines and drops control characters`() {
        assertEquals("a\\nb\\nc", PlanIcs.escape("a\r\nb\rc"))
        assertEquals("abcd\te", PlanIcs.escape("a\u0000b\u0007c\u007fd\te"))
    }

    @Test
    fun `icsFold leaves short lines alone`() {
        assertEquals("SUMMARY:Short", PlanIcs.fold("SUMMARY:Short"))
    }

    @Test
    fun `icsFold folds lines over 75 octets with CRLF + space`() {
        val line = "DESCRIPTION:" + "x".repeat(200)
        val folded = PlanIcs.fold(line)
        val parts = folded.split("\r\n")
        assertTrue(parts.size > 1)
        parts.forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertTrue(parts.drop(1).all { it.startsWith(" ") })
        assertEquals(line, folded.replace("\r\n ", ""))
    }

    @Test
    fun `icsFold never splits a multi-byte character`() {
        val line = "SUMMARY:" + "東京".repeat(40)
        val folded = PlanIcs.fold(line)
        assertEquals(line, folded.replace("\r\n ", ""))
        folded.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
    }

    @Test
    fun `planItineraryDays prefers the full itinerary, then the saved snapshot`() {
        val live = PlanIcs.planItineraryDays(JsonObject(emptyMap()), itinerary)
        assertEquals(listOf("Tsukiji Outer Market", "Shibuya Crossing"), live[0].activities())
        val snapshot = PlanIcs.planItineraryDays(buildJsonObject { put("itinerary", itinerary) }, null)
        assertEquals(live.map { it.activities() }, snapshot.map { it.activities() })
        val first = live[0]["slots"]!!.jsonArray[0].jsonObject
        assertEquals("09:00", first["start_time"]!!.jsonPrimitive.content)
        assertEquals("11:00", first["end_time"]!!.jsonPrimitive.content)
    }

    @Test
    fun `planItineraryDays falls back to individually selected slots grouped by day`() {
        val sel =
            obj(
                """{"itinerary_slots":[
                {"key":"2-afternoon","day_number":2,"time_of_day":"afternoon","activity":"B"},
                {"key":"1-morning","day_number":1,"time_of_day":"morning","activity":"A","date":"2027-04-01"}]}""",
            )
        val days = PlanIcs.planItineraryDays(sel, null)
        assertEquals(listOf(1, 2), days.map { it["day_number"]!!.jsonPrimitive.content.toInt() })
        assertEquals("A", days[0].activities()[0])
    }

    @Test
    fun `planItineraryDays is empty when there is no itinerary`() {
        assertTrue(PlanIcs.planItineraryDays(JsonObject(emptyMap()), null).isEmpty())
    }

    @Test
    fun `is a valid calendar wrapper with CRLF line endings`() {
        val ics = build()
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"))
        assertTrue(ics.contains("PRODID:"))
        assertTrue(ics.contains("X-WR-CALNAME:Tokyo Trip"))
        assertFalse(ics.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun `makes one timed event per itinerary slot on its date`() {
        val evs = events(build())
        assertEquals(3, evs.size)
        val (market, crossing, shrine) = evs
        assertEquals("SUMMARY:Tsukiji Outer Market", market.field("SUMMARY"))
        assertEquals("DTSTART:20270401T090000", market.field("DTSTART"))
        assertEquals("DTEND:20270401T110000", market.field("DTEND"))
        assertEquals("LOCATION:Tsukiji\\, Tokyo", market.field("LOCATION"))
        assertEquals("DTSTART:20270401T183000", crossing.field("DTSTART"))
        assertEquals("DTEND:20270401T210000", crossing.field("DTEND"))
        // Day 2 has no date: derived from the trip's departure date
        assertEquals("DTSTART:20270402T133000", shrine.field("DTSTART"))
    }

    @Test
    fun `applies the user's slot edits and notes`() {
        val evs =
            events(
                build(
                    obj(
                        """{"itinerary_edits":{"1-morning":{"activity":"Toyosu Market","location":"Toyosu"}},
                        "itinerary_notes":{"1-morning":"Go early; tuna auction"}}""",
                    ),
                ),
            )
        assertEquals("SUMMARY:Toyosu Market", evs[0].field("SUMMARY"))
        assertEquals("LOCATION:Toyosu", evs[0].field("LOCATION"))
        assertTrue(evs[0].field("DESCRIPTION")!!.contains("Go early\\; tuna auction"))
    }

    @Test
    fun `every event has a unique UID and a DTSTAMP`() {
        val evs = events(build(obj("""{"hotel":{"name":"Park Hyatt Tokyo"}}""")))
        val uids = evs.map { it.field("UID") }
        assertEquals(evs.size, uids.toSet().size)
        evs.forEach { assertEquals("DTSTAMP:20261028T120000Z", it.field("DTSTAMP")) }
    }

    @Test
    fun `adds the hotel stay as an all-day span ending on check-out`() {
        val evs = events(build(obj("""{"hotel":{"name":"Park Hyatt Tokyo","location":"Shinjuku"}}""")))
        val hotel = evs.first { it.field("SUMMARY")!!.contains("Park Hyatt") }
        assertEquals("DTSTART;VALUE=DATE:20270401", hotel.field("DTSTART"))
        assertEquals("DTEND;VALUE=DATE:20270404", hotel.field("DTEND"))
    }

    @Test
    fun `adds a timed flight using its departure time and duration`() {
        val flight =
            """{"price_usd":900,
            "outbound":{"airline":"ANA","flight_number":"NH9","origin":"JFK","destination":"HND","departure_date":"2027-04-01","departure_time":"11:55","duration_minutes":840},
            "return":{"airline":"ANA","flight_number":"NH10","origin":"HND","destination":"JFK","departure_time":"17:05"}}"""
        val evs = events(build(obj("""{"flight":$flight}""")))
        val out = evs.first { it.field("SUMMARY")!!.contains("NH9") }
        assertEquals("SUMMARY:✈ ANA NH9 JFK → HND", out.field("SUMMARY"))
        assertEquals("DTSTART:20270401T115500", out.field("DTSTART"))
        assertEquals("DTEND:20270402T015500", out.field("DTEND"))
        // Return leg without its own date departs on the trip's return date
        val ret = evs.first { it.field("SUMMARY")!!.contains("NH10") }
        assertEquals("DTSTART:20270404T170500", ret.field("DTSTART"))
    }

    @Test
    fun `makes an all-day flight event when the time is unknown`() {
        val evs =
            events(
                build(
                    obj(
                        """{"flights":[{"leg_index":0,"leg_date":"2027-04-01","leg_from":"New York","leg_to":"Seoul",
                        "outbound":{"airline":"KE","flight_number":"KE82"}}]}""",
                    ),
                ),
            )
        val leg = evs.first { it.field("SUMMARY")!!.contains("KE82") }
        assertEquals("DTSTART;VALUE=DATE:20270401", leg.field("DTSTART"))
        assertEquals("DTEND;VALUE=DATE:20270402", leg.field("DTEND"))
    }

    @Test
    fun `adds selected local events as all-day events over their dates`() {
        val evs =
            events(
                build(
                    obj(
                        """{"events":[{"name":"Sumida River Fireworks","start_date":"2027-04-02","end_date":"2027-04-03","location":"Asakusa"},
                        {"name":"Undated meetup"}]}""",
                    ),
                ),
            )
        val fireworks = evs.first { it.field("SUMMARY")!!.contains("Sumida") }
        assertEquals("DTSTART;VALUE=DATE:20270402", fireworks.field("DTSTART"))
        assertEquals("DTEND;VALUE=DATE:20270404", fireworks.field("DTEND"))
        assertFalse(evs.any { it.field("SUMMARY")!!.contains("Undated") })
    }

    private fun uids(ics: String) = events(ics).map { it.field("UID") }

    @Test
    fun `gives different plans different event IDs even with the same name`() {
        val sel = obj("""{"hotel":{"name":"H"}}""")
        val a = uids(build(sel, planName = "My Trip Plan", planId = 1))
        val b = uids(build(sel, planName = "My Trip Plan", planId = 2))
        assertFalse(a.any { it in b })
    }

    @Test
    fun `keeps a saved plan's event IDs when it is renamed`() {
        val sel = obj("""{"hotel":{"name":"H"}}""")
        assertEquals(uids(build(sel, planName = "Japan", planId = 7)), uids(build(sel, planName = "Japan 2027", planId = 7)))
    }

    @Test
    fun `tells unsaved trips apart by their route and dates, not just the name`() {
        val sel = obj("""{"hotel":{"name":"H"}}""")
        val tokyo = uids(build(sel, planName = "東京"))
        val paris = JsonObject(searchData + ("destination" to JsonPrimitive("Paris")))
        val other = uids(build(sel, planName = "東京", search = paris))
        assertFalse(tokyo.any { it in other })
    }

    @Test
    fun `keeps a local event's ID when another event is removed`() {
        val sumida = """{"name":"Sumida","start_date":"2027-04-02"}"""
        val both = build(obj("""{"events":[{"name":"Other","start_date":"2027-04-01"},$sumida]}"""))
        val one = build(obj("""{"events":[$sumida]}"""))

        fun sumidaUid(ics: String) = events(ics).first { it.field("SUMMARY")!!.contains("Sumida") }.first { it.startsWith("UID") }
        assertEquals(sumidaUid(both), sumidaUid(one))
    }

    @Test
    fun `puts the local arrival time and booking link in a flight's description`() {
        val flight =
            """{"booking_url":"https://example.com/book","outbound":{"airline":"ANA","flight_number":"NH9","departure_date":"2027-04-01",
            "departure_time":"11:55","arrival_time":"15:55","duration_minutes":840}}"""
        val out = events(build(obj("""{"flight":$flight}"""))).first { it.field("SUMMARY")!!.contains("NH9") }
        assertTrue(out.field("DESCRIPTION")!!.contains("Arrives 15:55 local time"))
        assertTrue(out.field("DESCRIPTION")!!.contains("https://example.com/book"))
    }

    @Test
    fun `uses the original name for an empty-string activity edit`() {
        val ics = build(obj("""{"itinerary_edits":{"1-morning":{"activity":""}}}"""))
        assertTrue(events(ics).any { it.field("SUMMARY") == "SUMMARY:Tsukiji Outer Market" })
    }

    @Test
    fun `includes the itinerary's own slot notes`() {
        val it = obj("""{"days":[{"day_number":1,"date":"2027-04-01","slots":[{"time_of_day":"morning","activity":"A","notes":"Closed Mondays"}]}]}""")
        assertTrue(events(build(itinerary = it))[0].field("DESCRIPTION")!!.contains("Closed Mondays"))
    }

    @Test
    fun `lets a long evening slot run past midnight`() {
        val it = obj("""{"days":[{"day_number":1,"date":"2027-04-01","slots":[{"time_of_day":"evening","activity":"Night tour","duration_hours":8}]}]}""")
        assertEquals("DTEND:20270402T023000", events(build(itinerary = it))[0].field("DTEND"))
    }

    @Test
    fun `gives a same-day trip's hotel a single night`() {
        val sameDay = JsonObject(searchData + ("return_date" to JsonPrimitive("2027-04-01")))
        val hotel = events(build(obj("""{"hotel":{"name":"H"}}"""), search = sameDay)).first { it.field("SUMMARY")!!.contains("H") }
        assertEquals("DTEND;VALUE=DATE:20270402", hotel.field("DTEND"))
    }

    @Test
    fun `skips events it cannot date`() {
        val it = obj("""{"days":[{"day_number":1,"slots":[{"time_of_day":"morning","activity":"X"}]}]}""")
        assertEquals(0, events(build(planName = "No dates", search = JsonObject(emptyMap()), itinerary = it)).size)
    }

    // --- arranged itinerary ---------------------------------------------------------------

    private val trip =
        obj(
            """
            {"days":[
              {"day_number":1,"date":"2027-04-01","city":"Tokyo","theme":"Arrival","slots":[
                {"time_of_day":"morning","activity":"Tsukiji Outer Market","location":"Tsukiji","estimated_cost_usd":40},
                {"time_of_day":"evening","activity":"Shibuya Crossing"}]},
              {"day_number":2,"date":"2027-04-02","city":"Tokyo","theme":"Culture","slots":[
                {"time_of_day":"morning","activity":"Meiji Shrine"},
                {"time_of_day":"evening","activity":"Izakaya crawl"}]}
            ]}
            """.trimIndent(),
        )

    /** The layouts the web's `moveSlot(trip, null, '1-morning', 2, 'evening')` / `removeSlot(trip, null, '1-morning')` produce. */
    private fun layout(vararg positions: Pair<String, String?>) =
        buildJsonObject {
            put("fingerprint", ItineraryArrangement.fingerprint(trip))
            put("positions", JsonObject(positions.associate { (k, v) -> k to (v?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull) }))
        }

    private val swapped = layout("1-morning" to "2-evening", "2-evening" to "1-morning")
    private val removed = layout("1-morning" to null)
    private val arrangedSel =
        buildJsonObject {
            put("itinerary_layout", swapped)
            put("itinerary_edits", obj("""{"1-morning":{"activity":"Toyosu Market"}}"""))
            put("itinerary_notes", obj("""{"1-morning":"Go early"}"""))
        }

    @Test
    fun `planItineraryDays applies the layout to the live itinerary and the snapshot`() {
        val days = PlanIcs.planItineraryDays(arrangedSel, trip)
        assertEquals(listOf("Meiji Shrine", "Tsukiji Outer Market"), days[1].activities())
        assertEquals(listOf("Izakaya crawl", "Shibuya Crossing"), days[0].activities())
        val fromSnapshot = PlanIcs.planItineraryDays(JsonObject(arrangedSel + ("itinerary" to trip)), null)
        assertEquals("Tsukiji Outer Market", fromSnapshot[1].activities()[1])
    }

    @Test
    fun `does not rearrange the itinerary_slots fallback`() {
        val sel =
            buildJsonObject {
                put("itinerary_layout", swapped)
                put("itinerary_slots", JsonArray(listOf(obj("""{"day_number":1,"date":"2027-04-01","time_of_day":"morning","activity":"X"}"""))))
            }
        assertEquals("X", PlanIcs.planItineraryDays(sel, null)[0].activities()[0])
    }

    @Test
    fun `moves the ics event with its UID, edit and note`() {
        val evs = events(build(arrangedSel, planName = "T", itinerary = trip))
        val moved = evs.first { it.field("UID")!!.contains("slot-1-morning") }
        assertEquals("SUMMARY:Toyosu Market", moved.field("SUMMARY"))
        assertEquals("DTSTART:20270402T183000", moved.field("DTSTART"))
        assertEquals("DTEND:20270402T210000", moved.field("DTEND"))
        assertTrue(moved.field("DESCRIPTION")!!.contains("Go early"))
        val partner = evs.first { it.field("SUMMARY") == "SUMMARY:Izakaya crawl" }
        assertEquals("DTSTART:20270401T090000", partner.field("DTSTART"))
    }

    @Test
    fun `a removed activity produces no event`() {
        val evs = events(build(buildJsonObject { put("itinerary_layout", removed) }, planName = "T", itinerary = trip))
        assertEquals(3, evs.size)
        assertFalse(evs.any { it.field("SUMMARY") == "SUMMARY:Tsukiji Outer Market" })
    }

    @Test
    fun `a layout from another itinerary is ignored`() {
        val other = obj("""{"days":[{"day_number":1,"date":"2027-04-01","slots":[{"time_of_day":"morning","activity":"Other"}]}]}""")
        val days = PlanIcs.planItineraryDays(buildJsonObject { put("itinerary_layout", swapped) }, other)
        assertEquals(listOf("Other"), days[0].activities())
        assertEquals("1-morning", days[0]["slots"]!!.jsonArray[0].jsonObject["key"]!!.jsonPrimitive.content)
    }

    // --- scheduled times -------------------------------------------------------------------

    private fun scheduled(it: JsonObject, selections: JsonObject = JsonObject(emptyMap())) = events(build(selections, planName = "T", itinerary = it))

    private fun List<List<String>>.named(name: String) = first { it.field("SUMMARY") == "SUMMARY:$name" }

    @Test
    fun `uses the scheduled start and end, including travel from the previous stop`() {
        val kmPerDeg = 6371 * Math.PI / 180
        val lat2 = 35.6 + 4 / 1.3 / kmPerDeg
        val it =
            obj(
                """{"days":[{"day_number":1,"date":"2027-04-01","slots":[
                {"time_of_day":"morning","activity":"A","duration_hours":4.5,"lat":35.6,"lng":139.7},
                {"time_of_day":"afternoon","activity":"B","duration_hours":2,"lat":$lat2,"lng":139.7}]}]}""",
            )
        val b = scheduled(it).named("B")
        assertEquals("DTSTART:20270401T135500", b.field("DTSTART"))
        assertEquals("DTEND:20270401T155500", b.field("DTEND"))
    }

    @Test
    fun `rolls a stop that starts after midnight onto the next day`() {
        val it =
            obj(
                """{"days":[{"day_number":1,"date":"2027-04-01","slots":[
                {"time_of_day":"evening","activity":"Night tour","duration_hours":8},
                {"time_of_day":"late night","activity":"Club","duration_hours":1}]}]}""",
            )
        val club = scheduled(it).named("Club")
        assertEquals("DTSTART:20270402T025000", club.field("DTSTART"))
        assertEquals("DTEND:20270402T035000", club.field("DTEND"))
    }

    @Test
    fun `dates each event by its scheduled day offset`() {
        val it =
            obj(
                """{"days":[{"day_number":1,"date":"2027-04-01","slots":[
                {"time_of_day":"morning","activity":"A","duration_hours":12},
                {"time_of_day":"afternoon","activity":"B","duration_hours":12},
                {"time_of_day":"evening","activity":"C","duration_hours":12}]}]}""",
            )
        val evs = scheduled(it)

        fun whenOf(name: String) = evs.named(name).let { listOf(it.field("DTSTART"), it.field("DTEND")) }
        assertEquals(listOf("DTSTART:20270401T090000", "DTEND:20270401T210000"), whenOf("A"))
        assertEquals(listOf("DTSTART:20270401T212000", "DTEND:20270402T092000"), whenOf("B"))
        assertEquals(listOf("DTSTART:20270402T094000", "DTEND:20270402T214000"), whenOf("C"))
    }

    @Test
    fun `keeps a day listed out of order on its own date`() {
        val it =
            obj(
                """{"days":[{"day_number":1,"date":"2027-04-01","slots":[
                {"time_of_day":"evening","activity":"C","duration_hours":1},
                {"time_of_day":"morning","activity":"A","duration_hours":12}]}]}""",
            )
        val evs = scheduled(it)
        assertEquals("DTSTART:20270401T212000", evs.named("C").field("DTSTART"))
        assertEquals("DTSTART:20270401T090000", evs.named("A").field("DTSTART"))
        assertEquals("DTEND:20270401T210000", evs.named("A").field("DTEND"))
    }

    @Test
    fun `keeps the fixed slot hours for individually picked slots`() {
        val sel = obj("""{"itinerary_slots":[{"key":"1-afternoon","day_number":1,"date":"2027-04-01","time_of_day":"afternoon","activity":"Picked"}]}""")
        val ev = events(build(sel, planName = "T", itinerary = null)).single()
        assertEquals("DTSTART:20270401T130000", ev.field("DTSTART"))
        assertEquals("DTEND:20270401T170000", ev.field("DTEND"))
    }

    @Test
    fun `exports individually picked slots at their stored times, past midnight included`() {
        val sel =
            obj(
                """{"itinerary_slots":[
                {"key":"1-afternoon","day_number":1,"date":"2027-04-01","time_of_day":"afternoon","activity":"Picked","start_time":"13:30","end_time":"16:00","start_day_offset":0,"end_day_offset":0},
                {"key":"1-evening","day_number":1,"date":"2027-04-01","time_of_day":"evening","activity":"Late","start_time":"23:00","end_time":"01:00","start_day_offset":0,"end_day_offset":1}]}""",
            )
        val (a, b) = events(build(sel, planName = "T", itinerary = null))
        assertEquals("DTSTART:20270401T133000", a.field("DTSTART"))
        assertEquals("DTEND:20270401T160000", a.field("DTEND"))
        assertEquals("DTSTART:20270401T230000", b.field("DTSTART"))
        assertEquals("DTEND:20270402T010000", b.field("DTEND"))
    }

    @Test
    fun `planItineraryDays schedules the arranged days and names edited activities in warnings`() {
        val it =
            obj(
                """{"days":[{"day_number":1,"date":"2027-04-01","slots":[
                {"time_of_day":"morning","activity":"A","duration_hours":5},{"time_of_day":"afternoon","activity":"B"}]}]}""",
            )
        val day = PlanIcs.planItineraryDays(obj("""{"itinerary_edits":{"1-afternoon":{"activity":"Tile Museum"}}}"""), it).single()
        assertEquals("14:20", day["slots"]!!.jsonArray[1].jsonObject["start_time"]!!.jsonPrimitive.content)
        assertEquals(
            listOf("Tile Museum starts at 14:20 — later than planned"),
            day["schedule_warnings"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `imported trips note link places and credit the source`() {
        val search =
            JsonObject(searchData + ("import_source" to obj("""{"url":"https://blog.example/tokyo","title":"3 days in Tokyo"}""")))
        val it =
            obj(
                """{"days":[{"day_number":1,"date":"2027-04-01",
                "slots":[{"time_of_day":"morning","activity":"Senso-ji","from_link":true,"link_quote":"go at dawn"}],
                "link_extras":[{"name":"Golden Gai"}]}]}""",
            )
        val ics = build(planName = "Tokyo", search = search, itinerary = it).replace("\r\n ", "")
        assertTrue(ics.contains("X-WR-CALDESC:Inspired by 3 days in Tokyo — https://blog.example/tokyo"))
        assertTrue(ics.contains("From your link: “go at dawn”"))
        assertTrue(ics.contains("Also from your link: Golden Gai"))
    }

    // --- Android extras ----------------------------------------------------------------------

    @Test
    fun `events expose the same timed entries for per-event calendar inserts`() {
        val evs = PlanIcs.events(PlanExportInput("Tokyo Trip", searchData, JsonObject(emptyMap()), itinerary, null, now))
        assertEquals(3, evs.size)
        val first = evs[0]
        assertEquals("Tsukiji Outer Market", first.summary)
        assertFalse(first.allDay)
        assertEquals(java.time.LocalDateTime.of(2027, 4, 1, 9, 0), first.start)
        assertNotNull(first.end)
    }

    @Test
    fun `file names are slugs`() {
        assertEquals("tokyo-trip.ics", PlanIcs.fileName("Tokyo Trip!", "ics"))
        assertEquals("trip.ics", PlanIcs.fileName("東京", "ics"))
        assertEquals("trip.ics", PlanIcs.fileName(null, "ics"))
    }

    private fun JsonObject.activities() = this["slots"]!!.jsonArray.map { it.jsonObject["activity"]!!.jsonPrimitive.content }
}
