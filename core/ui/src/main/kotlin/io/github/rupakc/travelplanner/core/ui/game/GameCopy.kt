package io.github.rupakc.travelplanner.core.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.ui.R
import java.util.Locale

/**
 * Names of game ids (`game:badges.<id>.name|desc`, `game:rules.<id>`, `game:levels.<n>` on the web). An id this app
 * does not know (a newer backend) has no resource: the callers show [words] of the id instead.
 */
object GameCopy {
    private val badgeNames =
        mapOf(
            "verified" to R.string.game_badge_verified_name,
            "first_steps" to R.string.game_badge_first_steps_name,
            "traveller" to R.string.game_badge_traveller_name,
            "first_trip" to R.string.game_badge_first_trip_name,
            "trip_ready" to R.string.game_badge_trip_ready_name,
            "ready_regular" to R.string.game_badge_ready_regular_name,
            "smart_planner" to R.string.game_badge_smart_planner_name,
            "on_the_road" to R.string.game_badge_on_the_road_name,
            "road_warrior" to R.string.game_badge_road_warrior_name,
            "local_explorer" to R.string.game_badge_local_explorer_name,
            "local_legend" to R.string.game_badge_local_legend_name,
            "countries_3" to R.string.game_badge_countries_3_name,
            "countries_10" to R.string.game_badge_countries_10_name,
            "countries_30" to R.string.game_badge_countries_30_name,
            "continents_3" to R.string.game_badge_continents_3_name,
            "continents_6" to R.string.game_badge_continents_6_name,
            "storyteller" to R.string.game_badge_storyteller_name,
            "memory_keeper" to R.string.game_badge_memory_keeper_name,
            "deal_hunter" to R.string.game_badge_deal_hunter_name,
            "deal_hunter_silver" to R.string.game_badge_deal_hunter_silver_name,
            "deal_hunter_gold" to R.string.game_badge_deal_hunter_gold_name,
            "bullseye" to R.string.game_badge_bullseye_name,
            "saver_50" to R.string.game_badge_saver_50_name,
            "saver_200" to R.string.game_badge_saver_200_name,
            "saver_1000" to R.string.game_badge_saver_1000_name,
        )

    private val badgeDescs =
        mapOf(
            "verified" to R.string.game_badge_verified_desc,
            "first_steps" to R.string.game_badge_first_steps_desc,
            "traveller" to R.string.game_badge_traveller_desc,
            "first_trip" to R.string.game_badge_first_trip_desc,
            "trip_ready" to R.string.game_badge_trip_ready_desc,
            "ready_regular" to R.string.game_badge_ready_regular_desc,
            "smart_planner" to R.string.game_badge_smart_planner_desc,
            "on_the_road" to R.string.game_badge_on_the_road_desc,
            "road_warrior" to R.string.game_badge_road_warrior_desc,
            "local_explorer" to R.string.game_badge_local_explorer_desc,
            "local_legend" to R.string.game_badge_local_legend_desc,
            "countries_3" to R.string.game_badge_countries_3_desc,
            "countries_10" to R.string.game_badge_countries_10_desc,
            "countries_30" to R.string.game_badge_countries_30_desc,
            "continents_3" to R.string.game_badge_continents_3_desc,
            "continents_6" to R.string.game_badge_continents_6_desc,
            "storyteller" to R.string.game_badge_storyteller_desc,
            "memory_keeper" to R.string.game_badge_memory_keeper_desc,
            "deal_hunter" to R.string.game_badge_deal_hunter_desc,
            "deal_hunter_silver" to R.string.game_badge_deal_hunter_silver_desc,
            "deal_hunter_gold" to R.string.game_badge_deal_hunter_gold_desc,
            "bullseye" to R.string.game_badge_bullseye_desc,
            "saver_50" to R.string.game_badge_saver_50_desc,
            "saver_200" to R.string.game_badge_saver_200_desc,
            "saver_1000" to R.string.game_badge_saver_1000_desc,
        )

    private val ruleNames =
        mapOf(
            "email_verified" to R.string.game_rule_email_verified,
            "onboarding_complete" to R.string.game_rule_onboarding_complete,
            "traveller_type" to R.string.game_rule_traveller_type,
            "plan_saved" to R.string.game_rule_plan_saved,
            "trip_ready" to R.string.game_rule_trip_ready,
            "smart_planner" to R.string.game_rule_smart_planner,
            "trip_checkin" to R.string.game_rule_trip_checkin,
            "place_visited" to R.string.game_rule_place_visited,
            "country_visited" to R.string.game_rule_country_visited,
            "continent_visited" to R.string.game_rule_continent_visited,
            "recap_complete" to R.string.game_rule_recap_complete,
            "deal_drop" to R.string.game_rule_deal_drop,
            "deal_target" to R.string.game_rule_deal_target,
            "deal_saved_50" to R.string.game_rule_deal_saved_50,
            "deal_saved_200" to R.string.game_rule_deal_saved_200,
            "deal_saved_1000" to R.string.game_rule_deal_saved_1000,
        )

    private val levelNames =
        listOf(
            R.string.game_level_1,
            R.string.game_level_2,
            R.string.game_level_3,
            R.string.game_level_4,
            R.string.game_level_5,
            R.string.game_level_6,
            R.string.game_level_7,
            R.string.game_level_8,
            R.string.game_level_9,
            R.string.game_level_10,
        )

    @StringRes fun badgeName(id: String): Int? = badgeNames[id]

    @StringRes fun badgeDesc(id: String): Int? = badgeDescs[id]

    @StringRes fun ruleName(id: String): Int? = ruleNames[id]

    @StringRes fun levelName(level: Int): Int? = levelNames.getOrNull(level - 1)

    /** `first_steps` becomes "First steps": the fallback for an id without copy. */
    fun words(id: String): String =
        id.replace('_', ' ').trim().replaceFirstChar { it.titlecase(Locale.ENGLISH) }
}

/** The badge name in the app language, or the id's words. */
@Composable
fun badgeNameText(id: String): String = GameCopy.badgeName(id)?.let { stringResource(it) } ?: GameCopy.words(id)

/** The badge description, or null for an id without copy. */
@Composable
fun badgeDescText(id: String): String? = GameCopy.badgeDesc(id)?.let { stringResource(it) }

/** The rule name in the app language, or the id's words. */
@Composable
fun ruleNameText(id: String): String = GameCopy.ruleName(id)?.let { stringResource(it) } ?: GameCopy.words(id)

/** The level title ("Explorer") or null above the known range. */
@Composable
fun levelNameText(level: Int): String? = GameCopy.levelName(level)?.let { stringResource(it) }
