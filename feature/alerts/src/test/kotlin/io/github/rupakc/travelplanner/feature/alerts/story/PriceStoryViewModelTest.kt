package io.github.rupakc.travelplanner.feature.alerts.story

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.alerts.AdviceTip
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.PricePoint
import io.github.rupakc.travelplanner.core.model.alerts.PriceStory
import io.github.rupakc.travelplanner.core.model.alerts.StoryAdvice
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAlertsV2Repository
import io.github.rupakc.travelplanner.core.testing.fake.FakeJourneyFlagsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PriceStoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeAlertsV2Repository()
    private val telemetry = FakeTelemetry()

    private fun story(kind: String = "flight", code: String = "within_typical", verdict: String? = "typical") =
        PriceStory(
            kind = kind,
            watchId = 7,
            points = listOf(PricePoint("2026-11-01T10:00:00Z", 450.0), PricePoint("2026-11-02T10:00:00Z", 430.0)),
            verdict = verdict,
            advice = StoryAdvice(code),
            checkedDaysAgo = 1,
        )

    @Test(timeout = 10_000)
    fun `load shows the story and reports the view with kind and verdict only`() =
        runTest {
            repo.storyResult = AppResult.Success(story())
            val vm = PriceStoryViewModel(repo, telemetry)
            vm.load(AlertKind.FLIGHT, 7)
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertNotNull(vm.state.value.story)
            assertEquals(listOf(AlertKind.FLIGHT to 7L), repo.storyCalls)
            assertEquals(listOf("alert_story_view" to mapOf<String, Any?>("kind" to "flight", "verdict" to "typical")), telemetry.events)
        }

    @Test(timeout = 10_000)
    fun `loading the same watch again does not refetch, a failure retries`() =
        runTest {
            repo.storyResult = AppResult.Failure(AppError.NotFound("gone", code = "watch_not_found"))
            val vm = PriceStoryViewModel(repo, telemetry)
            vm.load(AlertKind.HOTEL, 3)
            advanceUntilIdle()
            assertEquals(TipFailure.NOT_FOUND, vm.state.value.error?.failure)
            repo.storyResult = AppResult.Success(story("hotel", verdict = null))
            vm.load(AlertKind.HOTEL, 3)
            advanceUntilIdle()
            assertEquals(2, repo.storyCalls.size)
            assertEquals(null, vm.state.value.error)
            vm.load(AlertKind.HOTEL, 3)
            advanceUntilIdle()
            assertEquals(2, repo.storyCalls.size)
            assertEquals("none", telemetry.events.single().second["verdict"])
        }

    @Test(timeout = 10_000)
    fun `retry reloads after an error`() =
        runTest {
            repo.storyResult = AppResult.Failure(AppError.Network())
            val vm = PriceStoryViewModel(repo, telemetry)
            vm.load(AlertKind.FLIGHT, 1)
            advanceUntilIdle()
            assertTrue(vm.state.value.error != null)
            repo.storyResult = AppResult.Success(story())
            vm.retry()
            advanceUntilIdle()
            assertEquals(null, vm.state.value.error)
        }

    @Test(timeout = 10_000)
    fun `the tip is offered for flights with a price only`() =
        runTest {
            val vm = PriceStoryViewModel(repo, telemetry)
            repo.storyResult = AppResult.Success(story("hotel"))
            vm.load(AlertKind.HOTEL, 1)
            advanceUntilIdle()
            assertFalse(vm.state.value.tipAvailable)
            vm.requestTip()
            advanceUntilIdle()
            assertTrue(repo.adviceCalls.isEmpty())

            val pending = PriceStoryViewModel(repo, telemetry)
            repo.storyResult = AppResult.Success(story(code = "pending"))
            pending.load(AlertKind.FLIGHT, 2)
            advanceUntilIdle()
            assertFalse(pending.state.value.tipAvailable)
        }

    @Test(timeout = 10_000)
    fun `a tip is shown and reported as ok or cached`() =
        runTest {
            repo.storyResult = AppResult.Success(story())
            val vm = PriceStoryViewModel(repo, telemetry)
            vm.load(AlertKind.FLIGHT, 7)
            advanceUntilIdle()
            repo.adviceResult = AppResult.Success(AdviceTip(tip = " Book soon. ", source = "ai", cached = false))
            vm.requestTip()
            advanceUntilIdle()
            assertEquals(TipState.Ready("Book soon.", false), vm.state.value.tip)
            repo.adviceResult = AppResult.Success(AdviceTip(tip = "Book soon.", cached = true))
            vm.requestTip()
            advanceUntilIdle()
            assertEquals(listOf("ok", "cached"), telemetry.events.filter { it.first == "alert_advice" }.map { it.second["outcome"] })
        }

    @Test(timeout = 10_000)
    fun `tip errors map the server codes and report error`() =
        runTest {
            repo.storyResult = AppResult.Success(story())
            val vm = PriceStoryViewModel(repo, telemetry)
            vm.load(AlertKind.FLIGHT, 7)
            advanceUntilIdle()
            val cases =
                listOf(
                    AppError.Server(503, "limit", code = "ai_limit") to TipFailure.LIMIT,
                    AppError.Server(503, "billing", code = "ai_billing") to TipFailure.BILLING,
                    AppError.Server(502, "failed", code = "advice_failed") to TipFailure.FAILED,
                    AppError.Client(422, "none", code = "advice_no_data") to TipFailure.NO_DATA,
                )
            for ((error, failure) in cases) {
                repo.adviceResult = AppResult.Failure(error)
                vm.requestTip()
                advanceUntilIdle()
                val tip = vm.state.value.tip as TipState.Failed
                assertEquals(failure, tip.error.failure)
            }
            assertTrue(telemetry.events.filter { it.first == "alert_advice" }.all { it.second["outcome"] == "error" })
        }

    @Test(timeout = 10_000)
    fun `an empty tip counts as a failed one`() =
        runTest {
            repo.storyResult = AppResult.Success(story())
            val vm = PriceStoryViewModel(repo, telemetry)
            vm.load(AlertKind.FLIGHT, 7)
            advanceUntilIdle()
            repo.adviceResult = AppResult.Success(AdviceTip(tip = "  "))
            vm.requestTip()
            advanceUntilIdle()
            assertTrue(vm.state.value.tip is TipState.Failed)
        }

    @Test(timeout = 10_000)
    fun `the gate follows the v2 flags`() =
        runTest {
            val on =
                FakeJourneyFlagsRepository(
                    io.github.rupakc.travelplanner.core.domain.server.JourneyFlags(
                        alerts = io.github.rupakc.travelplanner.core.domain.alerts.AlertsFlags(alertsApi = 2),
                    ),
                )
            val gate = StoryGateViewModel(on)
            advanceUntilIdle()
            assertTrue(gate.available.value)
            val off = StoryGateViewModel(FakeJourneyFlagsRepository())
            advanceUntilIdle()
            assertFalse(off.available.value)
        }
}
