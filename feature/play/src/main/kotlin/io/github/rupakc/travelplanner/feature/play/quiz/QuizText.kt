package io.github.rupakc.travelplanner.feature.play.quiz

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.core.model.play.QuestionKind
import io.github.rupakc.travelplanner.core.model.play.QuizOption
import io.github.rupakc.travelplanner.core.model.play.QuizQuestion
import io.github.rupakc.travelplanner.feature.play.R
import java.util.Locale

/** A question prompt resolved to a string resource plus its one optional argument (a country, capital, landmark or dish name). */
internal data class PromptCopy(
    @StringRes val res: Int,
    val arg: String? = null,
)

/**
 * Maps the server's prompt templates to localised copy (the web `play:quiz.prompt.*` strings). Country names are localised from
 * their ISO code; capitals, landmarks and dishes ship in English. An unknown `kind` (or a known one missing its field) becomes the
 * generic "Question" text, never a crash.
 */
internal object QuizText {
    fun prompt(question: QuizQuestion, locale: Locale): PromptCopy {
        val template = question.promptText("template") ?: question.kind
        val generic = PromptCopy(R.string.play_quiz_prompt_generic)
        return when (QuestionKind.from(template)) {
            QuestionKind.FLAG -> {
                PromptCopy(R.string.play_quiz_prompt_flag)
            }

            QuestionKind.CAPITAL_OF -> {
                regionName(question.promptText("code"), question.promptText("country"), locale)
                    ?.let { PromptCopy(R.string.play_quiz_prompt_capital_of, it) } ?: generic
            }

            QuestionKind.COUNTRY_OF_CAPITAL -> {
                question.promptText("capital")?.let { PromptCopy(R.string.play_quiz_prompt_country_of_capital, it) } ?: generic
            }

            QuestionKind.LANDMARK_COUNTRY -> {
                question.promptText("landmark")?.let { PromptCopy(R.string.play_quiz_prompt_landmark_country, it) } ?: generic
            }

            QuestionKind.DISH_COUNTRY -> {
                question.promptText("dish")?.let { PromptCopy(R.string.play_quiz_prompt_dish_country, it) } ?: generic
            }

            QuestionKind.UNKNOWN -> {
                generic
            }
        }
    }

    /** The flag emoji of a flag question, or null. */
    fun flag(question: QuizQuestion): String? = question.promptText("flag")?.takeIf { it.isNotBlank() }

    fun optionLabel(option: QuizOption, locale: Locale): String = regionName(option.code, option.label, locale) ?: option.label

    /** The region's name in [locale] from its ISO alpha-2 [code]; [fallback] (the server's English name) when the device does not know it. */
    fun regionName(code: String?, fallback: String?, locale: Locale): String? {
        val upper = code?.trim()?.uppercase()
        if (upper != null && upper.length == ISO2) {
            val name = runCatching { Locale.Builder().setRegion(upper).build().getDisplayCountry(locale) }.getOrNull()
            if (!name.isNullOrBlank() && name != upper) return name
        }
        return fallback?.takeIf { it.isNotBlank() }
    }

    private const val ISO2 = 2
}
