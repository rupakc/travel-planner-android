package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.UndecodedSection
import io.github.rupakc.travelplanner.core.model.section.VerificationLevel
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.EventDecoder
import io.github.rupakc.travelplanner.core.model.stream.PlanActionField
import io.github.rupakc.travelplanner.core.model.stream.PlanActionType
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes the recorded fixtures (built from api-contract.md examples) into typed events. */
class EventDecodingTest {
    @Test
    fun `search fixture covers every section, confidence, pricing, agent status, unknown and done`() {
        val events = Fixtures.searchEvents(Fixtures.SEARCH_FULL)
        val sections = events.filterIsInstance<SearchEvent.Section>().map { it.id }.toSet()
        assertEquals(SectionId.entries.toSet(), sections)
        assertTrue(events.any { it is SearchEvent.Confidence && it.data.grade == "green" && it.data.subScores["visa_ease"]?.score == 20 })
        assertTrue(events.any { it is SearchEvent.PricingAdvisor && it.data.recommendation == "wait" })
        assertTrue(events.any { it is SearchEvent.AgentStatus && it.agent == "flights" })
        assertTrue(events.any { it is SearchEvent.Unknown && it.type == "future_event_type" })
        assertEquals(SearchEvent.Done, events.last())
        assertFalse(events.any { it is SearchEvent.Section && it.data is UndecodedSection })
    }

    @Test
    fun `lenient numbers, sources, verification and raw JSON are preserved`() {
        val events = Fixtures.searchEvents(Fixtures.SEARCH_FULL)
        val flights = events.filterIsInstance<SearchEvent.Section>().first { it.id == SectionId.FLIGHTS }
        val data = flights.data as FlightsData
        assertEquals(1350.0, data.results[1].priceUsd!!, 0.0)
        assertEquals("15:30+1", data.results[0].outbound?.arrivalTime)
        assertEquals("JL002", data.results[0].returnLeg?.flightNumber)
        assertEquals(DataSource.AI, flights.source)

        val hotels = events.filterIsInstance<SearchEvent.Section>().filter { it.id == SectionId.HOTELS }
        assertEquals(2, hotels.size)
        val first = hotels.first().data as HotelsData
        assertEquals(true, first.verification?.pending)
        assertEquals(JsonPrimitive("kept"), first.results.single().raw["x_extra"])
        assertEquals(VerificationLevel.VERIFIED, hotels.last().data.verification?.levelKind)

        val staticVisa = events.filterIsInstance<SearchEvent.Section>().first { it.id == SectionId.VISA }
        assertEquals(DataSource.STATIC, staticVisa.source)
        val itinerary = events.filterIsInstance<SearchEvent.Section>().first { it.id == SectionId.ITINERARY }
        assertNull(itinerary.source)
    }

    @Test
    fun `malformed payloads are skipped and section errors decode`() {
        assertNull(EventDecoder.decodeSearch("{not json"))
        val error = EventDecoder.decodeSearch("""{"type":"flights","data":{"error":"timed out"},"source":"ai"}""") as SearchEvent.Section
        assertTrue(error.data.isError)
    }

    @Test
    fun `chat fixtures cover every chat event type`() {
        val qa = Fixtures.chatEvents(Fixtures.CHAT_QA)
        assertTrue(qa.first() is ChatEvent.Delta)
        assertEquals(listOf("Plan a week in Bangkok", "Best time to visit?"), (qa[2] as ChatEvent.Suggestions).chips)
        assertTrue(qa[3] is ChatEvent.SessionContextUpdate)
        assertEquals(ChatEvent.Done, qa.last())

        val planning = Fixtures.chatEvents(Fixtures.CHAT_PLANNING)
        assertEquals("Tokyo", (planning[0] as ChatEvent.PlanningStart).destination)
        val map = planning[1] as ChatEvent.TripMap
        assertEquals(40.71, map.originLat!!, 0.0)
        assertEquals(ChatEvent.ItineraryGenerating, planning[2])
        val itinerary = planning[3] as ChatEvent.ComprehensiveItinerary
        assertEquals("Hotel Gracery", itinerary.data?.days?.single()?.hotel?.name)
        assertEquals(ChatEvent.PlanningDone, planning.last())
        assertTrue(planning.last().isTerminal)

        val fallback = Fixtures.chatEvents(Fixtures.CHAT_FALLBACK)
        val add = fallback[0] as ChatEvent.PlanAction
        assertEquals(PlanActionType.ADD, add.action)
        assertEquals(PlanActionField.ACTIVITIES, add.fieldKind)
        assertNull((fallback[1] as ChatEvent.PlanAction).data)
        assertTrue(fallback.any { it is ChatEvent.AgentStatus })
        val results = fallback.filterIsInstance<ChatEvent.SectionResult>()
        assertEquals(SectionId.HOTELS, results[0].section)
        assertNull(results[1].section)
        assertEquals("https://example.com/trip", fallback.filterIsInstance<ChatEvent.ImportLink>().single().url)
        assertEquals("Legacy text", fallback.filterIsInstance<ChatEvent.Message>().single().text)
        assertTrue(fallback.any { it is ChatEvent.Error })
        assertTrue(fallback.any { it is ChatEvent.Unknown && it.type == "plan_clear" })
        assertEquals(ChatEvent.Done, fallback.last())
    }
}
