package io.github.rupakc.travelplanner.feature.trips.offline

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.OfflinePackRepository
import io.github.rupakc.travelplanner.core.model.trips.OfflinePack
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeOfflinePackRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OfflinePackViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeOfflinePackRepository()
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2027-04-01T23:30:00Z")

            override fun zone() = ZoneOffset.ofHours(5)
        }

    private fun vm() = OfflinePackViewModel(repo, telemetry, clock).also { it.start(12) }

    @Test
    fun `a saved pack shows without any network call and opening is reported once`() {
        repo.packs.value = mapOf(12L to OfflinePack(tripId = 12, title = "Lisbon"))
        val vm = vm()
        assertFalse(vm.state.value.loading)
        assertEquals("Lisbon", vm.state.value.view!!.pack.title)
        assertTrue(repo.calls.isEmpty())
        vm.start(12)
        assertEquals(listOf("offline_pack" to mapOf<String, Any?>("action" to "open", "result" to "ok", "kb" to 0)), telemetry.events)
    }

    @Test
    fun `nothing saved is an empty state not an error`() {
        val vm = vm()
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.view)
    }

    @Test
    fun `save uses the device local date and the new pack appears`() {
        val vm = vm()
        vm.onEvent(OfflinePackEvent.Save)
        assertEquals(listOf("save:12:2027-04-02"), repo.calls)
        assertNotNull(vm.state.value.view)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `a missing connection keeps the saved pack and says so`() {
        repo.packs.value = mapOf(12L to OfflinePack(tripId = 12))
        repo.saveResult = AppResult.Failure(AppError.Network())
        val vm = vm()
        vm.onEvent(OfflinePackEvent.Save)
        assertTrue(vm.state.value.offline)
        assertEquals(PackFailure.Kind.FAILED, vm.state.value.failure!!.kind)
        assertNotNull(vm.state.value.view)
    }

    @Test
    fun `disabled and storage failures map to their own messages and backend text is kept`() {
        val vm = vm()
        repo.saveResult = AppResult.Failure(AppError.NotFound("off", "offline_pack_disabled"))
        vm.onEvent(OfflinePackEvent.Save)
        assertEquals(PackFailure.Kind.UNSUPPORTED, vm.state.value.failure!!.kind)
        repo.saveResult = AppResult.Failure(AppError.Unknown(OfflinePackRepository.STORAGE_FULL))
        vm.onEvent(OfflinePackEvent.Save)
        assertEquals(PackFailure.Kind.QUOTA, vm.state.value.failure!!.kind)
        repo.saveResult = AppResult.Failure(AppError.RateLimited("Slow down", 5))
        vm.onEvent(OfflinePackEvent.Save)
        assertEquals("Slow down", vm.state.value.failure!!.message)
    }

    @Test
    fun `remove clears the pack and swap and amount are local`() {
        repo.packs.value = mapOf(12L to OfflinePack(tripId = 12))
        val vm = vm()
        vm.onEvent(OfflinePackEvent.SetAmount("10"))
        vm.onEvent(OfflinePackEvent.Swap)
        assertEquals("10", vm.state.value.amount)
        assertFalse(vm.state.value.toLocal)
        vm.onEvent(OfflinePackEvent.Remove)
        assertNull(vm.state.value.view)
        assertEquals(listOf("remove:12"), repo.calls)
    }
}
