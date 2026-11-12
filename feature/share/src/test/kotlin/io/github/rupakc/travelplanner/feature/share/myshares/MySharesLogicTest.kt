package io.github.rupakc.travelplanner.feature.share.myshares

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MySharesLogicTest {
    @Test
    fun `story card url is derived from the share url`() {
        assertEquals(
            "https://h.test/api/share/tok1/card.png?size=story",
            storyCardUrl("https://h.test/share/tok1", "tok1"),
        )
    }

    @Test
    fun `a base path is kept`() {
        assertEquals("https://h.test/app/api/share/tok1/card.png?size=story", storyCardUrl("https://h.test/app/share/tok1", "tok1"))
    }

    @Test
    fun `an unexpected url has no card`() {
        assertNull(storyCardUrl("https://h.test/other/tok1", "tok1"))
        assertNull(storyCardUrl("", "tok1"))
    }

    @Test
    fun `month is cut from the timestamp`() {
        assertEquals("2026-11-12", createdDay("2026-11-12T08:00:00Z"))
        assertNull(createdDay(null))
        assertNull(createdDay("x"))
    }
}
