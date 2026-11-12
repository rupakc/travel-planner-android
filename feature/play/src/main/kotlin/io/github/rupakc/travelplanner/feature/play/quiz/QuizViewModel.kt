package io.github.rupakc.travelplanner.feature.play.quiz

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.PlayRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.play.AnswerResult
import io.github.rupakc.travelplanner.core.model.play.QuizMode
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.core.model.play.QuizSession
import io.github.rupakc.travelplanner.core.model.play.QuizSummary
import io.github.rupakc.travelplanner.core.model.social.SocialErrorCode
import io.github.rupakc.travelplanner.core.model.social.socialErrorCode
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One quiz session (daily, pack or the weekly challenge; head-to-head sessions are T5.5's [startSession] extension point).
 * Answers are sent at once and judged and timed by the server (R12): this class shows the server's `correct`, `correct_choice` and
 * `points` and never computes a score. A started session is resumed by the server (`resumed`, `answers`).
 * Route args (`mode`, optional `pack`) come from the navigation [SavedStateHandle].
 */
@HiltViewModel
class QuizViewModel
    @Inject
    constructor(
        handle: SavedStateHandle,
        private val play: PlayRepository,
        private val telemetry: Telemetry,
        private val gameEvents: GameEvents,
    ) : ViewModel() {
        private val mode = QuizMode.from(handle.get<String>(ARG_MODE))
        private val pack = QuizPack.from(handle.get<String>(ARG_PACK))

        private val mutable = MutableStateFlow(QuizUiState())
        val state: StateFlow<QuizUiState> = mutable.asStateFlow()

        private var sessionId: String? = null
        private var pendingSummary: QuizSummary? = null
        private var ticker: Job? = null

        init {
            load()
        }

        fun onEvent(event: QuizEvent) {
            when (event) {
                is QuizEvent.Pick -> pick(event.choice)
                QuizEvent.Next -> next()
                QuizEvent.Retry -> load()
            }
        }

        private fun load() {
            ticker?.cancel()
            mutable.value = QuizUiState(gameVisible = gameEvents.visible.value)
            viewModelScope.launch {
                when (val result = startSession()) {
                    is AppResult.Failure -> mutable.update { it.copy(phase = failurePhase(result.error)) }
                    is AppResult.Success -> begin(result.value)
                }
            }
        }

        /** The one place a session is obtained: T5.5 adds the h2h create/accept sources here. */
        private suspend fun startSession(): AppResult<QuizSession> = play.startQuiz(mode, pack)

        private fun begin(session: QuizSession) {
            sessionId = session.sessionId
            telemetry.emit(
                TelemetryEvents.PLAY_QUIZ_START,
                mapOf("mode" to (session.quizMode.wire ?: mode.wire ?: "daily"), "pack" to (pack?.wire ?: "none")),
            )
            val summary = session.summary
            if (session.done && summary != null) {
                mutable.update { it.copy(phase = QuizPhase.Finished, questions = session.questions, summary = summary) }
                return
            }
            val answered = session.answers.map { it.index }.toSet()
            val firstOpen = session.questions.indexOfFirst { it.index !in answered }
            if (session.questions.isEmpty()) {
                mutable.update { it.copy(phase = QuizPhase.Error(null)) }
                return
            }
            mutable.update {
                it.copy(
                    phase = QuizPhase.Running,
                    questions = session.questions,
                    position = if (firstOpen < 0) session.questions.lastIndex else firstOpen,
                    resumed = session.resumed || answered.isNotEmpty(),
                )
            }
            startTicker()
        }

        private fun pick(choice: String) {
            val s = mutable.value
            val question = s.current ?: return
            val id = sessionId ?: return
            if (s.phase != QuizPhase.Running || s.busy || s.reveal != null) return
            ticker?.cancel()
            mutable.update { it.copy(busy = true, answerError = null) }
            viewModelScope.launch {
                when (val result = play.answer(id, question.index, choice)) {
                    is AppResult.Success -> revealed(choice, result.value)
                    is AppResult.Failure -> answerFailed(result.error)
                }
            }
        }

        private fun revealed(choice: String, answer: AnswerResult) {
            telemetry.emit(
                TelemetryEvents.PLAY_QUIZ_ANSWER,
                mapOf("correct" to answer.correct, "elapsed_s" to ((answer.elapsedMs ?: 0) / MS_PER_S).coerceAtLeast(0)),
            )
            if (answer.done) pendingSummary = answer.summary ?: QuizSummary()
            mutable.update {
                it.copy(busy = false, reveal = Reveal(choice, answer.correct, answer.correctChoice, answer.points ?: 0))
            }
        }

        private fun answerFailed(error: AppError) {
            when (error.socialErrorCode()) {
                SocialErrorCode.SESSION_EXPIRED, SocialErrorCode.SESSION_NOT_FOUND -> {
                    mutable.update { it.copy(busy = false, phase = QuizPhase.Expired) }
                }

                SocialErrorCode.GAME_HIDDEN -> {
                    mutable.update { it.copy(busy = false, phase = QuizPhase.Hidden) }
                }

                else -> {
                    // The question stays open and can be answered again; the countdown (display only) is not restarted.
                    mutable.update { it.copy(busy = false, answerError = AnswerError(error.message)) }
                }
            }
        }

        private fun next() {
            val s = mutable.value
            if (s.phase != QuizPhase.Running || s.reveal == null) return
            val summary = pendingSummary
            if (summary != null) {
                finish(summary)
                return
            }
            mutable.update { it.copy(position = it.position + 1, reveal = null, answerError = null) }
            startTicker()
        }

        private fun finish(summary: QuizSummary) {
            telemetry.emit(
                TelemetryEvents.PLAY_QUIZ_DONE,
                mapOf("mode" to (mode.wire ?: "daily"), "correct" to (summary.correct ?: 0)),
            )
            // GameEvents drops the delta while the game is hidden, so XP is celebrated only for a visible game.
            gameEvents.publish(summary.xp)
            mutable.update { it.copy(phase = QuizPhase.Finished, summary = summary, reveal = null, gameVisible = gameEvents.visible.value) }
        }

        /** Counts 15..0 once per question for display only; it never submits or blocks anything. */
        private fun startTicker() {
            ticker?.cancel()
            mutable.update { it.copy(secondsLeft = QUESTION_SECONDS) }
            ticker =
                viewModelScope.launch {
                    for (left in QUESTION_SECONDS - 1 downTo 0) {
                        delay(TICK_MS)
                        mutable.update { it.copy(secondsLeft = left) }
                    }
                }
        }

        private fun failurePhase(error: AppError): QuizPhase =
            when (error.socialErrorCode()) {
                SocialErrorCode.ALREADY_DONE -> QuizPhase.AlreadyDone
                SocialErrorCode.GAME_HIDDEN, SocialErrorCode.PLAY_DISABLED -> QuizPhase.Hidden
                else -> if (error is AppError.NotFound) QuizPhase.Hidden else QuizPhase.Error(error.message)
            }

        companion object {
            const val ARG_MODE = "mode"
            const val ARG_PACK = "pack"
            private const val TICK_MS = 1_000L
            private const val MS_PER_S = 1_000
        }
    }
