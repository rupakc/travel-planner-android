package io.github.rupakc.travelplanner.feature.play.board

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.play.BoardRow
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.core.model.play.BoardYou
import io.github.rupakc.travelplanner.core.model.play.Leaderboard
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakePlayRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BoardViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val play = FakePlayRepository()
    private val telemetry = FakeTelemetry()

    private val board =
        Leaderboard("2026-W46", listOf(BoardRow(1, "ana", 900), BoardRow(2, "bo", 800, you = true)), BoardYou(2, 800, optedIn = true))

    @Test(timeout = 10_000)
    fun `loads global this week and reports the scope`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.leaderboardResult = AppResult.Success(board)
            val vm = BoardViewModel(play, telemetry)
            val s = vm.state.value
            assertEquals(BoardPhase.Loaded, s.phase)
            assertEquals("2026-W46", s.weekLabel)
            assertEquals(2, s.rows.size)
            assertEquals(listOf("leaderboard(global,current)"), play.calls)
            assertEquals(listOf("play_board_view" to mapOf<String, Any?>("scope" to "global")), telemetry.events)
        }

    @Test(timeout = 10_000)
    fun `switching scope and week reloads and reports each scope view`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.leaderboardResult = AppResult.Success(board)
            val vm = BoardViewModel(play, telemetry)
            vm.onEvent(BoardEvent.Scope(BoardScope.FRIENDS))
            vm.onEvent(BoardEvent.Week(BoardWeek.PREVIOUS))
            vm.onEvent(BoardEvent.Week(BoardWeek.PREVIOUS))
            assertEquals(listOf("leaderboard(global,current)", "leaderboard(friends,current)", "leaderboard(friends,previous)"), play.calls)
            assertEquals(listOf("global", "friends", "friends"), telemetry.events.map { it.second["scope"] })
        }

    @Test(timeout = 10_000)
    fun `the opt-in prompt shows only on the global board for a player with a place who has not opted in`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.leaderboardResult = AppResult.Success(board.copy(you = BoardYou(null, 0, optedIn = false)))
            val vm = BoardViewModel(play, telemetry)
            assertTrue(vm.state.value.showOptInPrompt)
            vm.onEvent(BoardEvent.Scope(BoardScope.FRIENDS))
            assertFalse(vm.state.value.showOptInPrompt)
            play.leaderboardResult = AppResult.Success(board.copy(you = null))
            vm.onEvent(BoardEvent.Scope(BoardScope.GLOBAL))
            assertFalse(vm.state.value.showOptInPrompt)
        }

    @Test(timeout = 10_000)
    fun `failures hide for game_hidden and show the backend text otherwise, retry recovers`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.leaderboardResult = AppResult.Failure(AppError.Forbidden("hidden", "game_hidden"))
            assertEquals(BoardPhase.Hidden, BoardViewModel(play, telemetry).state.value.phase)
            play.leaderboardResult = AppResult.Failure(AppError.RateLimited("Slow down", 4))
            val vm = BoardViewModel(play, telemetry)
            assertEquals(BoardPhase.Error("Slow down"), vm.state.value.phase)
            assertTrue(telemetry.events.isEmpty())
            play.leaderboardResult = AppResult.Success(board)
            vm.onEvent(BoardEvent.Retry)
            assertEquals(BoardPhase.Loaded, vm.state.value.phase)
        }
}
