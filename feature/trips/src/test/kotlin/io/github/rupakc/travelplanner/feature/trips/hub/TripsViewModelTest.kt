package io.github.rupakc.travelplanner.feature.trips.hub

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TripsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeTripsRepository()
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2026-11-12T23:30:00Z")

            override fun zone() = ZoneOffset.ofHours(5)
        }

    private fun vm() = TripsViewModel(repo, telemetry, clock)

    private fun trip(id: Long, phase: Phase, title: String = "T$id") = Trip(id = id, title = title, phase = phase)

    @Test
    fun `loads with the device local date and groups by phase with the pinned trip first`() {
        repo.listResult = AppResult.Success(TripsResponse(listOf(trip(1, Phase.READY), trip(2, Phase.PLANNING), trip(3, Phase.PLANNING)), pinnedId = 1))
        val vm = vm()
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(1L, s.pinned?.id)
        assertEquals(listOf(Phase.PLANNING), s.groups.map { it.phase })
        assertTrue(repo.calls.any { it.contains("2026-11-13") })
        assertEquals(listOf("trip_view" to mapOf<String, Any?>("surface" to "hub", "phase" to "ready")), telemetry.events)
    }

    @Test
    fun `empty list shows the empty state`() {
        val s = vm().state.value
        assertFalse(s.loading)
        assertTrue(s.isEmpty)
        assertEquals("none", telemetry.events.single().second["phase"])
    }

    @Test
    fun `a failed load offers retry and a 429 keeps the backend message`() {
        repo.listResult = AppResult.Failure(AppError.RateLimited("Slow down", 30))
        val vm = vm()
        assertEquals("Slow down", vm.state.value.error?.message)
        repo.listResult = AppResult.Success(TripsResponse(listOf(trip(1, Phase.DREAMING))))
        vm.onEvent(TripsEvent.Refresh)
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.groups.size)
    }

    @Test
    fun `a cached list is flagged offline`() {
        repo.showingCached = true
        assertTrue(vm().state.value.offline)
    }

    @Test
    fun `create validates first, then sends and opens the new trip`() =
        kotlinx.coroutines.test.runTest {
            val vm = vm()
            vm.onEvent(TripsEvent.OpenCreate)
            vm.onEvent(TripsEvent.SubmitCreate)
            assertEquals(NewTripError.Title, vm.state.value.sheet?.error)
            assertTrue(repo.createRequests.isEmpty())

            repo.createResult = AppResult.Success(trip(9, Phase.DREAMING, "Idea"))
            vm.onEvent(TripsEvent.SetTitle("Idea"))
            vm.onEvent(TripsEvent.SetDestination("Lisbon"))
            vm.effects.test {
                vm.onEvent(TripsEvent.SubmitCreate)
                assertEquals(TripsEffect.OpenTrip(9), awaitItem())
            }
            assertEquals("Idea", repo.createRequests.single().title)
            assertEquals("Lisbon", repo.createRequests.single().destination)
            assertNull(repo.createRequests.single().startDate)
            assertNull(vm.state.value.sheet)
            assertTrue(telemetry.events.any { it.first == "trip_action" && it.second == mapOf<String, Any?>("action" to "create") })
        }

    @Test
    fun `server field errors land on the form and nothing is lost`() {
        repo.createResult = AppResult.Failure(AppError.Client(422, "Bad dates", code = "trip_dates_invalid"))
        val vm = vm()
        vm.onEvent(TripsEvent.OpenCreate)
        vm.onEvent(TripsEvent.SetTitle("Idea"))
        vm.onEvent(TripsEvent.SetStart("2027-04-01"))
        vm.onEvent(TripsEvent.SubmitCreate)
        val sheet = checkNotNull(vm.state.value.sheet)
        assertEquals(NewTripError.Dates, sheet.error)
        assertEquals("Idea", sheet.title)
        assertFalse(sheet.saving)
    }

    @Test
    fun `other create failures show the backend text or our own`() {
        repo.createResult = AppResult.Failure(AppError.Client(500, null))
        val vm = vm()
        vm.onEvent(TripsEvent.OpenCreate)
        vm.onEvent(TripsEvent.SetTitle("Idea"))
        vm.onEvent(TripsEvent.SubmitCreate)
        val sheet = checkNotNull(vm.state.value.sheet)
        assertNull(sheet.error)
        assertTrue(sheet.failed)
    }

    @Test
    fun `plan trip and open trip are effects, telemetry has enums only`() =
        kotlinx.coroutines.test.runTest {
            val vm = vm()
            vm.effects.test {
                vm.onEvent(TripsEvent.PlanTrip)
                assertEquals(TripsEffect.PlanTrip, awaitItem())
                vm.onEvent(TripsEvent.OpenTrip(4))
                assertEquals(TripsEffect.OpenTrip(4), awaitItem())
            }
            assertTrue(telemetry.events.all { (_, props) -> props.values.all { it is String } })
        }
}
