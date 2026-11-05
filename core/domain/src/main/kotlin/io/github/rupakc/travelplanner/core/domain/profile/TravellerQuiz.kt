package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.ACCESSIBLE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.ADVENTURE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUDGET_BACKPACKER
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUSINESS
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.CULTURE_FOODIE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.FAMILY
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.LUXURY
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.RELAXATION

/** One question of the onboarding quiz (spec §6.1). [id] is the key in `TravellerProfile.answers`. */
data class QuizQuestion(val id: String, val multiChoice: Boolean, val options: List<QuizOption>)

/** Default traveller counts a "who do you travel with" answer pre-fills (server Preferences). */
data class TravellerCounts(val adults: Int, val children: Int = 0)

/**
 * One answer option and everything it means: classifier [weights], plus what it pre-fills on the
 * server (`PUT /api/preferences`): web [interestIds], [budgetCategory], [accessibilityId],
 * [travellers], [addsInfant]. [pace] is a search-only field (no Preferences field) kept on the device.
 */
data class QuizOption(
    val id: String,
    val weights: Map<TravellerType, Int> = emptyMap(),
    val interestIds: List<String> = emptyList(),
    val budgetCategory: String? = null,
    val accessibilityId: String? = null,
    val travellers: TravellerCounts? = null,
    val addsInfant: Boolean = false,
    val pace: String? = null,
)

/**
 * THE quiz table: questions, options, scoring weights and Preferences mapping in one place.
 * Mirrored in docs/Onboarding-and-Traveller-Types.md — change both together.
 */
object TravellerQuiz {
    const val COMPANIONS = "companions"
    const val BUDGET = "budget"
    const val INTERESTS = "interests"
    const val PACE = "pace"
    const val PLANNING = "planning"
    const val NEEDS = "needs"

    /** Primary type when no answer scores (everything skipped): the broad "see and taste the place" traveller. */
    val DEFAULT_TYPE: TravellerType = CULTURE_FOODIE

    /** Secondary type threshold: second score ≥ 60 % of the primary score (spec §6.2). */
    const val SECONDARY_PERCENT = 60

    val questions: List<QuizQuestion> =
        listOf(
            QuizQuestion(
                COMPANIONS,
                multiChoice = false,
                options =
                    listOf(
                        QuizOption("solo", mapOf(BUDGET_BACKPACKER to 2, ADVENTURE to 1), travellers = TravellerCounts(adults = 1)),
                        QuizOption(
                            "partner",
                            mapOf(RELAXATION to 2, LUXURY to 1, CULTURE_FOODIE to 1),
                            travellers = TravellerCounts(adults = 2),
                        ),
                        QuizOption(
                            "family_kids",
                            mapOf(FAMILY to 4),
                            interestIds = listOf("family"),
                            travellers = TravellerCounts(adults = 2, children = 1),
                        ),
                        QuizOption("friends", mapOf(ADVENTURE to 2, BUDGET_BACKPACKER to 1), travellers = TravellerCounts(adults = 3)),
                        QuizOption("work", mapOf(BUSINESS to 4), travellers = TravellerCounts(adults = 1)),
                    ),
            ),
            QuizQuestion(
                BUDGET,
                multiChoice = false,
                options =
                    listOf(
                        QuizOption("shoestring", mapOf(BUDGET_BACKPACKER to 3), budgetCategory = "low"),
                        QuizOption("mid_range", mapOf(CULTURE_FOODIE to 1, FAMILY to 1), budgetCategory = "medium"),
                        QuizOption("comfort", mapOf(RELAXATION to 1, BUSINESS to 1), budgetCategory = "medium"),
                        QuizOption("luxury", mapOf(LUXURY to 4), budgetCategory = "high"),
                    ),
            ),
            QuizQuestion(
                INTERESTS,
                multiChoice = true,
                options =
                    listOf(
                        QuizOption("food", mapOf(CULTURE_FOODIE to 2), interestIds = listOf("food")),
                        QuizOption("history_culture", mapOf(CULTURE_FOODIE to 2), interestIds = listOf("history", "culture")),
                        QuizOption("nature", mapOf(ADVENTURE to 1, RELAXATION to 1), interestIds = listOf("nature")),
                        QuizOption("adventure", mapOf(ADVENTURE to 2), interestIds = listOf("adventure")),
                        QuizOption("nightlife", mapOf(BUDGET_BACKPACKER to 1), interestIds = listOf("nightlife")),
                        QuizOption("shopping", mapOf(LUXURY to 1), interestIds = listOf("shopping")),
                        QuizOption("art_museums", mapOf(CULTURE_FOODIE to 1), interestIds = listOf("art")),
                        QuizOption("beaches", mapOf(RELAXATION to 2), interestIds = listOf("nature", "wellness")),
                        QuizOption("photography", mapOf(ADVENTURE to 1, CULTURE_FOODIE to 1), interestIds = listOf("art")),
                        QuizOption("wellness", mapOf(RELAXATION to 1, LUXURY to 1), interestIds = listOf("wellness")),
                    ),
            ),
            QuizQuestion(
                PACE,
                multiChoice = false,
                options =
                    listOf(
                        QuizOption("relaxed", mapOf(RELAXATION to 2, FAMILY to 1), pace = "relaxed"),
                        QuizOption("balanced", mapOf(CULTURE_FOODIE to 1), pace = "balanced"),
                        QuizOption("packed", mapOf(ADVENTURE to 1, BUSINESS to 1, BUDGET_BACKPACKER to 1), pace = "packed"),
                    ),
            ),
            QuizQuestion(
                PLANNING,
                multiChoice = false,
                options =
                    listOf(
                        QuizOption("ahead_deals", mapOf(BUDGET_BACKPACKER to 1, FAMILY to 1)),
                        QuizOption("loose", mapOf(CULTURE_FOODIE to 1, RELAXATION to 1)),
                        QuizOption("spontaneous", mapOf(ADVENTURE to 1, BUDGET_BACKPACKER to 1)),
                    ),
            ),
            QuizQuestion(
                NEEDS,
                multiChoice = true,
                options =
                    listOf(
                        QuizOption("wheelchair", accessibilityId = "wheelchair"),
                        QuizOption("visual_impairment", accessibilityId = "visual_impairment"),
                        QuizOption("hearing_impairment", accessibilityId = "hearing_impairment"),
                        QuizOption("cognitive_disability", accessibilityId = "cognitive_disability"),
                        QuizOption("infants", mapOf(FAMILY to 3), addsInfant = true),
                        QuizOption("dietary"),
                    ),
            ),
        )

    /** Question by id, or null. */
    fun question(id: String): QuizQuestion? = questions.firstOrNull { it.id == id }

    /**
     * Valid options chosen for [questionId] in [answers]: unknown ids dropped, duplicates removed,
     * single-choice questions keep only the first valid answer.
     */
    fun chosen(answers: Map<String, List<String>>, questionId: String): List<QuizOption> {
        val question = question(questionId) ?: return emptyList()
        val picked =
            answers[questionId].orEmpty().distinct().mapNotNull { id -> question.options.firstOrNull { it.id == id } }
        return if (question.multiChoice) picked else picked.take(1)
    }

    /** All valid chosen options across the quiz. */
    fun chosen(answers: Map<String, List<String>>): List<QuizOption> = questions.flatMap { chosen(answers, it.id) }

    /** True when any of the web's four accessibility needs was chosen (adds [ACCESSIBLE] as a secondary). */
    fun hasAccessibilityNeed(answers: Map<String, List<String>>): Boolean = chosen(answers, NEEDS).any { it.accessibilityId != null }
}
