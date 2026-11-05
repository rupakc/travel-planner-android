package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.BUDGET
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.COMPANIONS
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.INTERESTS
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.NEEDS
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.PACE
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.PLANNING
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerClassification
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.ACCESSIBLE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.ADVENTURE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUDGET_BACKPACKER
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.BUSINESS
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.CULTURE_FOODIE
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.FAMILY
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.LUXURY
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType.RELAXATION
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TravellerClassifierTest {
    private val classifier = DefaultTravellerClassifier

    private fun classify(vararg answers: Pair<String, List<String>>) = classifier.classify(answers.toMap())

    @Test
    fun `quiz has six questions with unique ids and options`() {
        assertEquals(listOf(COMPANIONS, BUDGET, INTERESTS, PACE, PLANNING, NEEDS), TravellerQuiz.questions.map { it.id })
        TravellerQuiz.questions.forEach { q -> assertEquals(q.id, q.options.size, q.options.map { it.id }.distinct().size) }
        assertEquals(listOf(INTERESTS, NEEDS), TravellerQuiz.questions.filter { it.multiChoice }.map { it.id })
    }

    @Test
    fun `every scored type is reachable as primary`() {
        val cases =
            mapOf(
                BUDGET_BACKPACKER to classify(COMPANIONS to listOf("solo"), BUDGET to listOf("shoestring")),
                FAMILY to classify(COMPANIONS to listOf("family_kids")),
                LUXURY to classify(BUDGET to listOf("luxury")),
                ADVENTURE to classify(INTERESTS to listOf("adventure")),
                CULTURE_FOODIE to classify(INTERESTS to listOf("food")),
                BUSINESS to classify(COMPANIONS to listOf("work")),
                RELAXATION to classify(INTERESTS to listOf("beaches")),
            )
        cases.forEach { (type, result) -> assertEquals(type, result.primary) }
        // Together with ACCESSIBLE (secondary-only) that covers every type.
        assertEquals(TravellerType.entries.toSet() - ACCESSIBLE, cases.keys)
    }

    @Test
    fun `accessible is reachable and added as a secondary for each accessibility need`() {
        listOf("wheelchair", "visual_impairment", "hearing_impairment", "cognitive_disability").forEach { need ->
            val result = classify(COMPANIONS to listOf("work"), NEEDS to listOf(need))
            assertEquals(BUSINESS, result.primary)
            assertEquals(listOf(ACCESSIBLE), result.secondary)
        }
    }

    @Test
    fun `accessible comes after the scored secondary and never replaces types`() {
        val result = classify(PACE to listOf("packed"), NEEDS to listOf("wheelchair"))
        assertEquals(BUDGET_BACKPACKER, result.primary)
        assertEquals(listOf(ADVENTURE, ACCESSIBLE), result.secondary)
    }

    @Test
    fun `infants and dietary needs do not add accessible`() {
        val result = classify(NEEDS to listOf("infants", "dietary"))
        assertEquals(FAMILY, result.primary)
        assertEquals(emptyList<TravellerType>(), result.secondary)
    }

    @Test
    fun `ties are broken by the fixed type order`() {
        // packed = ADVENTURE 1, BUSINESS 1, BUDGET_BACKPACKER 1
        val result = classify(PACE to listOf("packed"))
        assertEquals(BUDGET_BACKPACKER, result.primary)
        assertEquals(listOf(ADVENTURE), result.secondary)
        // loose = CULTURE_FOODIE 1, RELAXATION 1
        assertEquals(TravellerClassification(CULTURE_FOODIE, listOf(RELAXATION)), classify(PLANNING to listOf("loose")))
    }

    @Test
    fun `secondary needs at least 60 percent of the primary score`() {
        // LUXURY 5, RELAXATION 3, CULTURE_FOODIE 1 → 3/5 = 60 % → secondary
        val atThreshold = mapOf(BUDGET to listOf("luxury"), INTERESTS to listOf("shopping", "beaches"), PLANNING to listOf("loose"))
        assertEquals(mapOf(LUXURY to 5, RELAXATION to 3, CULTURE_FOODIE to 1), DefaultTravellerClassifier.scores(atThreshold).nonZero())
        assertEquals(TravellerClassification(LUXURY, listOf(RELAXATION)), classifier.classify(atThreshold))

        // LUXURY 5, RELAXATION 2 → 40 % → none
        val below = classify(BUDGET to listOf("luxury"), INTERESTS to listOf("shopping", "beaches"))
        assertEquals(TravellerClassification(LUXURY, emptyList()), below)
    }

    @Test
    fun `only one scored secondary even when several qualify`() {
        // partner: RELAXATION 2, LUXURY 1, CULTURE_FOODIE 1 → plus food: CULTURE_FOODIE 3 → CF 3, R 2, L 1
        val result = classify(COMPANIONS to listOf("partner"), INTERESTS to listOf("food"))
        assertEquals(CULTURE_FOODIE, result.primary)
        assertEquals(listOf(RELAXATION), result.secondary)
    }

    @Test
    fun `empty or skipped answers give the default type`() {
        val expected = TravellerClassification(TravellerQuiz.DEFAULT_TYPE, emptyList())
        assertEquals(CULTURE_FOODIE, TravellerQuiz.DEFAULT_TYPE)
        assertEquals(expected, classifier.classify(emptyMap()))
        assertEquals(expected, classify(COMPANIONS to emptyList(), INTERESTS to emptyList()))
        assertEquals(expected, classify(NEEDS to listOf("dietary")))
    }

    @Test
    fun `unknown questions and options are ignored`() {
        val expected = TravellerClassification(TravellerQuiz.DEFAULT_TYPE, emptyList())
        assertEquals(expected, classify("bogus" to listOf("work"), COMPANIONS to listOf("aliens")))
    }

    @Test
    fun `single choice questions only count the first valid answer`() {
        assertEquals(BUSINESS, classify(COMPANIONS to listOf("nope", "work", "family_kids")).primary)
    }

    @Test
    fun `duplicate answers count once`() {
        assertEquals(mapOf(CULTURE_FOODIE to 2), DefaultTravellerClassifier.scores(mapOf(INTERESTS to listOf("food", "food"))).nonZero())
    }

    @Test
    fun `classification is deterministic`() {
        val answers = mapOf(COMPANIONS to listOf("friends"), INTERESTS to listOf("adventure", "nightlife", "food"), PACE to listOf("packed"))
        val first = classifier.classify(answers)
        repeat(5) { assertEquals(first, classifier.classify(answers)) }
    }

    @Test
    fun `every option weight targets a scored type`() {
        TravellerQuiz.questions.flatMap { it.options }.forEach { option ->
            assertTrue(option.id, option.weights.keys.none { it == ACCESSIBLE })
            assertTrue(option.id, option.weights.values.all { it > 0 })
        }
    }

    @Test
    fun `profile types round trip`() {
        val classification = TravellerClassification(FAMILY, listOf(RELAXATION, ACCESSIBLE))
        val profile = TravellerProfile().withClassification(mapOf(COMPANIONS to listOf("family_kids")), classification)
        assertEquals(listOf(FAMILY, RELAXATION, ACCESSIBLE), profile.types())
        assertEquals(emptyList<TravellerType>(), TravellerProfile().types())
        assertEquals(listOf(LUXURY), TravellerProfile(primaryType = "LUXURY", secondaryTypes = listOf("GONE")).types())
    }

    private fun Map<TravellerType, Int>.nonZero() = filterValues { it > 0 }
}
