package io.github.rupakc.travelplanner.feature.play.quiz

import io.github.rupakc.travelplanner.core.model.play.QuizOption
import io.github.rupakc.travelplanner.core.model.play.QuizQuestion
import io.github.rupakc.travelplanner.feature.play.R
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizTextTest {
    private fun q(kind: String?, vararg prompt: Pair<String, String>) =
        QuizQuestion(0, kind, JsonObject(prompt.associate { it.first to JsonPrimitive(it.second) }))

    @Test fun `known templates map to their copy with the right argument`() {
        assertEquals(PromptCopy(R.string.play_quiz_prompt_flag), QuizText.prompt(q("flag", "template" to "flag", "flag" to "x"), Locale.ENGLISH))
        assertEquals(
            PromptCopy(R.string.play_quiz_prompt_capital_of, "Japan"),
            QuizText.prompt(q("capital_of", "template" to "capital_of", "code" to "JP", "country" to "Japan"), Locale.ENGLISH),
        )
        assertEquals(
            PromptCopy(R.string.play_quiz_prompt_country_of_capital, "Lima"),
            QuizText.prompt(q("country_of_capital", "template" to "country_of_capital", "capital" to "Lima"), Locale.ENGLISH),
        )
        assertEquals(
            PromptCopy(R.string.play_quiz_prompt_landmark_country, "Petra"),
            QuizText.prompt(q("landmark_country", "landmark" to "Petra"), Locale.ENGLISH),
        )
        assertEquals(PromptCopy(R.string.play_quiz_prompt_dish_country, "Pho"), QuizText.prompt(q("dish_country", "dish" to "Pho"), Locale.ENGLISH))
    }

    @Test fun `an unknown kind or a missing field is the generic question`() {
        assertEquals(PromptCopy(R.string.play_quiz_prompt_generic), QuizText.prompt(q("riddle", "template" to "riddle"), Locale.ENGLISH))
        assertEquals(PromptCopy(R.string.play_quiz_prompt_generic), QuizText.prompt(q("dish_country"), Locale.ENGLISH))
        assertEquals(PromptCopy(R.string.play_quiz_prompt_generic), QuizText.prompt(q(null), Locale.ENGLISH))
    }

    @Test fun `country names are localised from the code with the server name as fallback`() {
        assertEquals("Japón", QuizText.regionName("JP", "Japan", Locale.forLanguageTag("es")))
        assertEquals("Japan", QuizText.regionName("jp", "x", Locale.ENGLISH))
        assertEquals("Atlantis", QuizText.regionName("??", "Atlantis", Locale.ENGLISH))
        assertEquals("Atlantis", QuizText.regionName(null, "Atlantis", Locale.ENGLISH))
        assertNull(QuizText.regionName(null, " ", Locale.ENGLISH))
        assertEquals("Deutschland", QuizText.optionLabel(QuizOption("de", "Germany", "DE"), Locale.GERMAN))
        assertEquals("Lima", QuizText.optionLabel(QuizOption("lima", "Lima"), Locale.GERMAN))
    }
}
