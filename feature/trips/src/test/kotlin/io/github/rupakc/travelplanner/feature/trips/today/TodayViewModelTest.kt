package io.github.rupakc.travelplanner.feature.trips.today

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.trips.CheckinResponse
import io.github.rupakc.travelplanner.core.model.trips.CheckinsResponse
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Streak
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeGameEvents
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeTelemetry
import io.github.rupakc.travelplanner.core.testing.fake.FakeTripsRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodayViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val trips = FakeTripsRepository()
    private val telemetry = FakeTelemetry()

    // 23:30 UTC on the 12th is already the 13th at 08:30 in Tokyo: the day sent must be the local one.
    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2026-11-12T23:30:00Z")

            override fun zone() = ZoneOffset.ofHours(9)
        }

    private val trip =
        Trip(id = 12, title = "Tokyo", destination = "Tokyo", startDate = "2026-11-12", endDate = "2026-11-20", phase = Phase.ON_THE_ROAD, dayIndex = 2)

    private fun plans() =
        FakePlansRepository(
            listOf(
                Plan(
                    id = 12,
                    name = "Tokyo",
                    selections =
                        PlanSelections(
                            itinerarySlots =
                                listOf(
                                    ItinerarySlot(
                                        timeOfDay = "morning",
                                        activity = "Senso-ji",
                                        city = "Tokyo",
                                        dayNumber = 2,
                                        date = "2026-11-13",
                                        startTime = "09:00",
                                        endTime = "11:00",
                                    ),
                                ),
                        ),
                ),
            ),
        )

    private fun vm(): TodayViewModel {
        trips.getResult = AppResult.Success(trip)
        return TodayViewModel(SavedStateHandle(mapOf("tripId" to 12L)), trips, plans(), telemetry, clock, FakeGameEvents())
    }

    private fun client(code: String) =
        AppResult.Failure(
            AppError.Client(
                status =
                    if (code ==
                        "checkin_out_of_trip"
                    ) {
                        409
                    } else {
                        422
                    },
                message = "server text",
                code = code,
            ),
        )

    @Test
    fun `shows day N, the city, today's slots and next up`() =
        runTest(timeout = 30.seconds) {
            val s = vm().state.value
            assertEquals(2, s.dayNumber)
            assertEquals("Tokyo", s.city)
            assertEquals("Senso-ji", s.nextUp?.slot?.activity)
            assertEquals(1, s.day?.slots?.size)
            assertTrue(trips.calls.contains("get:12:2026-11-13"))
            assertTrue(telemetry.events.contains("today_action" to mapOf<String, Any?>("action" to "next_up")))
        }

    @Test
    fun `check-in sends the local date, not the UTC one`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            trips.checkinResult = AppResult.Success(CheckinResponse(day = "2026-11-13", dayIndex = 2, city = "Tokyo"))
            vm.onEvent(TodayEvent.CheckIn)
            assertTrue(trips.calls.contains("checkin:12:2026-11-13"))
            assertTrue(vm.state.value.checkedIn)
            assertTrue(telemetry.events.contains("today_action" to mapOf<String, Any?>("action" to "checkin")))
        }

    @Test
    fun `a second tap is a no-op`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            trips.checkinResult = AppResult.Success(CheckinResponse(day = "2026-11-13"))
            vm.onEvent(TodayEvent.CheckIn)
            vm.onEvent(TodayEvent.CheckIn)
            assertEquals(1, trips.calls.count { it.startsWith("checkin:") })
        }

    @Test
    fun `an already checked-in day is known from the check-ins list`() =
        runTest(timeout = 30.seconds) {
            trips.checkinsResult = AppResult.Success(CheckinsResponse(days = listOf("2026-11-13"), streak = Streak(current = 3)))
            val vm = vm()
            assertTrue(vm.state.value.checkedIn)
            assertEquals(3, vm.state.value.streak?.current)
            vm.onEvent(TodayEvent.CheckIn)
            assertFalse(trips.calls.any { it.startsWith("checkin:") })
        }

    @Test
    fun `out of trip and invalid day map to their own problems`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            trips.checkinResult = client("checkin_out_of_trip")
            vm.onEvent(TodayEvent.CheckIn)
            assertEquals(TodayRules.CheckinProblem.OutOfTrip, vm.state.value.failure?.problem)
            assertFalse(vm.state.value.checkedIn)
            trips.checkinResult = client("checkin_day_invalid")
            vm.onEvent(TodayEvent.CheckIn)
            assertEquals(TodayRules.CheckinProblem.DayInvalid, vm.state.value.failure?.problem)
            assertNull(vm.state.value.failure?.message)
        }

    @Test
    fun `a new country is flagged and the streak kept`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            trips.checkinResult = AppResult.Success(CheckinResponse(newCountry = true, countryCode = "JP", streak = Streak(current = 1, longest = 1)))
            vm.onEvent(TodayEvent.CheckIn)
            assertTrue(vm.state.value.newCountry)
            assertEquals("JP", vm.state.value.countryCode)
            assertTrue(vm.state.value.gameVisible)
            assertEquals(1, vm.state.value.streak?.current)
            vm.onEvent(TodayEvent.DismissBanner)
            assertFalse(vm.state.value.newCountry)
        }

    @Test
    fun `telemetry carries only the action enum`() =
        runTest(timeout = 30.seconds) {
            val vm = vm()
            trips.checkinResult = AppResult.Success(CheckinResponse(city = "Tokyo"))
            vm.onEvent(TodayEvent.CheckIn)
            vm.onEvent(TodayEvent.DirectionsOpened)
            telemetry.events.filter { it.first == "today_action" }.forEach { assertEquals(setOf("action"), it.second.keys) }
        }
}
