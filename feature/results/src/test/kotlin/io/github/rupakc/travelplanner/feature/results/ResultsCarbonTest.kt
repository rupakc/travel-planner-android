package io.github.rupakc.travelplanner.feature.results

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonCardState
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonDiet
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCarbonRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareCalendarRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFeatureVisibility
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlanJourney
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeQuickWatch
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchLauncher
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The Results carbon footprint card: feature gate, request body once the stream is done, diet choice. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultsCarbonTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val request =
        TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-12-01", returnDate = "2026-12-08", nationality = "American")

    private val clock =
        object : AppClock {
            override fun now(): Instant = LocalDate.parse("2026-11-05").atStartOfDay().toInstant(ZoneOffset.UTC)

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val search = FakeSearchRepository()
    private val plan = FakeCurrentPlanStore()
    private val carbon = FakeCarbonRepository()

    private fun vm() =
        ResultsViewModel(
            search = search,
            searchLauncher = FakeSearchLauncher(plan, search),
            plan = plan,
            health = FakeHealthRepository(Health(status = "ok")),
            fareCalendar = FakeFareCalendarRepository(),
            fareWatches = FakeFareWatchesRepository(),
            priceWatches = FakePriceWatchesRepository(),
            featureVisibility = FakeFeatureVisibility(),
            auth = FakeAuthRepository(Session("t", TestUser)),
            clock = clock,
            dispatchers = TestAppDispatchers(main.testDispatcher),
            savedState = SavedStateHandle(),
            placeNames = PlaceNameLookup(search),
            journey = FakePlanJourney().journey,
            quickWatcher = FakeQuickWatch().watcher,
            carbon = carbon,
        )

    @Test
    fun `carbon card is hidden and silent with the feature off`() =
        runTest(timeout = 30.seconds) {
            val off = vm()
            off.state.test {
                search.start(request)
                main.testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(CarbonCardState.Hidden(), expectMostRecentItem().carbon)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(carbon.requests.isEmpty())
        }

    @Test
    fun `carbon card sends the result summary once the stream is done`() =
        runTest(timeout = 30.seconds) {
            carbon.enabled = true
            val on = vm()
            on.state.test {
                search.start(request)
                main.testDispatcher.scheduler.advanceUntilIdle()
                assertTrue(expectMostRecentItem().carbon is CarbonCardState.Ready)
                cancelAndIgnoreRemainingEvents()
            }
            val body = carbon.requests.last()
            val results = body["results"] as JsonObject
            assertTrue("flights" in results && "hotels" in results)
            assertEquals("\"${request.destination}\"", (body["search"] as JsonObject)["destination"].toString())
            assertFalse("selections" in body)
        }

    @Test
    fun `carbon diet change goes to the repository`() =
        runTest(timeout = 30.seconds) {
            carbon.enabled = true
            val vm = vm()
            vm.state.test {
                search.start(request)
                main.testDispatcher.scheduler.advanceUntilIdle()
                vm.onEvent(ResultsEvent.CarbonDiet(CarbonDiet.VEGETARIAN))
                main.testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(CarbonDiet.VEGETARIAN, expectMostRecentItem().carbon.diet)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("\"vegetarian\"", carbon.requests.last()["diet"].toString())
        }
}
