package io.github.rupakc.travelplanner.core.domain.profile

import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.BUDGET
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.COMPANIONS
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.INTERESTS
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.NEEDS
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz.PACE
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesMappingTest {
    /** The web's 10 interest ids and 4 accessibility ids (SearchPage INTERESTS, TravelerPanel). */
    private val webInterests = setOf("food", "history", "adventure", "culture", "nature", "shopping", "nightlife", "wellness", "art", "family")
    private val webAccessibility = setOf("wheelchair", "visual_impairment", "hearing_impairment", "cognitive_disability")

    @Test
    fun `only web interest and accessibility ids are produced, and all are reachable`() {
        val options = TravellerQuiz.questions.flatMap { it.options }
        val interests = options.flatMap { it.interestIds }.toSet()
        val needs = options.mapNotNull { it.accessibilityId }.toSet()
        assertEquals(webInterests, interests)
        assertEquals(webAccessibility, needs)
        assertTrue(options.mapNotNull { it.budgetCategory }.all { it in setOf("low", "medium", "high") })
    }

    @Test
    fun `budget maps shoestring low, mid and comfort medium, luxury high`() {
        mapOf("shoestring" to "low", "mid_range" to "medium", "comfort" to "medium", "luxury" to "high").forEach { (answer, category) ->
            assertEquals(category, Preferences().mergeQuizAnswers(mapOf(BUDGET to listOf(answer))).budgetCategory)
        }
    }

    @Test
    fun `skipped answers keep every existing field`() {
        val existing =
            Preferences(
                username = "alice",
                budgetCategory = "high",
                nationality = "Indian",
                currentResidence = "Munich, Germany",
                residencePermits = listOf("Schengen"),
                existingVisas = listOf("US"),
                interests = listOf("art"),
                numTravelers = 4,
                adults = 2,
                children = 1,
                seniors = 1,
                accessibilityNeeds = listOf("hearing_impairment"),
            )
        assertEquals(existing, existing.mergeQuizAnswers(emptyMap()))
    }

    @Test
    fun `lists are merged, never wiped`() {
        val existing = Preferences(interests = listOf("art", "food"), accessibilityNeeds = listOf("hearing_impairment"))
        val merged =
            existing.mergeQuizAnswers(
                mapOf(INTERESTS to listOf("food", "history_culture", "beaches"), NEEDS to listOf("wheelchair", "dietary")),
            )
        assertEquals(listOf("art", "food", "history", "culture", "nature", "wellness"), merged.interests)
        assertEquals(listOf("hearing_impairment", "wheelchair"), merged.accessibilityNeeds)
    }

    @Test
    fun `companions fill the default party and recount`() {
        val family = Preferences().mergeQuizAnswers(mapOf(COMPANIONS to listOf("family_kids")))
        assertEquals(2, family.adults)
        assertEquals(1, family.children)
        assertEquals(3, family.numTravelers)
        assertTrue("family" in family.interests)

        val partner = Preferences().mergeQuizAnswers(mapOf(COMPANIONS to listOf("partner")))
        assertEquals(2, partner.adults)
        assertEquals(2, partner.numTravelers)
    }

    @Test
    fun `a party the user already set is never overwritten`() {
        val bigFamily = Preferences(adults = 2, children = 3, numTravelers = 5)
        assertEquals(bigFamily.copy(interests = listOf("family")), bigFamily.mergeQuizAnswers(mapOf(COMPANIONS to listOf("family_kids"))))

        val soloWithSenior = Preferences(adults = 1, seniors = 1, numTravelers = 2)
        assertEquals(soloWithSenior, soloWithSenior.mergeQuizAnswers(mapOf(COMPANIONS to listOf("solo"))))
        assertEquals(soloWithSenior, soloWithSenior.mergeQuizAnswers(mapOf(COMPANIONS to listOf("work"))))
    }

    @Test
    fun `infants stay a minimum on a custom party`() {
        val merged =
            Preferences(adults = 2, children = 3, numTravelers = 5).mergeQuizAnswers(
                mapOf(
                    COMPANIONS to listOf("family_kids"),
                    NEEDS to listOf("infants"),
                ),
            )
        assertEquals(2, merged.adults)
        assertEquals(3, merged.children)
        assertEquals(1, merged.infants)
        assertEquals(6, merged.numTravelers)
    }

    @Test
    fun `infants need adds one infant unless there already are some`() {
        assertEquals(1, Preferences().mergeQuizAnswers(mapOf(NEEDS to listOf("infants"))).infants)
        assertEquals(2, Preferences().mergeQuizAnswers(mapOf(NEEDS to listOf("infants"))).numTravelers)
        assertEquals(2, Preferences(infants = 2, numTravelers = 3).mergeQuizAnswers(mapOf(NEEDS to listOf("infants"))).infants)
    }

    @Test
    fun `traveller total is clamped to the server range`() {
        val merged = Preferences(adults = 20, children = 20, numTravelers = 20).mergeQuizAnswers(mapOf(NEEDS to listOf("infants")))
        assertEquals(20, merged.numTravelers)
    }

    @Test
    fun `pace is read for the search form`() {
        assertEquals("packed", quizPace(mapOf(PACE to listOf("packed"))))
        assertNull(quizPace(emptyMap()))
    }
}
