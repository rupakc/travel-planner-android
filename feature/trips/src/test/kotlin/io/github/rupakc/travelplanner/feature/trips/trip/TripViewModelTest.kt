package io.github.rupakc.travelplanner.feature.trips.trip

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import io.github.rupakc.travelplanner.core.model.trips.PrepResponse
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePrepRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TripViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val trips = FakeTripsRepository()
    private var plans = FakePlansRepository()
    private val prep = FakePrepRepository()
    private var store = FakeCurrentPlanStore(plans)
    private val telemetry = FakeTelemetry()
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2026-11-12T23:30:00Z")

            override fun zone() = ZoneOffset.ofHours(5)
        }

    private val searchData =
        Json.parseToJsonElement(
            """{"origin":"NYC","destination":"Lisbon","departure_date":"2027-04-01","return_date":"2027-04-08"}""",
        ).jsonObject

    private fun trip(phase: Phase = Phase.PLANNING, dest: String? = "Lisbon") =
        Trip(id = 12, title = "Lisbon in spring", destination = dest, startDate = "2027-04-01", endDate = "2027-04-08", phase = phase)

    private fun vm(tab: String? = null) =
        TripViewModel(SavedStateHandle(mapOf("tripId" to 12L, "tab" to tab)), trips, plans, prep, store, telemetry, clock)

    private fun givenPlan(selections: PlanSelections = PlanSelections(), data: JsonObject = searchData) {
        plans = FakePlansRepository(listOf(Plan(id = 12, name = "Lisbon in spring", searchData = data, selections = selections)))
        store = FakeCurrentPlanStore(plans)
    }

    @Test
    fun `loads the trip with the device local date, the plan and the completeness`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan(PlanSelections(flight = Flight()))
        val s = vm().state.value
        assertEquals(12L, s.trip?.id)
        assertEquals(PlanStatus.Ready, s.planStatus)
        assertEquals(20, s.completeness?.percent)
        assertTrue(trips.calls.contains("get:12:2026-11-13"))
        assertEquals(
            listOf("completeness_view" to mapOf<String, Any?>("surface" to "trip", "percent" to 20)),
            telemetry.events.filter { it.first == "completeness_view" },
        )
    }

    @Test
    fun `planning reads the visa item for the visa part and ready does not`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        prep.prepResult = AppResult.Success(PrepResponse(items = listOf(PrepItem("visa", "na"))))
        assertEquals(20, vm().state.value.completeness?.percent)
        assertTrue(prep.calls.single().startsWith("prep:12:"))

        prep.calls.clear()
        trips.getResult = AppResult.Success(trip(Phase.READY))
        vm()
        assertTrue(prep.calls.isEmpty())
    }

    @Test
    fun `a failed trip load shows the backend message and retry loads it`() {
        trips.getResult = AppResult.Failure(AppError.NotFound("Trip not found"))
        givenPlan()
        val vm = vm()
        assertEquals("Trip not found", vm.state.value.error?.message)
        assertNull(vm.state.value.trip)
        trips.getResult = AppResult.Success(trip())
        vm.onEvent(TripEvent.Refresh)
        assertNull(vm.state.value.error)
        assertNotNull(vm.state.value.trip)
    }

    @Test
    fun `a failed plan load is retryable and keeps the trip`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        plans.failNext = AppError.Network()
        val vm = vm()
        assertEquals(PlanStatus.Failed, vm.state.value.planStatus)
        assertNotNull(vm.state.value.trip)
        vm.onEvent(TripEvent.RetryPlan)
        assertEquals(PlanStatus.Ready, vm.state.value.planStatus)
    }

    @Test
    fun `the tab argument is the requested tab and one trip_view is sent per tab`() {
        trips.getResult = AppResult.Success(trip(Phase.READY))
        givenPlan()
        val vm = vm(tab = "prep")
        assertEquals(TripTab.Prep, vm.state.value.requestedTab)
        vm.onEvent(TripEvent.TabShown(TripTab.Prep))
        vm.onEvent(TripEvent.TabShown(TripTab.Prep))
        vm.onEvent(TripEvent.TabShown(TripTab.Overview))
        assertEquals(
            listOf(
                mapOf<String, Any?>("surface" to "prep", "phase" to "ready"),
                mapOf<String, Any?>("surface" to "overview", "phase" to "ready"),
            ),
            telemetry.events.filter { it.first == "trip_view" }.map { it.second },
        )
    }

    @Test
    fun `rename validates then patches the title`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        val vm = vm()
        vm.onEvent(TripEvent.OpenRename)
        vm.onEvent(TripEvent.EditTitle("  "))
        vm.onEvent(TripEvent.SubmitDialog)
        assertTrue((vm.state.value.dialog as TripDialog.Rename).invalid)
        assertTrue(trips.patches.isEmpty())

        trips.updateResult = AppResult.Success(trip().copy(title = "Portugal"))
        vm.onEvent(TripEvent.EditTitle(" Portugal "))
        vm.onEvent(TripEvent.SubmitDialog)
        assertEquals("""{"title":"Portugal"}""", trips.patches.single().toString())
        assertEquals("Portugal", vm.state.value.trip?.title)
        assertNull(vm.state.value.dialog)
        assertTrue(telemetry.events.contains("trip_action" to mapOf<String, Any?>("action" to "rename")))
    }

    @Test
    fun `dates are validated locally and a blank pair clears with JSON nulls`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        val vm = vm()
        vm.onEvent(TripEvent.OpenDates)
        vm.onEvent(TripEvent.EditStart("2027-04-09"))
        vm.onEvent(TripEvent.EditEnd("2027-04-02"))
        vm.onEvent(TripEvent.SubmitDialog)
        assertTrue((vm.state.value.dialog as TripDialog.Dates).invalid)
        assertTrue(trips.patches.isEmpty())

        trips.updateResult = AppResult.Success(trip(Phase.DREAMING).copy(startDate = null, endDate = null))
        vm.onEvent(TripEvent.ClearDates)
        vm.onEvent(TripEvent.SubmitDialog)
        val body = trips.patches.single()
        assertEquals(JsonNull, body["start_date"])
        assertEquals(JsonNull, body["end_date"])
        assertEquals(Phase.DREAMING, vm.state.value.trip?.phase)
    }

    @Test
    fun `a server trip_dates_invalid blames the dates and a generic failure shows the message`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        val vm = vm()
        vm.onEvent(TripEvent.OpenDates)
        vm.onEvent(TripEvent.EditStart("2027-04-01"))
        trips.updateResult = AppResult.Failure(AppError.Client(422, "bad", code = "trip_dates_invalid"))
        vm.onEvent(TripEvent.SubmitDialog)
        val dialog = vm.state.value.dialog as TripDialog.Dates
        assertTrue(dialog.invalid)
        assertFalse(dialog.saving)

        trips.updateResult = AppResult.Failure(AppError.RateLimited("Slow down", 30))
        vm.onEvent(TripEvent.SubmitDialog)
        assertEquals("Slow down", vm.state.value.failure?.message)
    }

    @Test
    fun `phase override sends the wire value and follow my dates sends null`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        val vm = vm()
        trips.updateResult = AppResult.Success(trip(Phase.READY).copy(phaseSource = "manual"))
        vm.onEvent(TripEvent.SetPhase(Phase.READY))
        assertEquals(JsonPrimitive("ready"), trips.patches.last()["phase"])
        assertEquals("manual", vm.state.value.trip?.phaseSource)

        trips.updateResult = AppResult.Success(trip())
        vm.onEvent(TripEvent.SetPhase(null))
        assertEquals(JsonNull, trips.patches.last()["phase"])
        assertEquals(
            listOf("phase_override", "phase_clear"),
            telemetry.events.filter { it.first == "trip_action" }.map { it.second["action"] },
        )
    }

    @Test
    fun `delete asks first, then deletes and leaves`() =
        kotlinx.coroutines.test.runTest {
            trips.getResult = AppResult.Success(trip())
            givenPlan()
            val vm = vm()
            vm.onEvent(TripEvent.AskDelete)
            assertEquals(TripDialog.ConfirmDelete, vm.state.value.dialog)
            assertFalse(trips.calls.any { it.startsWith("delete") })
            vm.effects.test {
                vm.onEvent(TripEvent.ConfirmDelete)
                assertEquals(TripEffect.Deleted, awaitItem())
            }
            assertTrue(trips.calls.contains("delete:12"))
        }

    @Test
    fun `a failed delete keeps the trip and shows the message`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        val vm = vm()
        trips.deleteResult = AppResult.Failure(AppError.Server(503, "Down"))
        vm.onEvent(TripEvent.ConfirmDelete)
        assertEquals("Down", vm.state.value.failure?.message)
        assertNotNull(vm.state.value.trip)
    }

    @Test
    fun `open planner loads the saved plan into the draft and opens results`() =
        kotlinx.coroutines.test.runTest {
            trips.getResult = AppResult.Success(trip())
            givenPlan(PlanSelections(flight = Flight()))
            val vm = vm()
            vm.effects.test {
                vm.onEvent(TripEvent.OpenPlanner)
                assertEquals(TripEffect.OpenPlanner, awaitItem())
            }
            assertEquals(vm.state.value.plan?.id, store.draft.value.planId)
            assertTrue(telemetry.events.contains("trip_action" to mapOf<String, Any?>("action" to "open_planner")))
        }

    @Test
    fun `an unsaved draft for another plan asks before it is replaced`() {
        trips.getResult = AppResult.Success(trip())
        givenPlan()
        store.setSelections(PlanSelections(flight = Flight()))
        val vm = vm()
        vm.onEvent(TripEvent.OpenPlanner)
        assertEquals(TripDialog.ConfirmReplaceDraft, vm.state.value.dialog)
        assertNull(store.draft.value.planId)
        vm.onEvent(TripEvent.ConfirmOpenPlanner)
        assertEquals(vm.state.value.plan?.id, store.draft.value.planId)
    }

    @Test
    fun `a dreaming trip without a search offers plan it with its destination`() =
        kotlinx.coroutines.test.runTest {
            trips.getResult = AppResult.Success(trip(Phase.DREAMING, dest = "Rome").copy(startDate = null, endDate = null))
            givenPlan(data = JsonObject(mapOf("destination" to JsonPrimitive("Rome"))))
            val vm = vm()
            assertTrue(vm.state.value.canPlanIt)
            vm.effects.test {
                vm.onEvent(TripEvent.PlanIt)
                assertEquals(TripEffect.PlanIt("Rome"), awaitItem())
            }
            assertTrue(telemetry.events.contains("trip_action" to mapOf<String, Any?>("action" to "plan_it")))
        }

    @Test
    fun `returning to the overview reloads the trip silently`() {
        trips.getResult = AppResult.Success(trip(Phase.READY))
        givenPlan()
        val vm = vm()
        val before = trips.calls.count { it.startsWith("get") }
        vm.onEvent(TripEvent.SelectTab(TripTab.Overview))
        assertEquals(before + 1, trips.calls.count { it.startsWith("get") })
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `a phase change drops the requested tab so the tabs follow the new phase`() {
        trips.getResult = AppResult.Success(trip(Phase.READY))
        givenPlan()
        val vm = vm()
        vm.onEvent(TripEvent.SelectTab(TripTab.Money))
        assertEquals(TripTab.Money, vm.state.value.requestedTab)

        trips.updateResult = AppResult.Success(trip(Phase.MEMORIES).copy(phaseSource = "manual"))
        vm.onEvent(TripEvent.SetPhase(Phase.MEMORIES))
        assertNull(vm.state.value.requestedTab)
        val tabs = TripTabs.resolve(Phase.MEMORIES, vm.state.value.requestedTab, TripTab.entries.toSet())
        assertEquals(TripTab.Recap, tabs.selected)
    }

    @Test
    fun `an edit that keeps the phase keeps the requested tab`() {
        trips.getResult = AppResult.Success(trip(Phase.READY))
        givenPlan()
        val vm = vm()
        vm.onEvent(TripEvent.SelectTab(TripTab.Money))
        trips.updateResult = AppResult.Success(trip(Phase.READY).copy(title = "Renamed"))
        vm.onEvent(TripEvent.SetPhase(Phase.READY))
        assertEquals(TripTab.Money, vm.state.value.requestedTab)
    }
}
