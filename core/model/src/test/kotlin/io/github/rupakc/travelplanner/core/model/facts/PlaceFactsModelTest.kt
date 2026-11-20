package io.github.rupakc.travelplanner.core.model.facts

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlaceFactPick
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SelectionKeys
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.stream.EventDecoder
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `facts` section (web docs/Place-Facts.md) and `selections.place_facts`. */
class PlaceFactsModelTest {
    private val section =
        """{"places":[{"key":"city:2950159","scope":"city","name":"Berlin","country":"DE","country_name":"Germany",
          "destination":"Berlin, Germany","geonames_id":2950159,
          "groups":{"historical":[{"id":"c:1234","fact_type":"historical","text":"Berlin was founded in 1237.","origin":"crawler",
              "source":"crawler","date_ref":1237,"lang":"en","value_lang":null,"machine_translated":false,"confidence":"structured",
              "citation":{"source_id":"od-wikidata-entitydata","name":"Wikidata","url":"https://www.wikidata.org/wiki/Q64#P571",
                "as_of":"2026-11-20","licence":"open","attribution":"Wikidata (CC0 1.0)","licence_url":null,"conflict":false}},
              "not a fact"],
            "fun":[{"id":"a:1","fact_type":"fun","text":"AI fact","origin":"ai","verified":false,"citation":null}],
            "popular":"oops","unbelievable":[],"little_known":[]}}],
          "fact_types":["historical","fun","popular","unbelievable","little_known"],"counts":{"crawler":1,"ai":1},
          "verification":{"level":"partial"}}"""

    @Test
    fun `the facts event decodes into the optional section with a crawler source`() {
        val event = EventDecoder.decodeSearch("""{"type":"facts","data":$section,"source":"crawler"}""") as SearchEvent.Section
        assertEquals(SectionId.PLACE_FACTS, event.id)
        assertEquals(DataSource.CRAWLER, event.source)
        assertTrue(event.source!!.isPhaseZero)
        val data = event.data as PlaceFactsData
        val berlin = data.places.single()
        assertEquals(2950159, berlin.geonamesId)
        val historical = berlin.groups.getValue("historical").single()
        assertEquals("1237", historical.dateRef)
        assertTrue(historical.isCrawler)
        assertEquals("Wikidata (CC0 1.0)", historical.citation?.attribution)
        assertEquals(false, historical.citation?.conflict)
        assertEquals(JsonPrimitive("structured"), historical.raw["confidence"])
        assertFalse(berlin.groups.getValue("fun").single().isCrawler)
        assertNull(berlin.groups.getValue("fun").single().citation)
        // A group that is not an array is empty, never a decoding failure.
        assertTrue(berlin.groups.getValue("popular").isEmpty())
        assertEquals("partial", data.verification?.level)
    }

    @Test
    fun `a malformed place, groups value or citation is skipped without hiding the other places`() {
        val good = """{"key":"city:1","name":"Berlin","groups":{"fun":[{"id":"a:1","text":"AI fact","origin":"ai"},
            {"id":"c:2","text":"Bad citation","origin":"crawler","citation":"x"}]}}"""
        val json = """{"places":["x",42,null,{"key":"city:2","name":"Paris","groups":"oops"},$good]}"""
        val data = SectionDecoder.decode(SectionId.PLACE_FACTS, TravelJson.parseToJsonElement(json).jsonObject) as PlaceFactsData
        assertEquals(listOf("Paris", "Berlin"), data.places.map { it.name })
        assertTrue(data.places[0].groups.isEmpty())
        assertEquals(listOf("a:1"), data.places[1].groups.getValue("fun").map { it.id })
    }

    @Test
    fun `place facts is optional and stays out of the core sections`() {
        assertTrue(SectionId.PLACE_FACTS.optional)
        assertEquals(16, SectionId.CORE.size)
        assertFalse(SectionId.PLACE_FACTS in SectionId.CORE)
        assertEquals(SectionId.TIPS.ordinal + 1, SectionId.PLACE_FACTS.ordinal)
        assertEquals(SectionId.PLACE_FACTS, SectionId.fromWire("facts"))
        val error = SectionDecoder.decode(SectionId.PLACE_FACTS, TravelJson.parseToJsonElement("""{"error":"x"}""").jsonObject)
        assertTrue(error.isError)
    }

    private val webSelections =
        """{"tips":[],"place_facts":[
          {"id":"c:1234","text":"Berlin was founded in 1237.","fact_type":"historical","place":"Berlin","origin":"crawler","lang":"en",
            "value_lang":null,"citation":{"name":"Wikidata","url":"https://www.wikidata.org/wiki/Q64","as_of":"2026-11-20",
            "attribution":"Wikidata (CC0 1.0)","licence_url":null},"x_web":1},
          {"id":"a:1","text":"AI fact","fact_type":"fun","place":"Berlin","origin":"ai","lang":null,"value_lang":null,"citation":null},
          {"id":"c:old","text":"Old quote","origin":"crawler","quote":true,"citation":{"name":"Wikipedia","attribution":"CC BY-SA 4.0"}}]}"""

    @Test
    fun `web place facts decode, count and round-trip unchanged`() {
        val s = TravelJson.decodeFromString(PlanSelections.serializer(), webSelections)
        assertEquals(3, s.placeFacts.size)
        assertEquals(3, s.count)
        assertTrue(s.placeFacts[0].isCrawler)
        assertEquals("Wikidata", s.placeFacts[0].citation?.name)
        assertEquals(true, s.placeFacts[2].quote)
        assertNull(s.placeFacts[1].citation)
        val encoded = TravelJson.encodeToJsonElement(PlanSelections.serializer(), s).jsonObject
        assertEquals(JsonPrimitive(1), encoded[SelectionKeys.PLACE_FACTS]!!.jsonArray[0].jsonObject["x_web"])
        assertEquals(TravelJson.parseToJsonElement(webSelections).jsonObject[SelectionKeys.PLACE_FACTS], encoded[SelectionKeys.PLACE_FACTS])
        assertFalse(SelectionKeys.PLACE_FACTS in s.extras)
    }

    @Test
    fun `place facts toggle by id and the key is left out once empty`() {
        val fact = PlanItem.PlaceFactItem(PlaceFactPick(id = "a:1", text = "AI fact", origin = "ai"))
        var s = PlanSelectionRules.toggle(PlanSelections(), fact)
        assertTrue(PlanSelectionRules.isSelected(s, fact))
        assertTrue(PlanSelectionRules.isSelected(s, PlanItem.PlaceFactItem(PlaceFactPick(id = "a:1"))))
        assertEquals(1, s.count)
        s = PlanSelectionRules.toggle(s, fact)
        assertTrue(s.placeFacts.isEmpty())
        val encoded = TravelJson.encodeToJsonElement(PlanSelections.serializer(), s) as JsonObject
        assertFalse(SelectionKeys.PLACE_FACTS in encoded)
    }
}
