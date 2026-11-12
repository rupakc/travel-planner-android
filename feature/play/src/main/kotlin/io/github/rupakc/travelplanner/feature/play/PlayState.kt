package io.github.rupakc.travelplanner.feature.play

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.model.game.BadgeDef
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameMe
import io.github.rupakc.travelplanner.core.model.trips.Stats
import java.time.LocalDate

/** Where the Play screen is. */
sealed interface PlayPhase {
    data object Loading : PlayPhase

    /** `GET /game/me` answered 404 or `hidden` (game disabled or `hide_game`): the screen and tab are not shown. */
    data object Hidden : PlayPhase

    /** [message] is the backend's own words (429...), or null for the local copy. */
    data class Error(val message: String?) : PlayPhase

    data object Loaded : PlayPhase
}

enum class BadgeTier {
    BRONZE,
    SILVER,
    GOLD,
    ;

    companion object {
        fun of(value: String?): BadgeTier? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

@Immutable
data class BadgeUi(val id: String, val progress: Int, val target: Int, val earnedAt: String?, val tier: BadgeTier?) {
    val earned: Boolean get() = earnedAt != null || progress >= target
}

/** [id] is `daily` or `trip_checkin`. */
@Immutable
data class StreakUi(val id: String, val current: Int, val longest: Int, val lastDay: String?)

@Immutable
data class RecentUi(val rule: String, val points: Int, val createdAt: String?)

@Immutable
data class CountryUi(val code: String, val name: String, val visitedAt: String?)

@Immutable
data class PlayUiState(
    val phase: PlayPhase = PlayPhase.Loading,
    val level: Int = 1,
    val xp: Int = 0,
    val xpIntoLevel: Int = 0,
    /** XP this level spans; 0 at the top level. */
    val xpForLevel: Int = 0,
    val progress: Float = 0f,
    /** XP to the next level, or null at the top. */
    val remainingXp: Int? = null,
    val badges: List<BadgeUi> = emptyList(),
    val streaks: List<StreakUi> = emptyList(),
    val recent: List<RecentUi> = emptyList(),
    /** False without `features.trips` (stats unavailable): the countries and goal sections hide. */
    val tripsVisible: Boolean = false,
    val countries: List<CountryUi> = emptyList(),
    val bucketGoal: Int? = null,
)

sealed interface PlayEvent {
    data object Retry : PlayEvent
}

/** Pure mapping from the two game reads (and optional trip stats) to the screen state. */
internal object PlayMapper {
    private val streakOrder = listOf("daily", "trip_checkin")

    fun loaded(me: GameMe, catalog: GameCatalog?, stats: Stats?): PlayUiState {
        val span = me.nextLevelXp?.let { (it - me.levelMinXp).coerceAtLeast(1) } ?: 0
        val into = (me.xp - me.levelMinXp).coerceAtLeast(0)
        val progress = if (span == 0) 1f else (into.toFloat() / span).coerceIn(0f, 1f)
        return PlayUiState(
            phase = PlayPhase.Loaded,
            level = me.level,
            xp = me.xp,
            xpIntoLevel = into,
            xpForLevel = span,
            progress = progress,
            remainingXp = me.nextLevelXp?.let { (it - me.xp).coerceAtLeast(0) },
            badges = badges(me, catalog?.badges.orEmpty()),
            streaks = streakOrder.mapNotNull { id -> me.streaks[id]?.let { StreakUi(id, it.current, it.longest, it.lastDay) } },
            recent = me.recent.map { RecentUi(it.rule, it.points, it.createdAt) },
            tripsVisible = stats != null,
            countries = stats?.countries.orEmpty().map { CountryUi(it.code, it.name?.takeIf(String::isNotBlank) ?: it.code, it.visitedAt) },
            bucketGoal = stats?.bucketGoal,
        )
    }

    /** Catalog order first (tiers come from there), then any badge only the player's list knows. */
    private fun badges(me: GameMe, defs: List<BadgeDef>): List<BadgeUi> {
        val mine = me.badges.associateBy { it.id }
        val fromCatalog =
            defs.map { d ->
                val p = mine[d.id]
                BadgeUi(d.id, p?.progress ?: 0, p?.target ?: d.target, p?.earnedAt, BadgeTier.of(d.tier))
            }
        val known = defs.map { it.id }.toSet()
        return fromCatalog + me.badges.filter { it.id !in known }.map { BadgeUi(it.id, it.progress, it.target, it.earnedAt, null) }
    }

    /** The date part of an ISO timestamp (`2026-05-01T10:00:00Z`), or null. */
    fun dateOf(iso: String?): LocalDate? = iso?.take(DATE_LENGTH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private const val DATE_LENGTH = 10
}
