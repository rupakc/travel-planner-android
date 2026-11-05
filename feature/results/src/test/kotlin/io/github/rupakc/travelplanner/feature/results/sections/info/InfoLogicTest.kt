package io.github.rupakc.travelplanner.feature.results.sections.info

import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import io.github.rupakc.travelplanner.core.model.section.EmergencyCardData
import io.github.rupakc.travelplanner.core.model.section.EmergencyCity
import io.github.rupakc.travelplanner.core.model.section.EmergencyNumbers
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.PackingCategory
import io.github.rupakc.travelplanner.core.model.section.PackingItem
import io.github.rupakc.travelplanner.core.model.section.PackingListData
import io.github.rupakc.travelplanner.core.model.section.StressFinding
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.section.TransportOption
import io.github.rupakc.travelplanner.core.model.section.WalkingTour
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.model.section.WeatherDay
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure helpers of the info sections (web rules). */
class InfoLogicTest {
    // ---- packing --------------------------------------------------------------------------

    private val packing =
        PackingListData(
            categories = listOf(PackingCategory("Clothing", "👕", items = listOf(PackingItem("Light jacket", true)))),
            luggageNote = "Carry-on is enough",
            totalItems = 1,
        )

    @Test
    fun `packing state toggles, adds custom items and counts like the web`() {
        val s = PackingState().toggle("Clothing", "Light jacket").addCustom("Clothing", "  Umbrella ").addCustom("Clothing", "   ")
        assertTrue(s.isChecked("Clothing", "Light jacket"))
        assertEquals(listOf("Umbrella"), s.custom["Clothing"])
        assertEquals(2, s.total(packing))
        assertEquals(1, s.checkedCount)
        assertFalse(s.toggle("Clothing", "Light jacket").isChecked("Clothing", "Light jacket"))
    }

