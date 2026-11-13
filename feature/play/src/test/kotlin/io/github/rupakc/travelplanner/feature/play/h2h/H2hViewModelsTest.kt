package io.github.rupakc.travelplanner.feature.play.h2h

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.play.H2hList
import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.play.H2hOpponent
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.core.model.play.QuizSession
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlayRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class H2hViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val play = FakePlayRepository()
    private val telemetry = FakeTelemetry()
    private val flags = FakeJourneyFlagsRepository(JourneyFlags(game = true, play = true))

    private fun match(id: String, state: String, you: Int? = null, them: Int? = null, winner: String? = null) =
        H2hMatch(id, H2hOpponent("ana"), "flags", state, you, them, winner, "2026-11-15T10:00:00Z")

    private fun api(vararg m: H2hMatch) {
        play.h2hListResult = AppResult.Success(H2hList(m.toList()))
    }

    // ---- flags off: no request ----

    @Test(timeout = 10_000)
    fun `list and detail are hidden without a request when play or the game is off`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            for (off in listOf(JourneyFlags(game = true, play = false), JourneyFlags(game = false, play = true))) {
                flags.flags = off
                assertEquals(H2hListPhase.Hidden, H2hListViewModel(play, flags).state.value.phase)
                assertEquals(H2hDetailPhase.Hidden, detail().state.value.phase)
            }
            assertTrue(play.calls.none { it == "h2hMatches" })
        }

    // ---- list ----

    @Test(timeout = 10_000)
    fun `list loads matches and a quiet resume keeps them on failure`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "waiting_you"))
            val vm = H2hListViewModel(play, flags)
            assertEquals(H2hListPhase.Loaded, vm.state.value.phase)
            assertEquals(1, vm.state.value.matches.size)
            vm.onEvent(H2hListEvent.Resumed)
            play.h2hListResult = AppResult.Failure(AppError.Network())
            vm.onEvent(H2hListEvent.Resumed)
            assertEquals(H2hListPhase.Loaded, vm.state.value.phase)
            vm.onEvent(H2hListEvent.Retry)
            assertEquals(H2hListPhase.Error(null), vm.state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `list hides for game_hidden and 404 and shows the backend text for 429`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.h2hListResult = AppResult.Failure(AppError.Forbidden("hidden", "game_hidden"))
            assertEquals(H2hListPhase.Hidden, H2hListViewModel(play, flags).state.value.phase)
            play.h2hListResult = AppResult.Failure(AppError.NotFound("nope"))
            assertEquals(H2hListPhase.Hidden, H2hListViewModel(play, flags).state.value.phase)
            play.h2hListResult = AppResult.Failure(AppError.RateLimited("Slow down", 5))
            assertEquals(H2hListPhase.Error("Slow down"), H2hListViewModel(play, flags).state.value.phase)
        }

    // ---- create ----

    private fun problemFor(error: AppError): H2hCreateUiState {
        play.sessionResult = AppResult.Failure(error)
        val vm = H2hCreateViewModel(play, telemetry)
        vm.onEvent(H2hCreateEvent.Handle("ana"))
        vm.onEvent(H2hCreateEvent.Submit)
        return vm.state.value
    }

    @Test(timeout = 10_000)
    fun `create sends the normalized handle and pack, reports once and hands over the session`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.sessionResult = AppResult.Success(QuizSession(sessionId = "s9", id = "m1", mode = "h2h", pack = "capitals"))
            val vm = H2hCreateViewModel(play, telemetry)
            vm.onEvent(H2hCreateEvent.Handle(" @Ana_9 "))
            vm.onEvent(H2hCreateEvent.Pack(QuizPack.CAPITALS))
            vm.onEvent(H2hCreateEvent.Submit)
            assertEquals(listOf("createH2h(ana_9,capitals)"), play.calls)
            assertEquals("s9", withTimeout(5_000) { vm.sessions.first() })
            assertEquals(listOf("play_h2h_create" to emptyMap<String, Any?>()), telemetry.events)
        }

    @Test(timeout = 10_000)
    fun `an invalid handle is not sent`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            val vm = H2hCreateViewModel(play, telemetry)
            vm.onEvent(H2hCreateEvent.Handle("a!"))
            assertFalse(vm.state.value.valid)
            vm.onEvent(H2hCreateEvent.Submit)
            assertTrue(play.calls.isEmpty())
        }

    @Test(timeout = 10_000)
    fun `create failures map to safe reasons`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            assertEquals(CreateProblem.LIMIT, problemFor(AppError.Client(422, "limit", "limit_reached")).problem)
            // Private, blocked and unknown opponents all look the same.
            assertEquals(CreateProblem.CANNOT_CHALLENGE, problemFor(AppError.NotFound("x", "profile_not_found")).problem)
            assertEquals(CreateProblem.SELF, problemFor(AppError.Client(422, "x", "cannot_challenge_self")).problem)
            assertEquals(CreateProblem.RESTRICTED, problemFor(AppError.Forbidden("x", "restricted")).problem)
            val rate = problemFor(AppError.RateLimited("Too many", 9))
            assertEquals(CreateProblem.OTHER, rate.problem)
            assertEquals("Too many", rate.problemMessage)
            assertNull(problemFor(AppError.Network()).problemMessage)
            assertTrue(problemFor(AppError.Forbidden("hidden", "game_hidden")).hidden)
            assertTrue(telemetry.events.isEmpty())
        }

    // ---- detail ----

    private fun detail(id: String = "a") = H2hDetailViewModel(SavedStateHandle(mapOf("matchId" to id)), play, telemetry, flags)

    @Test(timeout = 10_000)
    fun `detail finds the match and reports open once`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "waiting_you"), match("b", "done"))
            val vm = detail()
            assertEquals(H2hDetailPhase.Loaded, vm.state.value.phase)
            assertEquals("a", vm.state.value.match?.id)
            vm.onEvent(H2hDetailEvent.Resumed)
            vm.onEvent(H2hDetailEvent.Resumed)
            assertEquals(1, telemetry.events.count { it.first == "play_h2h_open" })
        }

    @Test(timeout = 10_000)
    fun `an unknown match is missing`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("b", "done"))
            assertEquals(H2hDetailPhase.Missing, detail("zzz").state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `a finished match reports the winner once`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "done", 400, 250, "you"))
            val vm = detail()
            vm.onEvent(H2hDetailEvent.Resumed)
            vm.onEvent(H2hDetailEvent.Resumed)
            assertEquals(listOf(mapOf<String, Any?>("winner" to "you")), telemetry.events.filter { it.first == "play_h2h_result" }.map { it.second })
        }

    @Test(timeout = 10_000)
    fun `accept hands the session over and decline closes`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "waiting_you"))
            play.sessionResult = AppResult.Success(QuizSession(sessionId = "s2", id = "a", mode = "h2h"))
            val vm = detail()
            vm.onEvent(H2hDetailEvent.Accept)
            assertEquals("s2", withTimeout(5_000) { vm.sessions.first() })
            assertTrue("acceptH2h(a)" in play.calls)
            vm.onEvent(H2hDetailEvent.Decline)
            withTimeout(5_000) { vm.closed.first() }
            assertTrue("declineH2h(a)" in play.calls)
        }

    @Test(timeout = 10_000)
    fun `accept is ignored unless it is your turn`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "waiting_them", you = 300))
            val vm = detail()
            vm.onEvent(H2hDetailEvent.Accept)
            vm.onEvent(H2hDetailEvent.Decline)
            assertTrue(play.calls.none { it.startsWith("acceptH2h") || it.startsWith("declineH2h") })
        }

    @Test(timeout = 10_000)
    fun `an expired accept reloads the match as the server has it`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "waiting_you"))
            play.sessionResult = AppResult.Failure(AppError.Client(410, "gone", "match_expired"))
            val vm = detail()
            api(match("a", "expired"))
            vm.onEvent(H2hDetailEvent.Accept)
            assertEquals("expired", vm.state.value.match?.state)
            assertFalse(vm.state.value.busy)
            assertNull(vm.state.value.actionError)
        }

    @Test(timeout = 10_000)
    fun `another accept failure shows a message and keeps the invite`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            api(match("a", "waiting_you"))
            play.sessionResult = AppResult.Failure(AppError.RateLimited("Too many", 3))
            val vm = detail()
            vm.onEvent(H2hDetailEvent.Accept)
            assertEquals(ActionFailure("Too many"), vm.state.value.actionError)
            assertEquals("waiting_you", vm.state.value.match?.state)
        }
}
