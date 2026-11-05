package io.github.rupakc.travelplanner.core.domain.traveller

/** Traveller types (spec §6.2). Order = tie-break order for the classifier. */
enum class TravellerType {
    BUDGET_BACKPACKER,
    FAMILY,
    LUXURY,
    ADVENTURE,
    CULTURE_FOODIE,
    BUSINESS,
    RELAXATION,

    /** Added as a secondary whenever any accessibility need is chosen; never hides other types. */
    ACCESSIBLE,
}

/** Classifier output: primary type plus secondaries (second ≥ 60 % of primary, and ACCESSIBLE when applicable). */
data class TravellerClassification(val primary: TravellerType, val secondary: List<TravellerType>)

/**
 * Pure, deterministic classifier from quiz answers (question id → chosen option ids).
 * Implemented by the onboarding team in core:domain with its weight table (spec §6.2, §12).
 */
fun interface TravellerClassifier {
    fun classify(answers: Map<String, List<String>>): TravellerClassification
}