    @Test
    fun `packing plan json has the web shape and round-trips`() {
        val json = PackingState().toggle("Clothing", "Light jacket").addCustom("Clothing", "Umbrella").toPlanJson(packing)
        assertEquals(setOf("categories", "luggage_note", "checked_items", "custom_items", "total_items", "checked_count"), json.keys)
        assertEquals(true, json["checked_items"]!!.jsonObject["Clothing::Light jacket"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("2", json["total_items"]!!.jsonPrimitive.content)
        assertEquals("1", json["checked_count"]!!.jsonPrimitive.content)
        assertEquals("Light jacket", json["categories"]!!.jsonArray[0].jsonObject["items"]!!.jsonArray[0].jsonObject["item"]!!.jsonPrimitive.content)
        val back = PackingState.fromPlan(json)
        assertEquals(PackingState.decode(back.encode()), back)
        assertEquals(PackingState(), PackingState.fromPlan(null))
        // survives the plan selections serializer (web ↔ app)
        val raw = TravelJson.parseToJsonElement("""[{"name":"Clothing","items":[{"item":"Light jacket","essential":true,"brand":"any"}]}]""")
        assertEquals(raw, PackingState().toPlanJson(packing, raw)["categories"])
        val sel = PlanSelectionsSerializer.fromJson(PlanSelectionsSerializer.toJson(PlanSelections(packingList = json)))
        assertEquals(json, sel.packingList)
    }

    // ---- itinerary editing ----------------------------------------------------------------

    private val itinerary =
        ItineraryData(
            days =
                listOf(
                    ItineraryDay(
                        dayNumber = 1,
                        city = "Lisbon",
                        slots =
                            listOf(
                                ItinerarySlot(timeOfDay = "morning", activity = "Castle", durationHours = 2.0, estimatedCostUsd = 10.0),
                                ItinerarySlot(timeOfDay = "afternoon", activity = "Tram", durationHours = 1.0, estimatedCostUsd = 5.0),
                            ),
                    ),
                ),
        )

    @Test
    fun `plannedEntry stores key, position, edited fields and scheduled times`() {
        val day = ItinerarySchedule.scheduleDays(ItineraryLayouts.arrange(itinerary, null).days).single()
        val entry = ItineraryEdits.plannedEntry(1, day.slots[0], SlotEdit(activity = "Castle sunset", cost = 12.0))
        assertEquals("1-morning", entry.key)
        assertEquals(1, entry.dayNumber)
        assertEquals("Castle sunset", entry.activity)
        assertEquals(12.0, entry.estimatedCostUsd)
        assertEquals("09:00", entry.startTime)
        assertEquals("11:00", entry.endTime)
        val keys = setOf("key", "day_number", "time_of_day", "activity", "estimated_cost_usd")
        assertEquals(keys + setOf("start_time", "end_time", "start_day_offset", "end_day_offset", "duration_hours"), entry.raw.keys)
    }

    @Test
    fun `commitLayout moves and re-times picked slots`() {
        val day = ItinerarySchedule.scheduleDays(ItineraryLayouts.arrange(itinerary, null).days).single()
        val picked = ItineraryEdits.plannedEntry(1, day.slots[0], SlotEdit.NONE)
        val selections = PlanSelections(itinerarySlots = listOf(picked))
        val layout = ItineraryLayouts.moveSlot(itinerary, null, "1-morning", 1, "afternoon")
        val out = ItineraryEdits.commitLayout(itinerary, selections, layout)
        assertEquals(layout, out.itineraryLayout)
        val moved = out.itinerarySlots.single()
        assertEquals("afternoon", moved.timeOfDay)
        assertEquals("13:30", moved.startTime)
        assertEquals("afternoon", moved.raw["time_of_day"]!!.jsonPrimitive.content)
        // reset: back to the original spot
        assertEquals("morning", ItineraryEdits.commitLayout(itinerary, out, null).itinerarySlots.single().timeOfDay)
    }

    @Test
    fun `edits, notes and cost text are stored like the web`() {
        var s = ItineraryEdits.setEdit(PlanSelections.EMPTY, "1-morning", "activity", JsonPrimitive("Castle"))
        s = ItineraryEdits.setEdit(s, "1-morning", "cost", ItineraryEdits.costValue("abc", java.util.Locale.US))
        assertEquals(JsonNull, s.itineraryEdits["1-morning"]!!["cost"])
        assertEquals("Castle", s.itineraryEdits["1-morning"]!!["activity"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(12.5), ItineraryEdits.costValue(" 12.5 ", java.util.Locale.US))
        assertEquals(JsonPrimitive(12.5), ItineraryEdits.costValue("12,5", java.util.Locale.forLanguageTag("es-ES")))
        assertEquals(JsonPrimitive(1200.0), ItineraryEdits.costValue("1.200", java.util.Locale.forLanguageTag("es-ES")))
        assertEquals("Book", ItineraryEdits.setNote(s, "1-morning", "Book").itineraryNotes["1-morning"])
        val layout = ItineraryLayouts.removeSlot(itinerary, null, "1-afternoon")
        assertEquals(layout, ItineraryEdits.decodeLayout(ItineraryEdits.encodeLayout(layout)))
        assertNull(ItineraryEdits.decodeLayout(ItineraryEdits.encodeLayout(null)))
    }

    // ---- trip health check ----------------------------------------------------------------

    private val original = StressTestData(overall = "amber", score = 60)
    private val edited = StressTestData(overall = "green", score = 90)

    @Test
    fun `health view follows the web re-check rules`() {
        assertEquals(RecheckNote.NONE, healthView(original, false, RecheckState(), null).note)
        healthView(original, true, RecheckState(), "fp").let {
            assertEquals(RecheckNote.ORIGINAL, it.note)
            assertTrue(it.showRecheck)
            assertSame(original, it.data)
        }
        val done = RecheckState(result = RecheckResult(edited, "fp"))
        healthView(original, true, done, "fp").let {
            assertEquals(RecheckNote.EDITED, it.note)
            assertFalse(it.showRecheck)
            assertEquals(edited, it.data)
        }
        assertEquals(RecheckNote.STALE, healthView(original, true, done, "other").note)
        // back to the original layout: the re-check no longer applies
        assertEquals(original, healthView(original, false, done, "fp").data)
        val failed = RecheckState(failure = RecheckFailure("Too many requests", "fp"))
        assertEquals("Too many requests", healthView(original, true, failed, "fp").error?.message)
        assertNull(healthView(original, true, failed, "changed").error)
        assertEquals(RecheckState(result = done.result), RecheckState.decode(done.copy(running = true).encode()))
    }

    @Test
    fun `findings sort high to low and overall defaults to amber`() {
        val sorted = sortedFindings(listOf(StressFinding("low"), StressFinding("weird"), StressFinding("high"), StressFinding("medium")))
        assertEquals(listOf("high", "medium", "low", "weird"), sorted.map { it.severity })
        assertEquals(HealthOverall.AMBER, HealthOverall.of("purple"))
        assertEquals("🔎", healthCategoryEmoji("other"))
    }

    // ---- small helpers ----------------------------------------------------------------------

    @Test
    fun `dialable numbers keep digits and plus, first of several`() {
        assertEquals("110", dialableNumber("110"))
        assertEquals("+81352223311", dialableNumber("+81 3-5222-3311"))
        assertEquals("112", dialableNumber("112 / 911"))
        assertNull(dialableNumber("ask your hotel"))
        assertNull(dialableNumber(null))
    }

    @Test
    fun `emergency share text lists every city and number`() {
        val data =
            EmergencyCardData(
                cities = listOf(EmergencyCity("Tokyo", EmergencyNumbers("110", "119")), EmergencyCity("Kyoto", EmergencyNumbers(police = "110"))),
            )
        val labels = EmergencyLabels("Emergency card", "Police", "Ambulance", "Fire", "Tourist Police", "Hospitals", "Phrases", "Laws", "Verify", "Footer")
        val text = emergencyShareText(emergencyCards(data), labels)
        assertTrue(text.contains("📍 Tokyo"))
        assertTrue(text.contains("📍 Kyoto"))
        assertTrue(text.contains("Ambulance: 119"))
        assertTrue(text.contains("Fire: —"))
        assertTrue(text.endsWith("Footer"))
        assertEquals(1, emergencyCards(EmergencyCardData(emergencyNumbers = EmergencyNumbers("112"))).size)
    }

    @Test
    fun `web grouping and labelling helpers`() {
        val tours =
            listOf(
                WalkingTour(name = "A", city = "Lisbon"),
                WalkingTour(name = "B", city = "Lisbon", topPick = true),
                WalkingTour(name = "C", city = "Porto", topPick = true),
            )
        assertEquals(listOf("B", "A", "C"), sortTopPicksPerCity(tours).map { it.name })
        val days = listOf(WeatherDay(date = "1", city = "Lisbon, PT"), WeatherDay(date = "2", city = "Lisbon"), WeatherDay(date = "3", city = "Porto"))
        assertEquals(listOf("Lisbon" to 2, "Porto" to 1), weatherGroups(days).map { it.city to it.days.size })
        assertEquals(PlaceWeather.CHECK, placeWeather("Viewpoint", WeatherData(poorWeatherDayCount = 1)))
        assertEquals(PlaceWeather.GOOD, placeWeather("Park/Garden", WeatherData(days = days, poorWeatherDayCount = 0)))
        assertNull(placeWeather("Museum", WeatherData(poorWeatherDayCount = 3)))
        assertNull(placeWeather("Viewpoint", null))
        assertEquals("VISA ON ARRIVAL", visaTypeLabel("visa-on-arrival"))
        assertEquals("MRT", transportTag("metro"))
        assertEquals("?", transportTag("hovercraft"))
        val options = listOf(TransportOption(name = "x", scope = "inter_city"), TransportOption(name = "y", scope = "intra_city"), TransportOption(name = "z"))
        val groups = transportGroups(options)
        assertEquals(listOf("y", "x", "z"), groups.flatMap { g -> g.second.map { it.name } })
        assertEquals("https://a.b", safeHttpUrl(" https://a.b "))
        assertNull(safeHttpUrl("javascript:alert(1)"))
        assertEquals("Tokyo · Kyoto", cityOf(TravelJson.parseToJsonElement("""{"city":["Tokyo, Japan","Kyoto"]}""") as JsonObject))
        assertEquals("✨", eventEmoji("unknown"))
        assertEquals(Tone.Success, coverageTone("excellent"))
        assertEquals(TipSeverity.INFO, TipSeverity.of("whatever"))
    }
}
