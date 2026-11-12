package io.github.rupakc.travelplanner.core.data.fakes

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.trips.CreateTripRequest
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakeOfflinePackRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import io.github.rupakc.travelplanner.core.testing.fake.NotStubbed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Phase 1/2 fakes keep call logs, take settable results and fail loudly when unstubbed. */
class Phase12FakesTest {
    @Test
    fun `trips fake logs calls and defaults mutating results to not stubbed`() =
        runTest {
            val fake = FakeTripsRepository()
            assertEquals(AppResult.Success(Unit), fake.delete(3))
            assertTrue(fake.create(CreateTripRequest("T")) === NotStubbed)
            assertEquals(listOf("delete:3", "create"), fake.calls)
            assertEquals("T", fake.createRequests.single().title)
        }

    @Test
    fun `game events ignore null and empty deltas`() =
        runTest {
            val events = FakeGameEvents()
            events.publish(null)
            events.publish(GameDelta(xp = 5))
            events.publish(GameDelta(xp = 15, awards = listOf(io.github.rupakc.travelplanner.core.model.game.GameAward("plan_saved", 10))))
            assertEquals(1, events.published.size)
            events.deltas.test { assertEquals(15, awaitItem().xp) }
        }

    @Test
    fun `offline pack fake saves, exposes and purges`() =
        runTest {
            val fake = FakeOfflinePackRepository()
            fake.saved(7).test {
                assertNull(awaitItem())
                fake.save(7, "2026-11-12")
                assertEquals(OfflinePack(tripId = 7), awaitItem())
                fake.purgeAll()
                assertNull(awaitItem())
            }
        }
}
