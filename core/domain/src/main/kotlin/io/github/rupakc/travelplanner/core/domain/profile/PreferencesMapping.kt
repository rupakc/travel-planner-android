package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.model.preferences.Preferences

/** Server limits for traveller counts (`PUT /api/preferences`). */
private const val MAX_TRAVELLERS = 20

/**
 * Merges quiz [answers] into the user's current server [Preferences] (spec §6.1), never wiping what they had:
 * - interests / accessibility_needs: union (existing first, in order), web ids only;
 * - budget_category: set when the budget question was answered;
 * - adults / children: filled from the companions answer only while the server still holds the
 *   default party (1 adult, no children / seniors / infants) — a party the user set is never overwritten;
 * - infants: at least 1 when "travelling with infants" was chosen (a minimum, never lowered);
 * - num_travelers = adults + children + seniors + infants, clamped to 1..20 (when counts changed);
 * - every other field (nationality, residence, permits, visas…) is kept as is.
 */
fun Preferences.mergeQuizAnswers(answers: Map<String, List<String>>): Preferences {
    val chosen = TravellerQuiz.chosen(answers)
    val interestsOut = (interests + chosen.flatMap { it.interestIds }).distinct()
    val needsOut = (accessibilityNeeds + chosen.mapNotNull { it.accessibilityId }).distinct()
    val budget = TravellerQuiz.chosen(answers, TravellerQuiz.BUDGET).firstOrNull()?.budgetCategory ?: budgetCategory
    val counts = TravellerQuiz.chosen(answers, TravellerQuiz.COMPANIONS).firstOrNull()?.travellers?.takeIf { hasDefaultParty() }
    val wantsInfant = chosen.any { it.addsInfant }
    val adultsOut = counts?.adults ?: adults
    val childrenOut = counts?.children ?: children
    val infantsOut = if (wantsInfant) maxOf(infants, 1) else infants
    val countsChanged = adultsOut != adults || childrenOut != children || infantsOut != infants
    return copy(
        interests = interestsOut,
        accessibilityNeeds = needsOut,
        budgetCategory = budget,
        adults = adultsOut,
        children = childrenOut,
        infants = infantsOut,
        numTravelers = if (countsChanged) (adultsOut + childrenOut + seniors + infantsOut).coerceIn(1, MAX_TRAVELLERS) else numTravelers,
    )
}

/** True while the server holds the untouched default party (1 adult, nobody else). */
private fun Preferences.hasDefaultParty(): Boolean = adults == 1 && children == 0 && seniors == 0 && infants == 0

/** The trip pace answer (`relaxed` / `balanced` / `packed`) for the search form, or null when skipped. */
fun quizPace(answers: Map<String, List<String>>): String? = TravellerQuiz.chosen(answers, TravellerQuiz.PACE).firstOrNull()?.pace
