package io.github.rupakc.travelplanner.feature.play.hub

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.play.Challenge
import io.github.rupakc.travelplanner.core.model.play.DailyState
import io.github.rupakc.travelplanner.core.model.play.HistoryEntry
import io.github.rupakc.travelplanner.core.model.play.PackInfo
import io.github.rupakc.travelplanner.core.model.play.PlayHistory
import io.github.rupakc.travelplanner.core.model.play.PlayState
import io.github.rupakc.travelplanner.core.model.play.PlayStreak
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakePlayRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayHubViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val play = FakePlayRepository()
    private val telemetry = FakeTelemetry()

    private val loaded =
        PlayState(
            daily = DailyState(available = true, streak = PlayStreak(current = 3, longest = 5, freezeAvailable = true)),
            packs = listOf(PackInfo("flags", "pack.flags", 40)),
            challenge = Challenge("2026-W46:JP", "destination", "JP", "t", false),
            openH2h = 2,
        )

    @Test(timeout = 10_000)
    fun `loads state and history and reports play_state_view once`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.stateResult = AppResult.Success(loaded)
            play.historyResult = AppResult.Success(PlayHistory(listOf(HistoryEntry("h1", "daily", null, 320, 4, "2026-11-10T10:00:00Z"))))
            val vm = PlayHubViewModel(play, telemetry)
            val s = vm.state.value
            assertEquals(PlayHubPhase.Loaded, s.phase)
            assertTrue(s.daily.available)
            assertEquals(3, s.daily.streak.current)
            assertEquals(listOf("flags"), s.packs.map { it.id })
            assertEquals("JP", s.challenge?.country)
            assertEquals(2, s.openH2h)
            assertEquals(listOf("h1"), s.history.map { it.sessionId })
            assertEquals(listOf("state", "history(10)"), play.calls)
            vm.onEvent(PlayHubEvent.Resumed)
            vm.onEvent(PlayHubEvent.Resumed)
            assertEquals(1, telemetry.events.count { it.first == "play_state_view" })
            assertEquals(1, telemetry.events.count { it.first == "play_challenge_view" })
            assertEquals(2, play.calls.count { it == "state" })
        }

    @Test(timeout = 10_000)
    fun `a history failure leaves an empty list`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.stateResult = AppResult.Success(loaded)
            play.historyResult = AppResult.Failure(AppError.Network())
            val s = PlayHubViewModel(play, telemetry).state.value
            assertEquals(PlayHubPhase.Loaded, s.phase)
            assertTrue(s.history.isEmpty())
        }

    @Test(timeout = 10_000)
    fun `404 turns the hub off and game_hidden hides the tab`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.stateResult = AppResult.Failure(AppError.NotFound("Not found"))
            assertEquals(PlayHubPhase.Off, PlayHubViewModel(play, telemetry).state.value.phase)
            play.stateResult = AppResult.Failure(AppError.Forbidden("hidden", "game_hidden"))
            assertEquals(PlayHubPhase.GameHidden, PlayHubViewModel(play, telemetry).state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `error shows the backend text and retry recovers`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.stateResult = AppResult.Failure(AppError.RateLimited("Too many requests", 10))
            val vm = PlayHubViewModel(play, telemetry)
            assertEquals(PlayHubPhase.Error("Too many requests"), vm.state.value.phase)
            play.stateResult = AppResult.Success(loaded)
            vm.onEvent(PlayHubEvent.Retry)
            assertEquals(PlayHubPhase.Loaded, vm.state.value.phase)
        }

    @Test(timeout = 10_000)
    fun `a quiet reload failure keeps what is shown`() =
        runTest(timeout = kotlin.time.Duration.parse("10s")) {
            play.stateResult = AppResult.Success(loaded)
            val vm = PlayHubViewModel(play, telemetry)
            vm.onEvent(PlayHubEvent.Resumed)
            play.stateResult = AppResult.Failure(AppError.Network())
            vm.onEvent(PlayHubEvent.Resumed)
            assertEquals(PlayHubPhase.Loaded, vm.state.value.phase)
        }
}
