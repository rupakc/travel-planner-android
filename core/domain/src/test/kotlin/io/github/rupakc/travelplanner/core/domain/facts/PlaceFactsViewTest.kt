package io.github.rupakc.travelplanner.core.domain.facts

import io.github.rupakc.travelplanner.core.domain.export.PlanShareText
import io.github.rupakc.travelplanner.core.domain.export.PlanTextLabels
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.FactCitation
import io.github.rupakc.travelplanner.core.model.section.PlaceFact
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsPlace
import io.github.rupakc.travelplanner.core.model.section.TipsData
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the web's `PlaceFactsSection` helpers and `utils/placeFactText.js` tests. */
class PlaceFactsViewTest {
    private val cit =
        FactCitation(
            sourceId = "od-wikidata-entitydata",
            name = "Wikidata",
            publisher = "Wikidata contributors",
            url = "https://www.wikidata.org/wiki/Q64",
            asOf = "2026-11-20T08:00:00Z",
            licence = "open",
            attribution = "Wikidata (CC0 1.0)",
            licenceUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
        )
    private val crawler = PlaceFact(id = "c:1", factType = "historical", text = "Berlin was founded in 1237.", origin = "crawler", lang = "en", citation = cit)
    private val ai = PlaceFact(id = "a:2", factType = "fun", text = "AI fact", origin = "ai", lang = "und", citation = cit)
    private val berlin =
        PlaceFactsPlace(
            key = "city:2950159",
            scope = "city",
            name = "Berlin",
            groups = mapOf("historical" to listOf(crawler), "fun" to listOf(ai, PlaceFact(text = "  ")), "bogus" to listOf(crawler)),
        )
    private val empty = PlaceFactsPlace(key = "country:DE", name = "Germany", groups = mapOf("fun" to listOf(PlaceFact(text = ""))))

    @Test
    fun `places keep the five groups, drop blank facts and places without facts`() {
        val places = PlaceFactsView.places(PlaceFactsData(places = listOf(berlin, empty)))
        assertEquals(listOf("Berlin"), places.map { it.name })
        assertEquals(PlaceFactsView.FACT_TYPES.toSet(), places.single().groups.keys)
        assertEquals(listOf(ai), places.single().groups["fun"])
        assertEquals(listOf("historical", "fun"), PlaceFactsView.availableGroups(places.single()))
    }

    @Test
    fun `no section, an error or no facts means nothing to show`() {
        assertFalse(PlaceFactsView.hasFacts(null))
        assertFalse(PlaceFactsView.hasFacts(TipsData()))
        assertFalse(PlaceFactsView.hasFacts(PlaceFactsData(places = listOf(berlin), error = "no_facts")))
        assertFalse(PlaceFactsView.hasFacts(PlaceFactsData(places = listOf(empty))))
        assertTrue(PlaceFactsView.hasFacts(PlaceFactsData(places = listOf(berlin))))
    }

    @Test
    fun `a crawler fact keeps its citation, an AI fact never does`() {
        val c = PlaceFactsView.selection(crawler, berlin)
        assertEquals("c:1", c.id)
        assertEquals("Berlin", c.place)
        assertEquals("crawler", c.origin)
        assertEquals("en", c.lang)
        assertEquals("Wikidata", c.citation?.name)
        assertEquals("2026-11-20T08:00:00Z", c.citation?.asOf)
        val json = c.raw
        assertEquals(setOf("id", "text", "fact_type", "place", "origin", "lang", "value_lang", "citation"), json.keys)
        assertEquals(JsonNull, json["value_lang"])
        assertEquals(setOf("name", "url", "as_of", "attribution", "licence_url"), json["citation"]!!.jsonObject.keys)

        val a = PlaceFactsView.selection(ai, berlin)
        assertEquals("ai", a.origin)
        assertNull(a.citation)
        assertNull(a.lang)
        assertEquals(JsonNull, a.raw["citation"])
    }

    @Test
    fun `a fact without id is identified by place key and text, an unknown origin counts as AI`() {
        val pick = PlaceFactsView.selection(PlaceFact(text = "X", origin = "wikipedia", citation = cit), berlin)
        assertEquals("city:2950159:X", pick.id)
        assertEquals("ai", pick.origin)
        assertNull(pick.citation)
    }

