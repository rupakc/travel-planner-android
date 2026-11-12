package io.github.rupakc.travelplanner.core.ui.game

import io.github.rupakc.travelplanner.core.model.game.EarnedBadge
import io.github.rupakc.travelplanner.core.model.game.GameAward
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.game.LevelUp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameCelebrationsTest {
    private val delta =
        GameDelta(
            xp = 120,
            awards = listOf(GameAward("plan_saved", 10), GameAward("trip_ready", 40)),
            badges = listOf(EarnedBadge("first_trip"), EarnedBadge("mystery_badge")),
            levelUp = LevelUp(1, 2),
        )

    @Test fun `delta becomes snackbars then level up then badges`() {
        val c = GameCelebrations.of(delta, enabled = true)
        assertEquals(listOf(GameCelebration.Xp(10, "plan_saved"), GameCelebration.Xp(40, "trip_ready")), c.snackbars)
        assertEquals(listOf(GameCelebration.LevelUp(2), GameCelebration.Badge("first_trip"), GameCelebration.Badge("mystery_badge")), c.sheets)
    }

    @Test fun `hidden game suppresses everything`() {
        val c = GameCelebrations.of(delta, enabled = false)
        assertEquals(emptyList<GameCelebration>(), c.snackbars + c.sheets)
    }

    @Test fun `null and empty deltas show nothing`() {
        assertEquals(emptyList<GameCelebration>(), GameCelebrations.of(null, true).let { it.snackbars + it.sheets })
        assertEquals(emptyList<GameCelebration>(), GameCelebrations.of(GameDelta(xp = 5), true).let { it.snackbars + it.sheets })
    }

    @Test fun `zero point awards are not announced`() {
        val c = GameCelebrations.of(GameDelta(awards = listOf(GameAward("x", 0))), true)
        assertEquals(emptyList<GameCelebration>(), c.snackbars)
    }

    @Test fun `known ids have copy and unknown ids fall back to the id's words`() {
        assertNotNull(GameCopy.badgeName("first_trip"))
        assertNotNull(GameCopy.badgeDesc("countries_30"))
        assertNotNull(GameCopy.ruleName("deal_saved_1000"))
        assertNull(GameCopy.badgeName("mystery_badge"))
        assertEquals("Mystery badge", GameCopy.words("mystery_badge"))
        assertEquals("Countries 3", GameCopy.words("countries_3"))
        assertNull(GameCopy.levelName(11))
    }
}
