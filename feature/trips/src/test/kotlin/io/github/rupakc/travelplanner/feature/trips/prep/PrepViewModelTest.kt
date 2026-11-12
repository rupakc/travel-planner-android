package io.github.rupakc.travelplanner.feature.trips.prep

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import io.github.rupakc.travelplanner.core.model.trips.PrepResponse
import io.github.rupakc.travelplanner.core.model.trips.PrepToggleResponse
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakePrepRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrepViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakePrepRepository()
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2026-11-12T23:30:00Z")

            override fun zone() = ZoneOffset.ofHours(5)
        }

    private fun item(id: String, status: String = "todo", auto: Boolean = false) = PrepItem(id, status, auto)

    private fun response(items: List<PrepItem>): PrepResponse {
        val live = items.filter { it.status != "na" }
        val done = live.count { it.status == "done" }
        return PrepResponse(items, done, live.size, if (live.isEmpty()) 0 else done * 100 / live.size, live.isNotEmpty() && done == live.size)
    }

    private fun toggled(items: List<PrepItem>) =
        response(items).let { PrepToggleResponse(it.items, it.done, it.total, it.percent, it.complete) }

    private fun vm(items: List<PrepItem>): PrepViewModel {
        repo.prepResult = AppResult.Success(response(items))
        return PrepViewModel(SavedStateHandle(mapOf("tripId" to 12L)), repo, telemetry, clock)
    }

    private val three = listOf(item("visa", "na"), item("passport"), item("insurance"), item("packing"))

    @Test
    fun `loads with the local date and counts without na items, flooring the percent`() {
        val vm = vm(listOf(item("visa", "na"), item("passport", "done"), item("insurance"), item("packing")))
        val s = vm.state.value
        assertEquals("prep:12:2026-11-13", repo.calls.first())
        assertEquals(1, s.summary.done)
        assertEquals(3, s.summary.total)
        assertEquals(33, s.summary.percent)
    }

    @Test
    fun `a toggle is optimistic and the server answer replaces the list`() {
        val vm = vm(three)
        val after = three.map { if (it.id == "passport") it.copy(status = "done") else it }
        repo.toggleResult = AppResult.Success(toggled(after))
        vm.onEvent(PrepEvent.Toggle("passport", true))
        assertEquals("done", vm.state.value.items.first { it.id == "passport" }.status)
        assertEquals(33, vm.state.value.summary.percent)
        assertEquals("toggle:12:passport:true", repo.calls.last())
        assertNull(vm.state.value.busyId)
        assertEquals(listOf("prep_toggle" to mapOf<String, Any?>("item" to "passport", "done" to true)), telemetry.events)
    }

    @Test
    fun `a failed toggle rolls back and says so`() {
        val vm = vm(three)
        repo.toggleResult = AppResult.Failure(AppError.Network())
        vm.onEvent(PrepEvent.Toggle("passport", true))
        assertEquals("todo", vm.state.value.items.first { it.id == "passport" }.status)
        assertEquals(0, vm.state.value.summary.percent)
        assertEquals(PrepNotice.ToggleFailed, vm.state.value.notice)
        assertTrue(telemetry.events.isEmpty())
    }

    @Test
    fun `a backend message is shown as is`() {
        val vm = vm(three)
        repo.toggleResult = AppResult.Failure(AppError.RateLimited("Slow down", 30))
        vm.onEvent(PrepEvent.Toggle("passport", true))
        assertEquals(PrepNotice.Backend("Slow down"), vm.state.value.notice)
    }

    @Test
    fun `409 auto and na and 404 unknown show a message and reload the list`() {
        listOf(
            AppError.Client(409, "x", code = "prep_item_auto") to PrepNotice.Auto,
            AppError.Client(409, "x", code = "prep_item_na") to PrepNotice.NotNeeded,
            AppError.NotFound("x", code = "prep_item_unknown") to PrepNotice.Unknown,
        ).forEach { (error, notice) ->
            val vm = vm(three)
            val loads = repo.calls.count { it.startsWith("prep:") }
            repo.toggleResult = AppResult.Failure(error)
            vm.onEvent(PrepEvent.Toggle("passport", true))
            assertEquals(notice, vm.state.value.notice)
            assertEquals("todo", vm.state.value.items.first { it.id == "passport" }.status)
            assertEquals(loads + 1, repo.calls.count { it.startsWith("prep:") })
        }
    }

    @Test
    fun `auto and na items are never sent`() {
        val vm = vm(listOf(item("visa", "na"), item("offline_pack", auto = true), item("passport")))
        vm.onEvent(PrepEvent.Toggle("offline_pack", true))
        vm.onEvent(PrepEvent.Toggle("visa", true))
        assertFalse(repo.calls.any { it.startsWith("toggle") })
    }

    @Test
    fun `finishing the list celebrates once and sends prep_complete`() {
        val items = listOf(item("passport", "done"), item("insurance"))
        val vm = vm(items)
        val after = items.map { it.copy(status = "done") }
        repo.toggleResult = AppResult.Success(toggled(after))
        vm.onEvent(PrepEvent.Toggle("insurance", true))
        assertTrue(vm.state.value.summary.complete)
        assertTrue(vm.state.value.celebrate)
        assertEquals(listOf("prep_toggle", "prep_complete"), telemetry.events.map { it.first })
        assertEquals(2, telemetry.events.last().second["items"])
    }

    @Test
    fun `a load failure offers retry with the backend message`() {
        repo.prepResult = AppResult.Failure(AppError.RateLimited("Slow down", 10))
        val vm = PrepViewModel(SavedStateHandle(mapOf("tripId" to 12L)), repo, telemetry, clock)
        assertTrue(vm.state.value.failed)
        assertEquals("Slow down", vm.state.value.errorMessage)
        repo.prepResult = AppResult.Success(response(three))
        vm.onEvent(PrepEvent.Retry)
        assertFalse(vm.state.value.failed)
        assertEquals(4, vm.state.value.items.size)
    }
}
