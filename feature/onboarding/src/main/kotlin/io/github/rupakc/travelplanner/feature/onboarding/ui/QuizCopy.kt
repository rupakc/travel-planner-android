package io.github.rupakc.travelplanner.feature.onboarding.ui

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.feature.onboarding.R

/** Display copy for one quiz option: label, optional hint and a decorative emoji glyph (web uses emoji for interests). */
internal data class OptionCopy(
    @param:StringRes val label: Int,
    val glyph: String,
    @param:StringRes val hint: Int? = null,
)

/** Question heading for a [TravellerQuiz] question id. */
@StringRes
internal fun questionTitle(questionId: String): Int =
    when (questionId) {
        TravellerQuiz.COMPANIONS -> R.string.onboarding_q_companions
        TravellerQuiz.BUDGET -> R.string.onboarding_q_budget
        TravellerQuiz.INTERESTS -> R.string.onboarding_q_interests
        TravellerQuiz.PACE -> R.string.onboarding_q_pace
        TravellerQuiz.PLANNING -> R.string.onboarding_q_planning
        TravellerQuiz.NEEDS -> R.string.onboarding_q_needs
        else -> error("No copy for question $questionId")
    }

/** Copy for every option of the quiz table, keyed `"<question>/<option>"`. A test checks it covers the table. */
internal val OPTION_COPY: Map<String, OptionCopy> =
    mapOf(
        "companions/solo" to OptionCopy(R.string.onboarding_opt_companions_solo, "🎒"),
        "companions/partner" to OptionCopy(R.string.onboarding_opt_companions_partner, "💑"),
        "companions/family_kids" to OptionCopy(R.string.onboarding_opt_companions_family_kids, "👨‍👩‍👧"),
        "companions/friends" to OptionCopy(R.string.onboarding_opt_companions_friends, "🎉"),
        "companions/work" to OptionCopy(R.string.onboarding_opt_companions_work, "💼"),
        "budget/shoestring" to OptionCopy(R.string.onboarding_opt_budget_shoestring, "🪙", R.string.onboarding_opt_budget_shoestring_hint),
        "budget/mid_range" to OptionCopy(R.string.onboarding_opt_budget_mid_range, "🏨", R.string.onboarding_opt_budget_mid_range_hint),
        "budget/comfort" to OptionCopy(R.string.onboarding_opt_budget_comfort, "🛎️", R.string.onboarding_opt_budget_comfort_hint),
        "budget/luxury" to OptionCopy(R.string.onboarding_opt_budget_luxury, "💎", R.string.onboarding_opt_budget_luxury_hint),
        "interests/food" to OptionCopy(R.string.onboarding_opt_interests_food, "🍜"),
        "interests/history_culture" to OptionCopy(R.string.onboarding_opt_interests_history_culture, "🏛️"),
        "interests/nature" to OptionCopy(R.string.onboarding_opt_interests_nature, "🌿"),
        "interests/adventure" to OptionCopy(R.string.onboarding_opt_interests_adventure, "🧗"),
        "interests/nightlife" to OptionCopy(R.string.onboarding_opt_interests_nightlife, "🌙"),
        "interests/shopping" to OptionCopy(R.string.onboarding_opt_interests_shopping, "🛍️"),
        "interests/art_museums" to OptionCopy(R.string.onboarding_opt_interests_art_museums, "🎨"),
        "interests/beaches" to OptionCopy(R.string.onboarding_opt_interests_beaches, "🏖️"),
        "interests/photography" to OptionCopy(R.string.onboarding_opt_interests_photography, "📷"),
        "interests/wellness" to OptionCopy(R.string.onboarding_opt_interests_wellness, "🧘"),
        "pace/relaxed" to OptionCopy(R.string.onboarding_opt_pace_relaxed, "🐢", R.string.onboarding_opt_pace_relaxed_hint),
        "pace/balanced" to OptionCopy(R.string.onboarding_opt_pace_balanced, "⚖️", R.string.onboarding_opt_pace_balanced_hint),
        "pace/packed" to OptionCopy(R.string.onboarding_opt_pace_packed, "⚡", R.string.onboarding_opt_pace_packed_hint),
        "planning/ahead_deals" to OptionCopy(R.string.onboarding_opt_planning_ahead_deals, "📅"),
        "planning/loose" to OptionCopy(R.string.onboarding_opt_planning_loose, "🗺️"),
        "planning/spontaneous" to OptionCopy(R.string.onboarding_opt_planning_spontaneous, "🎲"),
        "needs/wheelchair" to OptionCopy(R.string.onboarding_opt_needs_wheelchair, "♿"),
        "needs/visual_impairment" to OptionCopy(R.string.onboarding_opt_needs_visual_impairment, "👁️"),
        "needs/hearing_impairment" to OptionCopy(R.string.onboarding_opt_needs_hearing_impairment, "🦻"),
        "needs/cognitive_disability" to OptionCopy(R.string.onboarding_opt_needs_cognitive_disability, "🧠"),
        "needs/infants" to OptionCopy(R.string.onboarding_opt_needs_infants, "🍼"),
        "needs/dietary" to OptionCopy(R.string.onboarding_opt_needs_dietary, "🥗"),
    )

internal fun optionCopy(questionId: String, optionId: String): OptionCopy =
    OPTION_COPY["$questionId/$optionId"] ?: error("No copy for $questionId/$optionId")

/** Decorative glyph for the result screen's type name. */
internal fun TravellerType.glyph(): String =
    when (this) {
        TravellerType.BUDGET_BACKPACKER -> "🎒"
        TravellerType.FAMILY -> "👨‍👩‍👧"
        TravellerType.LUXURY -> "💎"
        TravellerType.ADVENTURE -> "🧗"
        TravellerType.CULTURE_FOODIE -> "🏛️"
        TravellerType.BUSINESS -> "💼"
        TravellerType.RELAXATION -> "🏖️"
        TravellerType.ACCESSIBLE -> "♿"
    }
