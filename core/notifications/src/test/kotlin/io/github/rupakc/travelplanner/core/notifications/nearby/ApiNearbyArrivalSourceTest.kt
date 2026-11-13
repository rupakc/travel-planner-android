package io.github.rupakc.travelplanner.core.notifications.nearby

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.nearby.LocationCell
import io.github.rupakc.travelplanner.core.model.nearby.NearbyItem
import io.github.rupakc.travelplanner.core.model.nearby.NearbyItemsResponse
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNearbyRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiNearbyArrivalSourceTest {
    private val cell = LocationCell(38.725, -9.125, 0L)
    private val signedIn = FakeAuthRepository(Session("t", TestUser))

    private fun source(repo: FakeNearbyRepository, auth: FakeAuthRepository = signedIn) =
        ApiNearbyArrivalSource(repo, auth).apply { timeoutMs = 200 }

    private fun answer(vararg items: NearbyItem) = AppResult.Success(NearbyItemsResponse(NearbyPlace(name = "Lisbon"), items.toList()))

    private fun run(block: suspend () -> NearbyArrival?) = runBlocking { withTimeout(5_000) { block() } }

    @Test
    fun `the top pick and its category come from one v1 call, never with minutes`() {
        val repo = FakeNearbyRepository().apply { itemsResult = answer(NearbyItem(name = " Time Out Market ", category = "food", location = "Cais do Sodre")) }
        val arrival = run { source(repo).arrival(cell) }
        assertEquals(NearbyArrival("Lisbon", "Time Out Market", minutes = null, reason = "food"), arrival)
        assertEquals(listOf(Triple(38.725, -9.125, 1)), repo.itemCalls)
        assertEquals(emptyList<FakeNearbyRepository.NearbyCall>(), repo.requests)
    }

    @Test
    fun `an item without a name is skipped and a blank category gives no reason`() {
        val repo = FakeNearbyRepository().apply { itemsResult = answer(NearbyItem(name = " "), NearbyItem(name = "Alfama", category = " ")) }
        assertEquals(NearbyArrival("Lisbon", "Alfama", null, null), run { source(repo).arrival(cell) })
    }

    @Test
    fun `an error answer falls back to the generic text`() {
        val errors =
            listOf(
                AppError.RateLimited("slow down", 30),
                AppError.Client(503, "AI limit reached", "ai_limit"),
                AppError.Client(502, "agent", "agent_error"),
                AppError.NotFound("no place", "no_place"),
                AppError.Unauthorized(),
                AppError.Network(),
            )
        for (e in errors) {
            val repo = FakeNearbyRepository().apply { itemsResult = AppResult.Failure(e) }
            assertNull(run { source(repo).arrival(cell) })
            assertEquals(1, repo.itemCalls.size)
        }
    }

    @Test
    fun `an empty answer falls back`() {
        val repo = FakeNearbyRepository().apply { itemsResult = answer() }
        assertNull(run { source(repo).arrival(cell) })
    }

    @Test
    fun `a slow answer times out and falls back`() {
        val repo =
            FakeNearbyRepository().apply {
                itemsResult = answer(NearbyItem(name = "Alfama"))
                itemsDelayMs = 2_000
            }
        assertNull(run { source(repo).arrival(cell) })
    }

    @Test
    fun `a signed-out user makes no call at all`() {
        val repo = FakeNearbyRepository().apply { itemsResult = answer(NearbyItem(name = "Alfama")) }
        assertNull(run { source(repo, FakeAuthRepository(null)).arrival(cell) })
        assertEquals(0, repo.itemCalls.size)
    }
}
