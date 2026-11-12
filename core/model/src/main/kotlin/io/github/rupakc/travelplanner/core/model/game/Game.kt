package io.github.rupakc.travelplanner.core.model.game

import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GameDelta` of the Accounts contract. Where it appears it is nullable: `null` means the game is disabled;
 * an empty delta is `{xp: <total>, awards: [], badges: [], level_up: null}`.
 */
@Serializable
data class GameDelta(
    val xp: Int = 0,
    val awards: List<GameAward> = emptyList(),
    val badges: List<EarnedBadge> = emptyList(),
    @SerialName("level_up") val levelUp: LevelUp? = null,
) {
    /** Nothing to celebrate. */
    val isEmpty: Boolean get() = awards.isEmpty() && badges.isEmpty() && levelUp == null
}

@Serializable
data class GameAward(val rule: String, val points: Int = 0)

@Serializable
data class EarnedBadge(
    val id: String,
    @SerialName("earned_at") val earnedAt: String? = null,
)

@Serializable
data class LevelUp(val from: Int = 0, val to: Int = 0)

/** `GET /game/me` (404 = game disabled). */
@Serializable
data class GameMe(
    val xp: Int = 0,
    val level: Int = 1,
    @SerialName("level_min_xp") val levelMinXp: Int = 0,
    @SerialName("next_level_xp") val nextLevelXp: LenientInt = null,
    val badges: List<BadgeProgress> = emptyList(),
    val streaks: Map<String, StreakDto> = emptyMap(),
    val recent: List<RecentAward> = emptyList(),
    val hidden: Boolean = false,
)

@Serializable
data class BadgeProgress(
    val id: String,
    val progress: Int = 0,
    val target: Int = 1,
    @SerialName("earned_at") val earnedAt: String? = null,
)

/** A streak (`daily`, `trip_checkin`). */
@Serializable
data class StreakDto(
    val current: Int = 0,
    val longest: Int = 0,
    @SerialName("last_day") val lastDay: String? = null,
    val freezes: Int = 0,
)

@Serializable
data class RecentAward(
    val rule: String,
    val points: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

/** `GET /game/catalog`. */
@Serializable
data class GameCatalog(
    val version: Int = 1,
    val rules: List<GameRule> = emptyList(),
    val badges: List<BadgeDef> = emptyList(),
    val levels: List<LevelDef> = emptyList(),
)

@Serializable
data class GameRule(
    val id: String,
    val points: Int = 0,
    @SerialName("daily_cap") val dailyCap: LenientInt = null,
    val once: Boolean = false,
)

/** [tier] is `bronze` | `silver` | `gold`; [icon] is a Lucide name (the app maps it to a core icon or a generic one). */
@Serializable
data class BadgeDef(
    val id: String,
    val rule: String? = null,
    val target: Int = 1,
    val tier: String = "bronze",
    val icon: String? = null,
)

@Serializable
data class LevelDef(
    val level: Int,
    @SerialName("min_xp") val minXp: Int = 0,
)
