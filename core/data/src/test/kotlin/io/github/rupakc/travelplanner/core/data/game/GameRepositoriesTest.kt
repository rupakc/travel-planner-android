package io.github.rupakc.travelplanner.core.data.game

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.impl.GameEventsImpl
import io.github.rupakc.travelplanner.core.data.impl.GameRepositoryImpl
import io.github.rupakc.travelplanner.core.model.game.EarnedBadge
import io.github.rupakc.travelplanner.core.model.game.GameAward
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.game.GameMe
import io.github.rupakc.travelplanner.core.model.game.LevelUp
import io.github.rupakc.travelplanner.core.network.api.GameApi
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class GameRepositoriesTest {
    private class Api : GameApi {
        var me: GameMe? = GameMe()
        var catalogCalls = 0

        override suspend fun me(): GameMe = me ?: throw HttpException(Response.error<GameMe>(404, "{}".toResponseBody()))

        override suspend fun catalog(): GameCatalog = GameCatalog(version = ++catalogCalls)
    }

    private val delta =
        GameDelta(
            xp = 60,
            awards = listOf(GameAward("plan_saved", 10)),
            badges = listOf(EarnedBadge("first_trip")),
            levelUp = LevelUp(1, 2),
        )

    @Test fun `catalog is cached for five minutes`() =
        runTest {
            var t = 0L
            val api = Api()
            val repo = GameRepositoryImpl(api, GameEventsImpl(FakeTelemetry()), { t })
            repo.catalog()
            t = 299_000
            repo.catalog()
            assertEquals(1, api.catalogCalls)
            t = 301_000
            repo.catalog()
            assertEquals(2, api.catalogCalls)
        }

    @Test fun `404 on me is NotFound and silences the bus`() =
        runTest {
            val events = GameEventsImpl(FakeTelemetry())
            val api = Api().apply { me = null }
            val result = GameRepositoryImpl(api, events, { 0L }).me()
            assertTrue((result as AppResult.Failure).error is AppError.NotFound)
            val seen = mutableListOf<GameDelta>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { events.deltas.toList(seen) }
            events.publish(delta)
            assertTrue(seen.isEmpty())
            api.me = GameMe()
            GameRepositoryImpl(api, events, { 0L }).me()
            events.publish(delta)
            assertEquals(listOf(delta), seen)
        }

    @Test fun `hidden me silences telemetry and a visible me restores it`() =
        runTest {
            val tel = FakeTelemetry()
            val events = GameEventsImpl(tel)
            val api = Api().apply { me = GameMe(hidden = true) }
            val repo = GameRepositoryImpl(api, events, { 0L })
            repo.me()
            events.publish(delta)
            assertTrue(tel.events.isEmpty())
            api.me = GameMe(hidden = false)
            repo.me()
            events.publish(delta)
            assertEquals(listOf("game_award", "badge_earned", "level_up", "game_view"), tel.events.map { it.first })
            assertEquals(mapOf("level" to 2), tel.events[2].second)
        }

    @Test fun `null and empty deltas are ignored`() {
        val tel = FakeTelemetry()
        val events = GameEventsImpl(tel)
        events.publish(null)
        events.publish(GameDelta(xp = 5))
        assertTrue(tel.events.isEmpty())
    }
}
