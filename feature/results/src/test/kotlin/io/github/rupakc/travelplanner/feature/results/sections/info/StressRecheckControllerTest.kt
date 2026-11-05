package io.github.rupakc.travelplanner.feature.results.sections.info

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.search.StressTestPayload
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StressRecheckControllerTest {
    private val payload = StressTestPayload(TestRequest, JsonObject(emptyMap()))

    @Test
    fun `start runs once, publishes the result and keeps the plan fingerprint`() =
        runTest {
            val gate = CompletableDeferred<AppResult<StressTestData>>()
            var calls = 0
            val controller =
                StressRecheckController(this) {
                    calls++
                    gate.await()
                }
            controller.state.test {
                assertEquals(RecheckState(), awaitItem())
                controller.start(payload, "fp", "base")
                assertTrue(awaitItem().running)
                controller.start(payload, "fp", "base") // ignored while running
                gate.complete(AppResult.Success(StressTestData(overall = "green", score = 90)))
                val done = awaitItem()
                assertFalse(done.running)
                assertEquals(RecheckResult(StressTestData(overall = "green", score = 90), "fp"), done.result)
                assertEquals("base", done.base)
            }
            assertEquals(1, calls)
        }

    @Test
    fun `failures and error results become failures, clear cancels and forgets`() =
        runTest {
            val controller = StressRecheckController(this) { AppResult.Failure(AppError.RateLimited("Slow down", 30)) }
            controller.start(payload, "fp", null)
            testScheduler.advanceUntilIdle()
            assertEquals(RecheckFailure("Slow down", "fp"), controller.state.value.failure)

            val errored = StressRecheckController(this) { AppResult.Success(StressTestData(error = "timed out")) }
            errored.start(payload, "fp", null)
            testScheduler.advanceUntilIdle()
            assertNull(errored.state.value.failure?.message)
            assertNull(errored.state.value.result)

            val never = CompletableDeferred<AppResult<StressTestData>>()
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val pending = StressRecheckController(scope) { never.await() }
            pending.start(payload, "fp", null)
            scope.testScheduler.advanceUntilIdle()
            assertTrue(pending.state.value.running)
            pending.clear()
            assertEquals(RecheckState(), pending.state.value)
        }

    @Test
    fun `restore brings back saved results but never a running flag`() =
        runTest {
            val controller = StressRecheckController(this) { AppResult.Failure(AppError.Network()) }
            val saved = RecheckState(result = RecheckResult(StressTestData(score = 1), "fp"), running = true)
            controller.restore(RecheckState.decode(saved.encode()))
            assertFalse(controller.state.value.running)
            assertEquals(saved.result, controller.state.value.result)
        }

    @Test
    fun `sections that render their own error copy`() {
        assertEquals(
            setOf(ResultsSection.WalkingTours, ResultsSection.Events, ResultsSection.StressTest, ResultsSection.Forex),
            ResultsSection.entries.filter { it.rendersOwnError }.toSet(),
        )
    }
}