    @Test
    fun `language tags follow the web langAttr`() {
        assertEquals("de", PlaceFactsView.langTag("de"))
        assertEquals("zh-Hant", PlaceFactsView.langTag("zh-Hant"))
        assertNull(PlaceFactsView.langTag("und"))
        assertNull(PlaceFactsView.langTag("UND"))
        assertNull(PlaceFactsView.langTag("not a tag"))
        assertNull(PlaceFactsView.langTag("d"))
        assertNull(PlaceFactsView.langTag(null))
        assertNull(PlaceFactsView.langTag("a".repeat(2) + "-x".repeat(20)))
    }

    @Test
    fun `a single quoted value is split out in its own language`() {
        val q = PlaceFactsView.quotedValue("The official name of Germany is “Bundesrepublik Deutschland”.", "de")!!
        assertEquals("The official name of Germany is “", q.before)
        assertEquals("Bundesrepublik Deutschland", q.value)
        assertEquals("”.", q.after)
        assertEquals("de", q.lang)
        assertNull(PlaceFactsView.quotedValue("No quote here.", "de"))
        assertNull(PlaceFactsView.quotedValue("Two “a” and “b”.", "de"))
        assertNull(PlaceFactsView.quotedValue("Empty “ ”.", "de"))
        assertNull(PlaceFactsView.quotedValue("Name “X”.", "und"))
        assertNull(PlaceFactsView.quotedValue("Name “X”.", null))
    }

    @Test
    fun `as-of days and links`() {
        assertEquals("2026-11-20", PlaceFactsView.day("2026-11-20T08:00:00Z"))
        assertNull(PlaceFactsView.day("Edition 2025"))
        assertNull(PlaceFactsView.link("javascript:alert(1)"))
        assertEquals("https://www.wikidata.org/wiki/Q64", PlaceFactsView.link("https://www.wikidata.org/wiki/Q64"))
    }

    @Test
    fun `share text prints facts with their source or the AI label`() {
        val selections =
            PlanSelections(
                placeFacts =
                    listOf(
                        PlaceFactsView.selection(crawler, berlin),
                        PlaceFactsView.selection(ai, berlin),
                        PlaceFactsView.selection(crawler.copy(id = "c:bad", citation = cit.copy(url = "javascript:x", licenceUrl = "javascript:y")), berlin),
                        PlaceFactsView.selection(crawler.copy(id = "c:anon", text = "Nameless source", citation = cit.copy(name = " ")), berlin),
                        PlaceFactsView.selection(crawler.copy(id = "c:none", text = "No citation", citation = null), berlin),
                    ),
            )
        val labels =
            PlanTextLabels(
                flights = "Flights",
                hotel = "Hotel",
                activities = "Activities",
                places = "Places",
                walkingTours = "Tours",
                events = "Events",
                sim = "SIM",
                gettingAround = "Getting around",
                tips = "Tips",
                itinerary = "Itinerary",
                day = { "Day $it" },
                estimatedCost = { "Estimated cost: $it" },
                perNight = { "$it / night" },
                estimate = "Estimate",
                placeFacts = "Place facts",
                aiFact = "AI-suggested, not verified",
                source = { "Source: $it" },
                asOf = { "as of $it" },
            )
        val text = PlanShareText.build("Berlin", emptyList(), selections, emptyList(), null, labels)
        val sourced =
            "Place facts\n  • Berlin: Berlin was founded in 1237.\n    Source: Wikidata · as of 2026-11-20\n" +
                "    https://www.wikidata.org/wiki/Q64\n    Wikidata (CC0 1.0)\n    https://creativecommons.org/publicdomain/zero/1.0/"
        assertTrue(text, text.contains(sourced))
        assertTrue(text, text.contains("  • Berlin: AI fact\n    AI-suggested, not verified"))
        assertFalse(text, text.contains("javascript"))
        // A crawler pick without a source name, or without a citation, is printed with the AI label (web print).
        assertTrue(text, text.contains("  • Berlin: Nameless source\n    AI-suggested, not verified"))
        assertTrue(text, text.contains("  • Berlin: No citation\n    AI-suggested, not verified"))
        // Without a label the section is left out (older callers).
        val old = PlanShareText.build("Berlin", emptyList(), selections, emptyList(), null, labels.copy(placeFacts = ""))
        assertFalse(old.contains("Berlin was founded"))
    }

    @Test
    fun `the selection JSON decodes back into PlanSelections`() {
        val raw = PlaceFactsView.selection(crawler, berlin).raw
        val s = TravelJson.decodeFromString(PlanSelections.serializer(), """{"place_facts":[$raw]}""")
        assertEquals(JsonPrimitive("c:1"), s.placeFacts.single().raw["id"])
        assertEquals("Wikidata (CC0 1.0)", s.placeFacts.single().citation?.attribution)
    }
}
