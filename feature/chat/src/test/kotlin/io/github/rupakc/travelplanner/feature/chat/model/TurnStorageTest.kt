package io.github.rupakc.travelplanner.feature.chat.model

import io.github.rupakc.travelplanner.core.data.repository.StoredChatMessage
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnStorageTest {
    private fun fold(name: String) = Fixtures.chatEvents(name).fold(ChatStreamReducer.start(), ChatStreamReducer::reduce).turn

    @Test
    fun `plain text turns store no extras`() {
        assertNull(encodeExtras(ChatTurn(id = 1, role = "user", content = "hi")))
    }

    @Test
    fun `planning turn round-trips through Room json`() {
        val turn = fold(Fixtures.CHAT_PLANNING)
        val restored = StoredChatMessage(7, 1, "assistant", turn.content, encodeExtras(turn), 0).toTurn()
        assertEquals(7L, restored.id)
        assertEquals(turn.tripMap, restored.tripMap)
        assertEquals(turn.itinerary, restored.itinerary)
        assertEquals(turn.suggestions, restored.suggestions)
        assertTrue(restored.planningDone)
        assertEquals(turn.toHistory().copy(), restored.toHistory())
    }

    @Test
    fun `fallback turn keeps decoded sections and error`() {
        val turn = fold(Fixtures.CHAT_FALLBACK)
        val restored = StoredChatMessage(8, 1, "assistant", turn.content, encodeExtras(turn), 0).toTurn()
        assertEquals(turn.sectionOrder, restored.sectionOrder)
        assertTrue(restored.sections["hotels"]?.data is HotelsData)
        assertEquals(TurnError.STREAM, restored.error)
        assertEquals("https://example.com/trip", restored.importLink)
    }

    @Test
    fun `unreadable extras degrade to plain text`() {
        val restored = StoredChatMessage(9, 1, "assistant", "hello", "{not json", 0).toTurn()
        assertEquals("hello", restored.content)
        assertTrue(restored.sections.isEmpty())
    }
}
