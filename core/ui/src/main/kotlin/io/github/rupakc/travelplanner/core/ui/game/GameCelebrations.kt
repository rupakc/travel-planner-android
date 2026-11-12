package io.github.rupakc.travelplanner.core.ui.game

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.model.game.GameDelta

/** One thing to celebrate. */
sealed interface GameCelebration {
    /** A snackbar: "+10 XP: Trip saved". */
    data class Xp(val points: Int, val rule: String) : GameCelebration

    /** A sheet: the new level. */
    data class LevelUp(val to: Int) : GameCelebration

    /** A sheet: a badge earned (the id may be unknown to this app). */
    data class Badge(val id: String) : GameCelebration
}

/** A delta split into what the host shows: [snackbars] one after another, [sheets] one after another. */
@Immutable
data class GameCelebrations(val snackbars: List<GameCelebration>, val sheets: List<GameCelebration>) {
    val isEmpty: Boolean get() = snackbars.isEmpty() && sheets.isEmpty()

    companion object {
        val NONE = GameCelebrations(emptyList(), emptyList())

        /** Pure: [enabled] false (`hide_game`, game disabled) shows nothing. */
        fun of(delta: GameDelta?, enabled: Boolean): GameCelebrations {
            if (delta == null || !enabled || delta.isEmpty) return NONE
            val xp = delta.awards.filter { it.points > 0 }.map { GameCelebration.Xp(it.points, it.rule) }
            val sheets =
                listOfNotNull(delta.levelUp?.let { GameCelebration.LevelUp(it.to) }) + delta.badges.map { GameCelebration.Badge(it.id) }
            return GameCelebrations(xp, sheets)
        }
    }
}
