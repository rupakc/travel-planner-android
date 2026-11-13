package io.github.rupakc.travelplanner.feature.plan.detail

import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.alerts.PlanWatchOffers
import io.github.rupakc.travelplanner.core.domain.alerts.QuickWatchOutcome
import io.github.rupakc.travelplanner.core.model.alerts.QuickWatchResponse
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatchKinds
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeQuickWatch
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Wiring item 4: trip detail hosts Team G's PriceWatchSheet for "Watch whole trip". */
class TripWatchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock =
        object : AppClock {
            override fun now(): Instant = LocalDate.parse("2026-11-05").atStartOfDay().toInstant(ZoneOffset.UTC)

            override fun zone(): ZoneId = ZoneOffset.UTC
        }
    private val upcoming =
        Plan(
            id = 7,
            name = "Tokyo",
            searchData = JsonObject(emptyMap()),
        ).let {
            it.copy(
                searchData =
                    io.github.rupakc.travelplanner.core.model.json.TravelJson.encodeToJsonElement(
                        TravelSearchRequest.serializer(),
                        TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-12-01", nationality = "American"),
                    ) as JsonObject,
            )
        }

    private fun vm(health: Health = Health(fareWatchAvailable = true), quick: FakeQuickWatch = FakeQuickWatch()) =
        TripWatchViewModel(
            FakePriceWatchesRepository(),
            FakeHealthRepository(health),
            FakeAuthRepository(Session("t", TestUser)),
            clock,
            quick.watcher,
            PlanWatchOffers(quick.watcher, quick.alerts, quick.telemetry),
        )

    @Test
    fun `with one-tap on the button watches the whole trip at once instead of opening the sheet`() =
        runTest {
            val quick = FakeQuickWatch.on()
            quick.alerts.quickWatchResult =
                AppResult.Success(QuickWatchResponse("trip", JsonObject(mapOf("id" to JsonPrimitive(3))), existing = false))
            val vm = vm(quick = quick)
            vm.open(upcoming)
            assertNull(vm.sheet.value)
            val sent = quick.alerts.quickWatchCalls.single()
            assertEquals("trip", sent.kind)
            assertEquals("trip", sent.surface)
            assertEquals(7L, sent.planId)
            assertEquals(QuickWatchOutcome.Added("trip", 3), vm.outcomeFlow.first())
        }

    @Test
    fun `an upcoming saved trip opens the trip price-watch sheet with the plan id`() =
        runTest {
            val vm = vm()
            assertTrue(vm.canWatch(upcoming))
            vm.open(upcoming)
            val state = vm.sheet.value!!.state.value
            assertEquals(PriceWatchKinds.TRIP, state.kind)
            assertEquals("7", state.spec["plan_id"]?.jsonPrimitive?.content)
            vm.dismiss()
            assertNull(vm.sheet.value)
        }

    @Test
    fun `no live prices or a past trip hides the action`() =
        runTest {
            assertFalse(vm(Health(fareWatchAvailable = false)).canWatch(upcoming))
            val past = upcoming.copy(searchData = JsonObject(upcoming.searchData + ("departure_date" to JsonPrimitive("2026-10-01"))))
            assertFalse(vm().canWatch(past))
        }
}
