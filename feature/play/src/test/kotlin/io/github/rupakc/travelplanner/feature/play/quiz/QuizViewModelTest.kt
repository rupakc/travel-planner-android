package io.github.rupakc.travelplanner.feature.play.quiz

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.game.GameAward
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.play.AnswerResult
import io.github.rupakc.travelplanner.core.model.play.GivenAnswer
import io.github.rupakc.travelplanner.core.model.play.QuizOption
import io.github.rupakc.travelplanner.core.model.play.QuizQuestion
import io.github.rupakc.travelplanner.core.model.play.QuizSession
import io.github.rupakc.travelplanner.core.model.play.QuizSummary
import io.github.rupakc.travelplanner.core.model.play.SummaryStreak
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakePlayRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuizViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val play = FakePlayRepository()
    private val telemetry = FakeTelemetry()
    private val events = FakeGameEvents()

    private fun question(i: Int) = QuizQuestion(i, "flag", options = listOf(QuizOption("a", "A"), QuizOption("b", "B")))

    private val session = QuizSession(sessionId = "s1", mode = "daily", questions = (0..2).map(::question))

    private fun vm(mode: String = "daily", pack: String? = null) =
        QuizViewModel(SavedStateHandle(mapOf("mode" to mode, "pack" to pack)), play, telemetry, events)

    @Test(timeout = 10_000)
    fun `starts the daily quiz and reports play_quiz_start`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session)
            val s = vm().state.value
            assertEquals(QuizPhase.Running, s.phase)
            assertEquals(3, s.questions.size)
            assertEquals(0, s.position)
            assertEquals(listOf("startQuiz(daily,null)"), play.calls)
            assertEquals("play_quiz_start" to mapOf("mode" to "daily", "pack" to "none"), telemetry.events.single())
        }

    @Test(timeout = 10_000)
    fun `a pack quiz sends the pack`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session.copy(mode = "pack", pack = "flags"))
            vm("pack", "flags")
            assertEquals(listOf("startQuiz(pack,flags)"), play.calls)
            assertEquals(mapOf("mode" to "pack", "pack" to "flags"), telemetry.events.single().second)
        }

    @Test(timeout = 10_000)
    fun `answer is sent at once and the servers verdict is shown`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session)
            play.answerResult = AppResult.Success(AnswerResult(correct = false, correctChoice = "b", points = 0, elapsedMs = 4200))
            val vm = vm()
            vm.onEvent(QuizEvent.Pick("a"))
            val s = vm.state.value
            assertEquals(Reveal("a", false, "b", 0), s.reveal)
            assertTrue("answer(s1,0,a)" in play.calls)
            assertEquals("play_quiz_answer" to mapOf("correct" to false, "elapsed_s" to 4), telemetry.events.last())
        }

    @Test(timeout = 10_000)
    fun `a second tap while an answer is shown is ignored and next moves on`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session)
            play.answerResult = AppResult.Success(AnswerResult(correct = true, correctChoice = "a", points = 130))
            val vm = vm()
            vm.onEvent(QuizEvent.Pick("a"))
            vm.onEvent(QuizEvent.Pick("b"))
            assertEquals(1, play.calls.count { it.startsWith("answer") })
            assertEquals(130, vm.state.value.reveal?.points)
            vm.onEvent(QuizEvent.Next)
            assertEquals(1, vm.state.value.position)
            assertNull(vm.state.value.reveal)
        }

    @Test(timeout = 10_000)
    fun `the last answer finishes with the servers summary and publishes xp`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session.copy(questions = listOf(question(0))))
            val delta = GameDelta(xp = 10, awards = listOf(GameAward("quiz_daily", 10)))
            play.answerResult =
                AppResult.Success(
                    AnswerResult(
                        correct = true,
                        correctChoice = "a",
                        points = 140,
                        done = true,
                        summary = QuizSummary(score = 140, correct = 1, total = 1, xp = delta, streak = SummaryStreak(current = 4)),
                    ),
                )
            val vm = vm()
            vm.onEvent(QuizEvent.Pick("a"))
            assertEquals(QuizPhase.Running, vm.state.value.phase)
            vm.onEvent(QuizEvent.Next)
            val s = vm.state.value
            assertEquals(QuizPhase.Finished, s.phase)
            assertEquals(140, s.summary?.score)
            assertTrue(s.gameVisible)
            assertEquals(listOf(delta), events.published)
            assertEquals("play_quiz_done" to mapOf("mode" to "daily", "correct" to 1), telemetry.events.last())
        }

    @Test(timeout = 10_000)
    fun `hidden game shows no xp and publishes nothing`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            events.setVisible(false)
            play.sessionResult = AppResult.Success(session.copy(questions = listOf(question(0))))
            play.answerResult =
                AppResult.Success(
                    AnswerResult(
                        done = true,
                        summary = QuizSummary(score = 0, correct = 0, xp = GameDelta(xp = 10, awards = listOf(GameAward("quiz_daily", 10)))),
                    ),
                )
            val vm = vm()
            vm.onEvent(QuizEvent.Pick("a"))
            vm.onEvent(QuizEvent.Next)
            assertFalse(vm.state.value.gameVisible)
            assertTrue(events.published.isEmpty())
        }

    @Test(timeout = 10_000)
    fun `a started session resumes at the first open question`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult =
                AppResult.Success(session.copy(resumed = true, answers = listOf(GivenAnswer(0, "a", true, "a", 120), GivenAnswer(1, "b", false, "a", 0))))
            val s = vm().state.value
            assertEquals(2, s.position)
            assertTrue(s.resumed)
        }

    @Test(timeout = 10_000)
    fun `a finished session opens on its summary`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session.copy(done = true, summary = QuizSummary(score = 300, correct = 3, total = 3)))
            val s = vm().state.value
            assertEquals(QuizPhase.Finished, s.phase)
            assertEquals(300, s.summary?.score)
        }

    @Test(timeout = 10_000)
    fun `409 already_done shows the finished state`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Failure(AppError.Client(409, "Already played", "already_done"))
            assertEquals(QuizPhase.AlreadyDone, vm().state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `game_hidden and a disabled play hide the screen`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Failure(AppError.Forbidden("hidden", "game_hidden"))
            assertEquals(QuizPhase.Hidden, vm().state.value.phase)
            play.sessionResult = AppResult.Failure(AppError.NotFound("off", "play_disabled"))
            assertEquals(QuizPhase.Hidden, vm().state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `a rate limit message is shown as is and retry reloads`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Failure(AppError.RateLimited("Slow down", 30))
            val vm = vm()
            assertEquals(QuizPhase.Error("Slow down"), vm.state.value.phase)
            play.sessionResult = AppResult.Success(session)
            vm.onEvent(QuizEvent.Retry)
            assertEquals(QuizPhase.Running, vm.state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `an expired session asks to start again`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session)
            play.answerResult = AppResult.Failure(AppError.Client(410, "Expired", "session_expired"))
            val vm = vm()
            vm.onEvent(QuizEvent.Pick("a"))
            assertEquals(QuizPhase.Expired, vm.state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `a failed answer keeps the question open and can be sent again`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session)
            play.answerResult = AppResult.Failure(AppError.Network())
            val vm = vm()
            vm.onEvent(QuizEvent.Pick("a"))
            assertEquals(QuizPhase.Running, vm.state.value.phase)
            assertEquals(AnswerError(null), vm.state.value.answerError)
            assertNull(vm.state.value.reveal)
            play.answerResult = AppResult.Success(AnswerResult(correct = true, correctChoice = "a", points = 100))
            vm.onEvent(QuizEvent.Pick("a"))
            assertEquals(100, vm.state.value.reveal?.points)
            assertNull(vm.state.value.answerError)
        }

    @Test(timeout = 10_000)
    fun `the countdown is display only and never answers`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session)
            val vm = vm()
            assertEquals(QUESTION_SECONDS, vm.state.value.secondsLeft)
            advanceTimeBy(3_100)
            assertEquals(QUESTION_SECONDS - 3, vm.state.value.secondsLeft)
            advanceTimeBy(60_000)
            assertEquals(0, vm.state.value.secondsLeft)
            assertEquals(0, play.calls.count { it.startsWith("answer") })
            assertEquals(QuizPhase.Running, vm.state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `a head-to-head session is fetched by id, never created again`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(session.copy(sessionId = "h1", mode = "h2h", pack = "capitals"))
            val vm = QuizViewModel(SavedStateHandle(mapOf("mode" to "h2h", "sessionId" to "h1")), play, telemetry, events)
            assertEquals(QuizPhase.Running, vm.state.value.phase)
            assertEquals(listOf("quiz(h1)"), play.calls)
            assertEquals(mapOf("mode" to "h2h", "pack" to "capitals"), telemetry.events.single().second)
        }

    @Test(timeout = 10_000)
    fun `a head-to-head quiz without a session id is an error`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            val vm = QuizViewModel(SavedStateHandle(mapOf("mode" to "h2h")), play, telemetry, events)
            assertEquals(QuizPhase.Error(null), vm.state.value.phase)
            assertTrue(play.calls.isEmpty())
        }
}
