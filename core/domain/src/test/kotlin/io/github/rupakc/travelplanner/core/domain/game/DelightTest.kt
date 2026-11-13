package io.github.rupakc.travelplanner.core.domain.game

import io.github.rupakc.travelplanner.core.model.game.BadgeDef
import io.github.rupakc.travelplanner.core.model.game.BadgeProgress
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameMe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelightTest {
    private val catalog = GameCatalog(badges = listOf(BadgeDef("deal_1", target = 1), BadgeDef("saver_100", target = 4), BadgeDef("verified")))

    @Test
    fun `picks the open deal badge with the most progress`() {
        val me = GameMe(badges = listOf(BadgeProgress("deal_1", 1, 1, "2026-01-01"), BadgeProgress("saver_100", 3, 4)))
        assertEquals(DealHunterState.Next("saver_100", 3, 4, 75), Delight.dealHunter(me, catalog, gameVisible = true))
    }

    @Test
    fun `unseen catalog badge counts as zero progress with its catalog target`() {
        val state = Delight.dealHunter(GameMe(), catalog, true) as DealHunterState.Next
        assertEquals(0, state.progress)
        assertEquals(0, state.percent)
    }

    @Test
    fun `all earned is all done and no deal badges is hidden`() {
        val done = GameMe(badges = listOf(BadgeProgress("deal_1", 1, 1, "x"), BadgeProgress("saver_100", 4, 4, "x")))
        assertEquals(DealHunterState.AllDone, Delight.dealHunter(done, catalog, true))
        assertEquals(DealHunterState.Hidden, Delight.dealHunter(GameMe(badges = listOf(BadgeProgress("verified"))), GameCatalog(), true))
    }

    @Test
    fun `hide_game, hidden me and missing me render nothing`() {
        assertEquals(DealHunterState.Hidden, Delight.dealHunter(GameMe(), catalog, gameVisible = false))
        assertEquals(DealHunterState.Hidden, Delight.dealHunter(GameMe(hidden = true), catalog, true))
        assertEquals(DealHunterState.Hidden, Delight.dealHunter(null, catalog, true))
    }

    @Test
    fun `percent clamps`() {
        assertEquals(100, Delight.percent(9, 4))
        assertEquals(0, Delight.percent(-1, 4))
        assertEquals(0, Delight.percent(1, 0))
    }

    @Test
    fun `bucket needs a positive goal and visible game and caps the fraction`() {
        assertNull(Delight.bucket(3, null, true))
        assertNull(Delight.bucket(3, 0, true))
        assertNull(Delight.bucket(3, 10, gameVisible = false))
        assertEquals(0.3f, Delight.bucket(3, 10, true)!!.fraction, 0.001f)
        assertEquals(1f, Delight.bucket(12, 10, true)!!.fraction, 0f)
    }

    @Test
    fun `reduced motion gates animation and confetti`() {
        assertTrue(Delight.isReducedScale(0f))
        assertFalse(Delight.isReducedScale(1f))
        assertFalse(Delight.animate(reducedMotion = true))
        assertTrue(Delight.confetti(reducedMotion = false, celebrate = true))
        assertFalse(Delight.confetti(reducedMotion = true, celebrate = true))
        assertFalse(Delight.confetti(reducedMotion = false, celebrate = false))
    }
}
