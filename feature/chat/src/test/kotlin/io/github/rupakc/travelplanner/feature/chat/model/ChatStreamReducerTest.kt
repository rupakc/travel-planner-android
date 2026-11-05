package io.github.rupakc.travelplanner.feature.chat.model

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamReducerTest {
    private fun fold(events: List<ChatEvent>): StreamState = events.fold(ChatStreamReducer.start(), ChatStreamReducer::reduce)

    @Test
    fun `qa stream appends deltas, keeps chips and ends on done`() {
        val state = fold(Fixtures.chatEvents(Fixtures.CHAT_QA))
        val turn = state.turn
        assertEquals("US citizens can visit **Thailand** visa-free for up to 60 days.", turn.content)
        assertEquals(listOf("Plan a week in Bangkok", "Best time to visit?"), turn.suggestions)
        assertFalse(turn.streaming)
        assertNull(turn.error)
        assertEquals("Thailand", (turn.sessionContext?.get("destination") as? kotlinx.serialization.json.JsonPrimitive)?.content)
    }

    @Test
    fun `planning stream ends on planning_done with trip map and itinerary`() {
        val state = fold(Fixtures.chatEvents(Fixtures.CHAT_PLANNING))
        val turn = state.turn
        assertFalse(turn.streaming)
        assertTrue(turn.planningDone)
        assertEquals("Tokyo", turn.tripMap?.cities?.single()?.city)
        assertEquals(1, turn.itinerary?.days?.size)
        assertFalse(turn.itineraryGenerating)
        assertEquals(listOf("Add a day trip to Nikko"), turn.suggestions)
        assertTrue(turn.content.startsWith("Heads up"))
        assertNull(turn.error)
    }

    @Test
    fun `fallback stream keeps sections, import link and error flag`() {
        val state = fold(Fixtures.chatEvents(Fixtures.CHAT_FALLBACK))
        val turn = state.turn
        assertTrue(state.planActionsReceived)
        assertEquals(listOf("hotels", "mystery"), turn.sectionOrder)
        assertTrue(turn.sections["hotels"]?.data is HotelsData)
        assertEquals(ChatSectionStatus.DONE, turn.sections["hotels"]?.status)
        assertTrue(turn.pendingAgents.isEmpty())
        assertEquals("https://example.com/trip", turn.importLink)
        assertEquals("Legacy text", turn.content)
        assertEquals(TurnError.STREAM, turn.error)
        assertFalse(turn.streaming)
    }

    @Test
    fun `an AI error does not replace good section data`() {
        val good = section("""{"results":[{"name":"Park Hyatt Tokyo"}]}""", DataSource.STATIC)
        val bad = section("""{"error":"timed out"}""", DataSource.AI)
        val turn = fold(listOf(good, bad)).turn
        assertEquals("Park Hyatt Tokyo", (turn.sections["hotels"]?.data as HotelsData).results.single().name)
        assertEquals(ChatSectionStatus.DONE, turn.sections["hotels"]?.status)
        val staticOnly = fold(listOf(good)).turn
        assertEquals(ChatSectionStatus.ENHANCING, staticOnly.sections["hotels"]?.status)
    }

    @Test
    fun `agent status adds a pending agent until its section arrives`() {
        val loading = fold(listOf(ChatEvent.AgentStatus("hotels", "loading"))).turn
        assertEquals(listOf("hotels"), loading.pendingAgents)
        assertTrue(loading.planning)
    }

    @Test
    fun `done turns trailing dash questions into chips`() {
        val turn = fold(listOf(ChatEvent.Delta("Lisbon is great.\n— Want a plan?\n— Which month?"), ChatEvent.Done)).turn
        assertEquals("Lisbon is great.", turn.content)
        assertEquals(listOf("Want a plan?", "Which month?"), turn.suggestions)
    }

    @Test
    fun `empty done is an EMPTY error, a plan action alone is not`() {
        assertEquals(TurnError.EMPTY, fold(listOf(ChatEvent.Done)).turn.error)
        val action = ChatEvent.PlanAction(null, "all", null)
        assertNull(fold(listOf(action, ChatEvent.Done)).turn.error)
    }

    @Test
    fun `stream end without terminal event still finishes`() {
        val state = ChatStreamReducer.finish(fold(listOf(ChatEvent.Delta("partial"))))
        assertFalse(state.turn.streaming)
        assertNull(state.turn.error)
    }

    @Test
    fun `request failure keeps the backend detail`() {
        val state = ChatStreamReducer.fail(ChatStreamReducer.start(), AppError.RateLimited("Too many requests — please wait 30 seconds and try again.", 30))
        assertEquals(TurnError.REQUEST, state.turn.error)
        assertEquals("Too many requests — please wait 30 seconds and try again.", state.turn.errorDetail)
    }

    @Test
    fun `stop keeps content and marks stopped`() {
        val state = ChatStreamReducer.stop(fold(listOf(ChatEvent.Delta("Half an ans"))))
        assertTrue(state.turn.stopped)
        assertFalse(state.turn.streaming)
        assertEquals("Half an ans", state.turn.content)
    }

    @Test
    fun `non-http import links are ignored`() {
        assertNull(fold(listOf(ChatEvent.ImportLink("javascript:alert(1)", null))).turn.importLink)
    }

    private fun section(json: String, source: DataSource): ChatEvent.SectionResult {
        val raw = TravelJson.parseToJsonElement(json) as JsonObject
        return ChatEvent.SectionResult("hotels", SectionId.HOTELS, source, SectionDecoder.decode(SectionId.HOTELS, raw), raw)
    }
}
