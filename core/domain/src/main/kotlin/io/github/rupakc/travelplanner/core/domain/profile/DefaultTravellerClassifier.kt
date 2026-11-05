package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassification
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassifier
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile

/**
 * Weighted-score classifier over [TravellerQuiz] (spec §6.2). Pure and deterministic:
 * - score per type = sum of the chosen options' weights;
 * - primary = highest score, ties broken by [TravellerType] declaration order;
 * - one scored secondary = the runner-up when its score ≥ [TravellerQuiz.SECONDARY_PERCENT] % of the primary;
 * - [TravellerType.ACCESSIBLE] is appended as a secondary when any accessibility need is chosen;
 * - nothing scored (all skipped) → [TravellerQuiz.DEFAULT_TYPE], no scored secondary.
 */
object DefaultTravellerClassifier : TravellerClassifier {
    override fun classify(answers: Map<String, List<String>>): TravellerClassification {
        val ranked = scores(answers).entries.filter { it.value > 0 }.sortedWith(RANKING)
        val primary = ranked.firstOrNull()?.key ?: TravellerQuiz.DEFAULT_TYPE
        val top = ranked.firstOrNull()?.value ?: 0
        val runnerUp = ranked.getOrNull(1)?.takeIf { it.value * PERCENT >= top * TravellerQuiz.SECONDARY_PERCENT }?.key
        val secondary =
            buildList {
                if (runnerUp != null) add(runnerUp)
                if (TravellerQuiz.hasAccessibilityNeed(answers) && primary != TravellerType.ACCESSIBLE) add(TravellerType.ACCESSIBLE)
            }
        return TravellerClassification(primary, secondary.distinct())
    }

    /** Raw score per type (every type present, 0 when unscored). Exposed for tests and docs. */
    fun scores(answers: Map<String, List<String>>): Map<TravellerType, Int> {
        val totals = TravellerType.entries.associateWith { 0 }.toMutableMap()
        TravellerQuiz.chosen(answers).forEach { option ->
            option.weights.forEach { (type, weight) -> totals[type] = totals.getValue(type) + weight }
        }
        return totals
    }

    private const val PERCENT = 100
    private val RANKING = compareByDescending<Map.Entry<TravellerType, Int>> { it.value }.thenBy { it.key.ordinal }
}

/** Primary first, then secondaries; unknown stored names are dropped. Empty before onboarding. */
fun TravellerProfile.types(): List<TravellerType> =
    (listOfNotNull(primaryType) + secondaryTypes)
        .mapNotNull { name -> TravellerType.entries.firstOrNull { it.name == name } }
        .distinct()

/** A profile holding [classification] for [answers] (onboarding flag/time untouched). */
fun TravellerProfile.withClassification(answers: Map<String, List<String>>, classification: TravellerClassification): TravellerProfile =
    copy(primaryType = classification.primary.name, secondaryTypes = classification.secondary.map { it.name }, answers = answers)
