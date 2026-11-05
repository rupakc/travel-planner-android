package io.github.rupakc.travelplanner.core.domain.chat

import io.github.rupakc.travelplanner.core.model.chat.ChatItinerary
import io.github.rupakc.travelplanner.core.model.chat.ChatItineraryDay
import io.github.rupakc.travelplanner.core.model.chat.ChatMessage
import io.github.rupakc.travelplanner.core.model.chat.ChatSlot
import io.github.rupakc.travelplanner.core.model.chat.TripSummary
import io.github.rupakc.travelplanner.core.model.chat.TripSummaryCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The web's `chatHistory.test.js` cases, plus the chat-itinerary schema the backend really sends. */
class ChatHistoryTest {
    private fun assistant(content: String = "", itinerary: ChatItinerary? = null, cities: List<String> = emptyList(), sections: List<String> = emptyList()) =
        HistoryTurn(ChatMessage.ROLE_ASSISTANT, content, itinerary = itinerary, tripCities = cities, sectionNames = sections)

    @Test
    fun `returns plain text content unchanged`() {
        assertEquals("Hello!", describeAssistantForApi(assistant("Hello!")))
    }

    @Test
    fun `synthesizes text for an itinerary-only turn`() {
        val itinerary =
            ChatItinerary(
                tripSummary = TripSummary(cities = listOf(TripSummaryCity(name = "Tokyo, Japan"))),
                days =
                    listOf(
                        ChatItineraryDay(dayNumber = 1, morning = ChatSlot(activity = "Senso-ji Temple"), afternoon = ChatSlot(activity = "Ramen tour")),
                        ChatItineraryDay(dayNumber = 2, morning = ChatSlot(activity = "TeamLab Planets")),
                    ),
            )
        val text = describeAssistantForApi(assistant(itinerary = itinerary))
        assertTrue(text, text.contains("2-day itinerary"))
        assertTrue(text, text.contains("Tokyo, Japan"))
        assertTrue(text, text.contains("Senso-ji Temple"))
        assertTrue(text, text.contains("Day 1: Senso-ji Temple; Ramen tour | Day 2: TeamLab Planets"))
    }

    @Test
    fun `synthesizes text for a sections-only turn`() {
        val text = describeAssistantForApi(assistant(sections = listOf("flights", "hotels")))
        assertTrue(text.contains("flights"))
        assertTrue(text.contains("hotels"))
        assertEquals("[Shared search results for: flights, hotels]", text)
    }

    @Test
    fun `includes the trip route when present`() {
        val turn = assistant(cities = listOf("Paris", "Rome")).copy(tripDeparture = "2026-09-01", tripReturn = "2026-09-10")
        val text = describeAssistantForApi(turn)
        assertTrue(text.contains("Paris → Rome"))
        assertTrue(text.contains("2026-09-01"))
        assertEquals("[Trip route: Paris → Rome, 2026-09-01 to 2026-09-10]", text)
    }

    @Test
    fun `missing return date reads TBD`() {
        val turn = assistant(cities = listOf("Lisbon")).copy(tripDeparture = "2026-09-01")
        assertEquals("[Trip route: Lisbon, 2026-09-01 to TBD]", describeAssistantForApi(turn))
    }

    @Test
    fun `drops empty assistant turns with no structured data`() {
        val history =
            buildApiHistory(
                listOf(
                    HistoryTurn(ChatMessage.ROLE_USER, "plan a trip to Tokyo"),
                    assistant(""),
                    HistoryTurn(ChatMessage.ROLE_USER, "what about the food?"),
                ),
            )
        assertEquals(
            listOf(ChatMessage("user", "plan a trip to Tokyo"), ChatMessage("user", "what about the food?")),
            history,
        )
    }

    @Test
    fun `keeps full conversation context including synthesized planning turns`() {
        val history =
            buildApiHistory(
                listOf(
                    HistoryTurn(ChatMessage.ROLE_USER, "plan 3 days in Tokyo"),
                    assistant(itinerary = ChatItinerary(days = listOf(ChatItineraryDay(dayNumber = 1)))),
                    HistoryTurn(ChatMessage.ROLE_USER, "swap day 1"),
                ),
            )
        assertEquals(3, history.size)
        assertEquals("assistant", history[1].role)
        assertTrue(history[1].content.contains("itinerary"))
    }

    @Test
    fun `blank user turns are never sent`() {
        assertEquals(emptyList<ChatMessage>(), buildApiHistory(listOf(HistoryTurn(ChatMessage.ROLE_USER, "   "))))
    }

    @Test
    fun `only the first 14 days are summarised`() {
        val days = (1..20).map { ChatItineraryDay(dayNumber = it, morning = ChatSlot(activity = "A$it")) }
        val text = describeAssistantForApi(assistant(itinerary = ChatItinerary(days = days)))
        assertTrue(text.contains("20-day itinerary"))
        assertTrue(text.contains("Day 14: A14"))
        assertTrue(!text.contains("Day 15"))
    }
}
