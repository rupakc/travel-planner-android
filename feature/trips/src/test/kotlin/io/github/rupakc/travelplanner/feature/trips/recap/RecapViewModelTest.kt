package io.github.rupakc.travelplanner.feature.trips.recap

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCompleteResponse
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeRecapRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecapViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val recaps = FakeRecapRepository()
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2027-04-10T12:00:00Z")

            override fun zone() = ZoneOffset.UTC
        }

    private val recap = Recap(tripId = 12, title = "Lisbon", endDate = "2027-04-08", distanceKm = null, canComplete = true)

    private fun vm(r: Recap = recap): RecapViewModel {
        recaps.recapResult = AppResult.Success(r)
        val plans = FakePlansRepository(listOf(Plan(id = 12, name = "Lisbon", selections = PlanSelections())))
        return RecapViewModel(SavedStateHandle(mapOf("tripId" to 12L)), recaps, plans, telemetry, clock)
    }

    @Test
    fun `loads the recap with a null distance and sends the device date`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            val s = vm.state.value
            assertFalse(s.loading)
            assertNull(s.recap!!.distanceKm)
            assertEquals(listOf("recap:12:2027-04-10"), recaps.calls)
            assertEquals(listOf("view"), telemetry.events.map { it.second["action"] })
        }

    @Test
    fun `a failed load shows the error and retry loads`() =
        runTest(timeout = 30.seconds) {
            recaps.recapResult = AppResult.Failure(AppError.Network("x"))
            val vm = RecapViewModel(SavedStateHandle(mapOf("tripId" to 12L)), recaps, FakePlansRepository(emptyList()), telemetry, clock)
            assertTrue(vm.state.value.failed)
            recaps.recapResult = AppResult.Success(recap)
            vm.onEvent(RecapEvent.Retry)
            assertFalse(vm.state.value.failed)
            assertNotNull(vm.state.value.recap)
        }

    @Test
    fun `repeat completion keeps the first completed_at`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            recaps.completeResult = AppResult.Success(RecapCompleteResponse("2027-04-10T12:00:00Z", listOf("PT")))
            vm.onEvent(RecapEvent.Complete)
            assertEquals("2027-04-10T12:00:00Z", vm.state.value.recap!!.completedAt)
            assertEquals(setOf("PT"), vm.state.value.newCountries)
            // A second tap sends nothing; even a later server answer would not move the first time.
            recaps.completeResult = AppResult.Success(RecapCompleteResponse("2027-04-11T08:00:00Z"))
            vm.onEvent(RecapEvent.Complete)
            assertEquals("2027-04-10T12:00:00Z", vm.state.value.recap!!.completedAt)
            assertEquals(1, recaps.calls.count { it.startsWith("complete") })
        }

    @Test
    fun `recap_not_ready shows our message`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            recaps.completeResult = AppResult.Failure(AppError.Client(status = 409, message = "server", code = "recap_not_ready"))
            vm.onEvent(RecapEvent.Complete)
            assertTrue(vm.state.value.completeFailure!!.notReady)
            assertNull(vm.state.value.recap!!.completedAt)
            vm.onEvent(RecapEvent.DismissFailure)
            assertNull(vm.state.value.completeFailure)
        }

    @Test
    fun `other failures show the backend text`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            recaps.completeResult = AppResult.Failure(AppError.Client(status = 422, message = "Backend says no", code = "x"))
            vm.onEvent(RecapEvent.Complete)
            assertFalse(vm.state.value.completeFailure!!.notReady)
            assertEquals("Backend says no", vm.state.value.completeFailure!!.message)
        }

    @Test
    fun `telemetry carries only the action enum`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            vm.onEvent(RecapEvent.Shared)
            vm.onEvent(RecapEvent.WhereNext)
            assertEquals(listOf("view", "share", "where_next"), telemetry.events.map { it.second["action"] })
            assertTrue(telemetry.events.all { it.second.keys == setOf("action") })
        }
}
