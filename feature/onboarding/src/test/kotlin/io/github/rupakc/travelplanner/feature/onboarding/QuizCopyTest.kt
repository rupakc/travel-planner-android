package io.github.rupakc.travelplanner.feature.onboarding

import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.feature.onboarding.ui.OPTION_COPY
import io.github.rupakc.travelplanner.feature.onboarding.ui.questionTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class QuizCopyTest {
    @Test
    fun `every question and option of the table has copy, and nothing extra`() {
        val keys = TravellerQuiz.questions.flatMap { q -> q.options.map { "${q.id}/${it.id}" } }.toSet()
        assertEquals(keys, OPTION_COPY.keys)
        TravellerQuiz.questions.forEach { questionTitle(it.id) }
        assertEquals(OPTION_COPY.size, OPTION_COPY.values.map { it.label }.distinct().size)
    }
}
