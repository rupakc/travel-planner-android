package io.github.rupakc.travelplanner.feature.play.h2h

import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.play.H2hState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H2hLogicTest {
    @Test
    fun `handles are trimmed lowercased and lose a leading at sign`() {
        assertEquals("ana_9", normalizeHandle("  @Ana_9 "))
        assertTrue(isHandleShape("abc"))
        assertFalse(isHandleShape("ab"))
        assertFalse(isHandleShape("a".repeat(21)))
        assertFalse(isHandleShape("a b"))
        assertFalse(isHandleShape("ana-9"))
    }

    @Test
    fun `matches group by state in display order and unknown states are dropped`() {
        val m =
            listOf(
                H2hMatch("1", state = "done"),
                H2hMatch("2", state = "waiting_you"),
                H2hMatch("3", state = "brand_new"),
                H2hMatch("4", state = "expired"),
                H2hMatch("5", state = "waiting_you"),
            )
        val groups = groupMatches(m)
        assertEquals(listOf(H2hState.WAITING_YOU, H2hState.DONE, H2hState.EXPIRED), groups.map { it.first })
        assertEquals(listOf("2", "5"), groups.first().second.map { it.id })
    }

    @Test
    fun `scores show only for a finished match with both scores`() {
        assertTrue(H2hMatch("1", state = "done", you = 300, them = 250).showsScores())
        assertFalse(H2hMatch("1", state = "done", you = 300, them = null).showsScores())
        assertFalse(H2hMatch("1", state = "waiting_them", you = 300, them = 250).showsScores())
    }
}
