package io.github.rupakc.travelplanner.core.data.carbon

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonCardState
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.model.carbon.CarbonFootprint
import io.github.rupakc.travelplanner.core.testing.fake.FakeCarbonRepository
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The card state machine: feature gate, debounce, diet, 404 and retry (web `CarbonFootprint`). */
class CarbonCardsTest {
    private val body = JsonObject(mapOf("search" to JsonObject(emptyMap()), "results" to JsonObject(emptyMap())))
    private val ok = CarbonFootprint(totalKg = 12.0)

    @Test
    fun `off never asks the server`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = false)
            repo.cardStates(MutableStateFlow(body)).test {
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                advanceTimeBy(5 * 60_000)
                expectNoEvents()
            }
            assertTrue(repo.requests.isEmpty())
        }

    @Test
    fun `a failed config read is retried and the card appears once the flag reads on`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = false, result = AppResult.Success(ok))
            repo.cardStates(MutableStateFlow(body), availabilityRetryMillis = listOf(1_000L, 2_000L)).test {
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                advanceTimeBy(1_500)
                expectNoEvents()
                repo.enabled = true
                assertEquals(CarbonCardState.Loading(CarbonDiet.MIXED), awaitItem())
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.MIXED), awaitItem())
            }
            assertEquals(1, repo.requests.size)
        }

    @Test
    fun `a new body reads the flag again after the retries ran out`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = false, result = AppResult.Success(ok))
            val bodies = MutableStateFlow<JsonObject?>(body)
            repo.cardStates(bodies, availabilityRetryMillis = listOf(1_000L)).test {
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                advanceTimeBy(5_000)
                expectNoEvents()
                repo.enabled = true
                bodies.value = JsonObject(body + ("x" to JsonPrimitive(1)))
                assertEquals(CarbonCardState.Loading(CarbonDiet.MIXED), awaitItem())
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.MIXED), awaitItem())
            }
        }

    @Test
    fun `loading then ready with the remembered diet in the body`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = true, result = AppResult.Success(ok))
            repo.setDiet(CarbonDiet.VEGAN)
            repo.cardStates(MutableStateFlow(body)).test {
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                assertEquals(CarbonCardState.Loading(CarbonDiet.VEGAN), awaitItem())
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.VEGAN), awaitItem())
            }
            assertEquals(JsonPrimitive("vegan"), repo.requests.single()["diet"])
        }

    @Test
    fun `a diet change keeps the numbers while refreshing`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = true, result = AppResult.Success(ok))
            repo.cardStates(MutableStateFlow(body)).test {
                skipItems(2)
                assertTrue(awaitItem() is CarbonCardState.Ready)
                repo.setDiet(CarbonDiet.MEAT_HEAVY)
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.MEAT_HEAVY, refreshing = true), awaitItem())
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.MEAT_HEAVY), awaitItem())
            }
            assertEquals(listOf("mixed", "meat_heavy"), repo.requests.map { (it["diet"] as JsonPrimitive).content })
        }

    @Test
    fun `quick changes send only the last body`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = true, result = AppResult.Success(ok))
            val bodies = MutableStateFlow<JsonObject?>(body)
            repo.cardStates(bodies).test {
                skipItems(2)
                bodies.value = JsonObject(body + ("x" to JsonPrimitive(1)))
                bodies.value = JsonObject(body + ("x" to JsonPrimitive(2)))
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.MIXED), awaitItem())
            }
            assertEquals(listOf(JsonPrimitive(2)), repo.requests.map { it["x"] })
        }

    @Test
    fun `a 404 hides the card for good`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = true, result = AppResult.Failure(AppError.NotFound()))
            val bodies = MutableStateFlow<JsonObject?>(body)
            repo.cardStates(bodies).test {
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                assertEquals(CarbonCardState.Loading(CarbonDiet.MIXED), awaitItem())
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                repo.result = AppResult.Success(ok)
                bodies.value = JsonObject(body + ("x" to JsonPrimitive(1)))
                advanceTimeBy(1_000)
                expectNoEvents()
            }
            assertEquals(1, repo.requests.size)
        }

    @Test
    fun `another failure shows the error until a retry succeeds`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = true, result = AppResult.Failure(AppError.Server(500, null)))
            val retries = MutableStateFlow(0)
            repo.cardStates(MutableStateFlow(body), retries).test {
                skipItems(2)
                assertEquals(CarbonCardState.Failed(CarbonDiet.MIXED), awaitItem())
                repo.result = AppResult.Success(ok)
                retries.value = 1
                assertEquals(CarbonCardState.Loading(CarbonDiet.MIXED), awaitItem())
                assertEquals(CarbonCardState.Ready(ok, CarbonDiet.MIXED), awaitItem())
            }
        }

    @Test
    fun `no body hides the card without a request`() =
        runTest(timeout = 10.seconds) {
            val repo = FakeCarbonRepository(enabled = true)
            repo.cardStates(MutableStateFlow<JsonObject?>(null)).test {
                assertEquals(CarbonCardState.Hidden(), awaitItem())
                advanceTimeBy(1_000)
                expectNoEvents()
            }
            assertTrue(repo.requests.isEmpty())
        }
}
