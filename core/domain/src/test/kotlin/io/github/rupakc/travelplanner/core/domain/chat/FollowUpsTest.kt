package io.github.rupakc.travelplanner.core.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FollowUpsTest {
    @Test
    fun `trailing dash lines become follow-ups and leave the body`() {
        val parsed = parseFollowUps("Lisbon is lovely in May.\n\n— Want a 3-day plan?\n– Prefer the coast?")
        assertEquals("Lisbon is lovely in May.", parsed.body)
        assertEquals(listOf("Want a 3-day plan?", "Prefer the coast?"), parsed.followUps)
    }

    @Test
    fun `blank lines between follow-ups are skipped`() {
        val parsed = parseFollowUps("Body\n- one\n\n- two\n\n")
        assertEquals("Body", parsed.body)
        assertEquals(listOf("one", "two"), parsed.followUps)
    }

    @Test
    fun `text without trailing questions is unchanged`() {
        val parsed = parseFollowUps("- a list item\nthen prose")
        assertEquals("- a list item\nthen prose", parsed.body)
        assertEquals(emptyList<String>(), parsed.followUps)
    }

    @Test
    fun `empty text`() {
        assertEquals(FollowUps("", emptyList()), parseFollowUps(""))
    }

    @Test
    fun `done chips need at least two adjacent dash lines`() {
        assertEquals(FollowUps("Answer", listOf("Q1?", "Q2?")), extractDoneChips("Answer\n— Q1?\n— Q2?"))
        assertNull(extractDoneChips("Answer\n— only one?"))
        assertNull(extractDoneChips("Answer only"))
    }
}
