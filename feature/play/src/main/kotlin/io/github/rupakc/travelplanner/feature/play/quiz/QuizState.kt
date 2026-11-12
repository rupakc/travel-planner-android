package io.github.rupakc.travelplanner.feature.play.quiz

import androidx.compose.runtime.Immutable
import io.github.rupakc.travelplanner.core.model.play.QuizQuestion
import io.github.rupakc.travelplanner.core.model.play.QuizSummary

/** Seconds shown per question. The countdown is for feel only: the server times every answer and never rejects a late one. */
const val QUESTION_SECONDS = 15

/** Where the quiz screen is. */
sealed interface QuizPhase {
    data object Loading : QuizPhase

    /** `403 game_hidden` or the Play feature is off: the shell leaves the screen. */
    data object Hidden : QuizPhase

    /** `409 already_done` (daily or challenge finished). */
    data object AlreadyDone : QuizPhase

    /** `410 session_expired` while answering: start again. */
    data object Expired : QuizPhase

    /** [message] is the backend's own words (429...), or null for the local copy. */
    data class Error(val message: String?) : QuizPhase

    data object Running : QuizPhase

    data object Finished : QuizPhase
}

/** The server's verdict on the answer just given. Points are the server's, never computed here. */
@Immutable
data class Reveal(val choice: String, val correct: Boolean, val correctChoice: String?, val points: Int)

@Immutable
data class QuizUiState(
    val phase: QuizPhase = QuizPhase.Loading,
    val questions: List<QuizQuestion> = emptyList(),
    /** Position in [questions] (0-based). */
    val position: Int = 0,
    val reveal: Reveal? = null,
    /** An answer is in flight. */
    val busy: Boolean = false,
    /** A failed answer send (backend text or null for the local copy); the question stays open. */
    val answerError: AnswerError? = null,
    val secondsLeft: Int = QUESTION_SECONDS,
    val resumed: Boolean = false,
    val summary: QuizSummary? = null,
    /** XP and streak lines show only while the game is visible (`hide_game`, ruling R4). */
    val gameVisible: Boolean = false,
) {
    val current: QuizQuestion? get() = questions.getOrNull(position)
    val isLast: Boolean get() = position >= questions.lastIndex
}

data class AnswerError(val message: String?)

sealed interface QuizEvent {
    data class Pick(val choice: String) : QuizEvent

    data object Next : QuizEvent

    data object Retry : QuizEvent
}
