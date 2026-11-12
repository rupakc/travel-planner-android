package io.github.rupakc.travelplanner.feature.play

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.game.BadgeDef
import io.github.rupakc.travelplanner.core.model.game.BadgeProgress
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameMe
import io.github.rupakc.travelplanner.core.model.game.RecentAward
import io.github.rupakc.travelplanner.core.model.game.StreakDto
import io.github.rupakc.travelplanner.core.model.trips.CountryStat
import io.github.rupakc.travelplanner.core.model.trips.Stats
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import java.util.Optional
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val game = FakeGameRepository()
    private val trips = FakeTripsRepository()
    private val telemetry = FakeTelemetry()

    private fun vm(withTrips: Boolean = true) = PlayViewModel(game, if (withTrips) Optional.of(trips) else Optional.empty(), telemetry)

    private val me =
        GameMe(
            xp = 150,
            level = 2,
            levelMinXp = 100,
            nextLevelXp = 250,
            badges = listOf(BadgeProgress("first_trip", 1, 1, "2026-05-01T10:00:00Z"), BadgeProgress("mystery_badge", 2, 5)),
            streaks = mapOf("daily" to StreakDto(3, 7, "2026-05-02"), "trip_checkin" to StreakDto(1, 1), "other" to StreakDto(9, 9)),
            recent = listOf(RecentAward("plan_saved", 10, "2026-05-01T10:00:00Z")),
        )

    @Test fun `loaded state maps level, badges, streaks and recent`() =
        runTest {
            game.meResult = AppResult.Success(me)
            game.catalogResult = AppResult.Success(GameCatalog(badges = listOf(BadgeDef("first_trip", tier = "silver"), BadgeDef("mystery_badge"))))
            val s = vm().loaded()
            assertEquals(2, s.level)
            assertEquals(50, s.xpIntoLevel)
            assertEquals(150, s.xpForLevel)
            assertEquals(1f / 3f, s.progress, 0.001f)
            assertEquals(100, s.remainingXp)
            assertEquals(listOf("first_trip", "mystery_badge"), s.badges.map { it.id })
            assertTrue(s.badges[0].earned)
            assertEquals(BadgeTier.SILVER, s.badges[0].tier)
            assertFalse(s.badges[1].earned)
            assertEquals(listOf("daily", "trip_checkin"), s.streaks.map { it.id })
            assertEquals(listOf("plan_saved"), s.recent.map { it.rule })
        }

    @Test fun `top level has no next xp and a full bar`() =
        runTest {
            game.meResult = AppResult.Success(me.copy(level = 10, nextLevelXp = null))
            val s = vm().loaded()
            assertNull(s.remainingXp)
            assertEquals(1f, s.progress, 0f)
        }

    @Test fun `404 hides Play`() =
        runTest {
            game.meResult = AppResult.Failure(AppError.NotFound())
            assertEquals(PlayPhase.Hidden, vm().settled().phase)
            assertTrue(telemetry.events.isEmpty())
        }

    @Test fun `hidden flag hides Play`() =
        runTest {
            game.meResult = AppResult.Success(me.copy(hidden = true))
            assertEquals(PlayPhase.Hidden, vm().settled().phase)
            assertTrue(telemetry.events.isEmpty())
        }

    @Test fun `network failure shows an error and retry loads`() =
        runTest {
            game.meResult = AppResult.Failure(AppError.Network())
            val model = vm()
            model.state.test {
                assertEquals(PlayPhase.Error(null), awaitPhase())
                game.meResult = AppResult.Success(me)
                model.onEvent(PlayEvent.Retry)
                awaitLoaded()
            }
        }

    @Test fun `backend detail is shown as is`() =
        runTest {
            game.meResult = AppResult.Failure(AppError.RateLimited("Too many requests", 30))
            assertEquals(PlayPhase.Error("Too many requests"), vm().settled().phase)
        }

    @Test fun `trips section shows countries and goal`() =
        runTest {
            game.meResult = AppResult.Success(me)
            trips.statsResult = AppResult.Success(Stats(countries = listOf(CountryStat("PT", "Portugal"), CountryStat("JP")), bucketGoal = 10))
            val s = vm().loaded()
            assertEquals(true, s.tripsVisible)
            assertEquals(listOf("Portugal", "JP"), s.countries.map { it.name })
            assertEquals(10, s.bucketGoal)
        }

    @Test fun `trips section hides when stats fail or trips are not bound`() =
        runTest {
            game.meResult = AppResult.Success(me)
            trips.statsResult = AppResult.Failure(AppError.NotFound())
            assertFalse(vm().loaded().tripsVisible)
            assertFalse(vm(withTrips = false).loaded().tripsVisible)
        }

    @Test fun `unknown streak and a missing catalog still render`() =
        runTest {
            game.meResult = AppResult.Success(me)
            game.catalogResult = AppResult.Failure(AppError.Network())
            val s = vm().loaded()
            assertEquals(null, s.badges[0].tier)
        }

    @Test fun `game_view nav is emitted once per successful load`() =
        runTest {
            game.meResult = AppResult.Success(me)
            vm().loaded()
            assertEquals(listOf("game_view" to mapOf<String, Any?>("surface" to "nav")), telemetry.events)
        }
}

private suspend fun app.cash.turbine.ReceiveTurbine<PlayUiState>.awaitPhase(): PlayPhase {
    while (true) {
        val s = awaitItem()
        if (s.phase != PlayPhase.Loading) return s.phase
    }
}

private suspend fun app.cash.turbine.ReceiveTurbine<PlayUiState>.awaitLoaded(): PlayUiState {
    while (true) {
        val s = awaitItem()
        if (s.phase == PlayPhase.Loaded) return s
    }
}

private suspend fun PlayViewModel.settled(): PlayUiState = state.first { it.phase != PlayPhase.Loading }

private suspend fun PlayViewModel.loaded(): PlayUiState = settled().also { assertEquals(PlayPhase.Loaded, it.phase) }
