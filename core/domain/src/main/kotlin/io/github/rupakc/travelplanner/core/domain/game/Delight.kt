package io.github.rupakc.travelplanner.core.domain.game

import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameMe

/** What the Deal-hunter track shows (web `DealHunterTrack`). */
sealed interface DealHunterState {
    /** Game hidden, unavailable, or no Deal-hunter badge is known: render nothing. */
    data object Hidden : DealHunterState

    /** Every Deal-hunter badge is earned. */
    data object AllDone : DealHunterState

    /** The open badge closest to done; [percent] is 0..100. */
    data class Next(val badgeId: String, val progress: Int, val target: Int, val percent: Int) : DealHunterState
}

/** Pure rules of the delight visuals (Deal-hunter track, bucket ring, motion and hide_game gates). */
object Delight {
    private const val FULL = 100.0
    private val dealBadge = Regex("^(deal_|saver_|bullseye)")

    /** Same selection as the web: `deal_*`, `saver_*` and `bullseye` badges from the catalog plus the user's own, the open one with most progress. */
    fun dealHunter(me: GameMe?, catalog: GameCatalog?, gameVisible: Boolean): DealHunterState {
        if (!gameVisible || me == null || me.hidden) return DealHunterState.Hidden
        val mine = me.badges.associateBy { it.id }
        val meta = catalog?.badges.orEmpty().associateBy { it.id }
        val ids = (meta.keys + mine.keys).filter { dealBadge.containsMatchIn(it) }
        if (ids.isEmpty()) return DealHunterState.Hidden
        val open =
            ids.mapNotNull { id ->
                val b = mine[id]
                if (b?.earnedAt != null) return@mapNotNull null
                val target = (b?.target ?: meta[id]?.target ?: 1).coerceAtLeast(1)
                Triple(id, (b?.progress ?: 0).coerceAtLeast(0), target)
            }
        val next = open.maxByOrNull { (_, progress, target) -> progress.toDouble() / target } ?: return DealHunterState.AllDone
        val (id, progress, target) = next
        return DealHunterState.Next(id, progress, target, percent(progress, target))
    }

    /** `100 * progress / target` rounded and clamped to 0..100; 0 for a non-positive target. */
    fun percent(progress: Int, target: Int): Int = if (target <= 0) 0 else Math.round(FULL * progress / target).toInt().coerceIn(0, FULL.toInt())

    /** Countries visited against the bucket-list goal; null without a positive goal (web `BucketRing`). */
    data class Bucket(val count: Int, val goal: Int) {
        /** Share of the ring filled, 0..1 (the count is capped at the goal). */
        val fraction: Float get() = count.coerceIn(0, goal).toFloat() / goal
    }

    fun bucket(count: Int, goal: Int?, gameVisible: Boolean): Bucket? =
        if (!gameVisible || goal == null ||
            goal <= 0
        ) {
            null
        } else {
            Bucket(count.coerceAtLeast(0), goal)
        }

    /** Animate only when the user has not turned system animations off. */
    fun animate(reducedMotion: Boolean): Boolean = !reducedMotion

    /** Confetti needs motion and a celebration; never under reduced motion. */
    fun confetti(reducedMotion: Boolean, celebrate: Boolean): Boolean = celebrate && animate(reducedMotion)

    /** True when the system animator scale is 0 (the "remove animations" setting). */
    fun isReducedScale(animatorScale: Float): Boolean = animatorScale == 0f
}
