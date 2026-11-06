package io.github.rupakc.travelplanner.feature.alerts

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTab
import io.github.rupakc.travelplanner.core.domain.live.LiveError
import io.github.rupakc.travelplanner.core.domain.live.LiveFailure
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckResult
import io.github.rupakc.travelplanner.core.model.alerts.LiveQuote
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.search.searchKey
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeLiveCheckRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeNotificationsRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePreferencesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchLauncher
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val clock =
        object : AppClock {
            override fun now(): Instant = Instant.parse("2026-11-05T12:00:00Z")

            override fun zone(): ZoneId = ZoneId.of("UTC")
        }
    private val fares = FakeFareWatchesRepository()
    private val prices = FakePriceWatchesRepository()
    private val search = FakeSearchRepository()
    private val plan = FakeCurrentPlanStore()
    private val launcher = FakeSearchLauncher(plan, search)
    private val notifications = FakeNotificationsRepository()
    private val liveChecks = FakeLiveCheckRepository()

    private fun fare(id: Long, status: String = "active") =
        FareWatch(
            id = id,
            status = status,
            originLabel = "Berlin, Germany",
            destinationLabel = "Lisbon, Portugal",
            departureDate = "2026-12-01",
            returnDate = "2026-12-08",
            numTravelers = 2,
            baselinePrice = 500.0,
            lastPrice = 454.0,
        )

    private fun TestScope.vm(args: Map<String, Any?> = emptyMap(), appScope: CoroutineScope = backgroundScope) =
        AlertsViewModel(
            SavedStateHandle(args),
            fares,
            prices,
            FakeHealthRepository(Health(serpapiConfigured = true, emailAlertsAvailable = true)),
            FakeAuthRepository(Session("t", TestUser.copy(email = "a@b.c"))),
            FakePreferencesRepository(),
            launcher,
            notifications,
            clock,
            appScope,
            PlaceNameLookup(search),
            liveChecks,
        )

    @Test
    fun `fare routes saved with airport codes show the cities and match them`() =
        runTest {
            search.airports = listOf(Airport(iataCode = "JFK", city = "New York"), Airport(iataCode = "LIS", city = "Lisbon"))
            fares.watches.value = listOf(fare(1).copy(originLabel = "JFK", destinationLabel = "LIS", originCode = "JFK", destinationCode = "LIS"))
            val vm = vm()
            advanceUntilIdle()
            assertEquals(mapOf("JFK" to "New York", "LIS" to "Lisbon"), vm.state.value.placeNames)
            vm.onEvent(AlertsEvent.Query("lisbon"))
            assertEquals(1, vm.state.value.shown.size)
        }

    @Test
    fun `loads both lists with tab counts, search and capabilities`() =
        runTest {
            fares.watches.value = listOf(fare(1), fare(2, status = "expired"))
            prices.watches.value = listOf(PriceWatch(id = 1, kind = "hotel", title = "Lumen Hotel", status = "active"))
            val vm = vm()
            advanceUntilIdle()
            val s = vm.state.value
            assertEquals(3, s.counts[AlertTab.ALL])
            assertEquals(2, s.counts[AlertTab.FLIGHTS])
            assertEquals(1, s.counts[AlertTab.HOTELS])
            assertEquals(2, s.active.size)
            assertEquals(1, s.past.size)
            assertEquals(true, s.livePrices)
            assertTrue(s.emailAvailable)
            assertEquals("a@b.c", s.userEmail)
            vm.onEvent(AlertsEvent.SelectTab(AlertTab.HOTELS))
            assertEquals(listOf("price-watch-1"), vm.state.value.shown.map { it.key })
            vm.onEvent(AlertsEvent.SelectTab(AlertTab.ALL))
            vm.onEvent(AlertsEvent.Query("lumen"))
            assertEquals(listOf("price-watch-1"), vm.state.value.shown.map { it.key })
        }

    @Test
    fun `deep link focuses the watch and leaves a hiding tab`() =
        runTest {
            fares.watches.value = listOf(fare(7))
            prices.watches.value = listOf(PriceWatch(id = 7, kind = "trip", title = "Lisbon trip"))
            val vm = vm(mapOf(AlertsViewModel.ARG_KIND to "trip", AlertsViewModel.ARG_WATCH to 7L))
            advanceUntilIdle()
            assertEquals("price-watch-7", vm.state.value.focusKey)
            val fareVm = vm(mapOf(AlertsViewModel.ARG_WATCH to 7L))
            advanceUntilIdle()
            assertEquals("watch-7", fareVm.state.value.focusKey)
        }

    @Test
    fun `pause and resume update the card and say so`() =
        runTest {
            fares.watches.value = listOf(fare(1))
            val vm = vm()
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.SetStatus("watch-1", active = false))
            advanceUntilIdle()
            assertEquals("paused", vm.state.value.fares!!.single().status)
            assertEquals(CardMessage.Paused(fare = true), vm.state.value.messages["watch-1"])
            assertTrue(vm.state.value.busy.isEmpty())
        }

    @Test
    fun `delete hides at once, undo restores, expiry deletes on the server`() =
        runTest {
            fares.watches.value = listOf(fare(1), fare(2))
            val vm = vm(appScope = this)
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.Delete("watch-1"))
            assertEquals(listOf(2L), vm.state.value.fares!!.map { it.id })
            vm.onEvent(AlertsEvent.Undo)
            assertEquals(setOf(1L, 2L), vm.state.value.fares!!.map { it.id }.toSet())
            assertEquals(2, fares.watches.value.size)
            vm.onEvent(AlertsEvent.Delete("watch-1"))
            vm.onEvent(AlertsEvent.UndoExpired)
            advanceUntilIdle()
            assertNull(vm.state.value.undo)
            assertEquals(listOf(2L), fares.watches.value.map { it.id })
        }

    @Test
    fun `undo still works after a long accessibility timeout (no fixed timer)`() =
        runTest {
            fares.watches.value = listOf(fare(1), fare(2))
            val vm = vm(appScope = this)
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.Delete("watch-1"))
            // TalkBack / "Time to take action" can keep the snackbar up for minutes.
            advanceTimeBy(120_000)
            advanceUntilIdle()
            assertEquals(2, fares.watches.value.size)
            vm.onEvent(AlertsEvent.Undo)
            advanceUntilIdle()
            assertEquals(setOf(1L, 2L), vm.state.value.fares!!.map { it.id }.toSet())
            assertEquals(2, fares.watches.value.size)
        }

    @Test
    fun `failed fare list still shows price watches with a retry`() =
        runTest {
            val failing =
                object : io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository by fares {
                    override suspend fun list() = AppResult.Failure(AppError.Network())
                }
            prices.watches.value = listOf(PriceWatch(id = 1, kind = "hotel", title = "Lumen"))
            val vm =
                AlertsViewModel(
                    SavedStateHandle(),
                    failing,
                    prices,
                    FakeHealthRepository(Health(serpapiConfigured = false)),
                    FakeAuthRepository(null),
                    FakePreferencesRepository(),
                    launcher,
                    notifications,
                    clock,
                    backgroundScope,
                    PlaceNameLookup(search),
                    liveChecks,
                )
            advanceUntilIdle()
            val s = vm.state.value
            assertTrue(s.faresFailed)
            assertFalse(s.loading)
            assertEquals(1, s.watches.size)
            assertEquals(false, s.livePrices)
        }

    @Test
    fun `replan starts a search for the watched trip`() =
        runTest {
            fares.watches.value = listOf(fare(1))
            val vm = vm()
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.Replan("watch-1"))
            advanceUntilIdle()
            assertEquals("Berlin, Germany", search.current.value.request?.origin)
            assertEquals(search.current.value.request?.searchKey(), plan.draft.value.searchKey)
        }

    @Test
    fun `saving the edit panel sends the target`() =
        runTest {
            fares.watches.value = listOf(fare(1))
            val vm = vm()
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.Save("watch-1", WatchEdit(AlertMode.BELOW_TARGET, 420.0, flexible = true, email = false, targetChanged = true)))
            advanceUntilIdle()
            assertEquals(CardMessage.Saved, vm.state.value.messages["watch-1"])
        }

    @Test
    fun `an alert found by Check now is marked seen so no system notification repeats it`() =
        runTest {
            val alert = AppNotification(id = 99, kind = "fare_drop", title = "Berlin → Lisbon dropped to $420")
            val checking =
                object : io.github.rupakc.travelplanner.core.data.repository.FareWatchesRepository by fares {
                    override suspend fun check(id: Long) = AppResult.Success(fare(1).copy(checkStatus = "checked", notification = alert))
                }
            fares.watches.value = listOf(fare(1))
            val vm =
                AlertsViewModel(
                    SavedStateHandle(),
                    checking,
                    prices,
                    FakeHealthRepository(Health()),
                    FakeAuthRepository(null),
                    FakePreferencesRepository(),
                    launcher,
                    notifications,
                    clock,
                    backgroundScope,
                    PlaceNameLookup(search),
                    liveChecks,
                )
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.Check("watch-1"))
            advanceUntilIdle()
            assertEquals(setOf(99L), notifications.seen)
            assertEquals("Berlin → Lisbon dropped to $420", (vm.state.value.messages["watch-1"] as CardMessage.Checked).outcome.notificationTitle)
        }

    @Test
    fun `the inbox marks what it shows as seen`() =
        runTest {
            val repo = FakeNotificationsRepository(listOf(AppNotification(id = 5, title = "x"), AppNotification(id = 6, title = "y")))
            NotificationsViewModel(repo, clock)
            advanceUntilIdle()
            assertEquals(setOf(5L, 6L), repo.seen)
        }

    @Test
    fun `search the web now sends the watch kind and id and keeps the answer in memory only`() =
        runTest {
            fares.watches.value = listOf(fare(1))
            prices.watches.value = listOf(PriceWatch(id = 4, kind = "hotel", title = "Lumen Hotel", status = "active", lastPrice = 398.0))
            val answer = LiveCheckResult(summary = "From €412 on Booking.com.", quotes = listOf(LiveQuote("Booking.com", 412.0, "EUR", 0)))
            liveChecks.result = AppResult.Success(answer)
            val vm = vm()
            advanceUntilIdle()
            val before = vm.state.value.watches
            vm.onEvent(AlertsEvent.LiveCheck("price-watch-4"))
            advanceUntilIdle()
            assertEquals(listOf("hotel" to 4L), liveChecks.requests)
            val live = vm.state.value.liveCheck!!
            assertEquals("price-watch-4", live.key)
            assertFalse(live.loading)
            assertEquals(answer, live.result)
            // Never merged into the watch, its history or its alerts.
            assertEquals(before, vm.state.value.watches)
            assertEquals(listOf(398.0), prices.watches.value.map { it.lastPrice })
            assertTrue(notifications.seen.isEmpty())
            assertTrue(vm.state.value.messages.isEmpty())
            vm.onEvent(AlertsEvent.LiveCheck("watch-1"))
            advanceUntilIdle()
            assertEquals("flight" to 1L, liveChecks.requests.last())
            vm.onEvent(AlertsEvent.DismissLiveCheck)
            assertNull(vm.state.value.liveCheck)
        }

    @Test
    fun `search the web now failures are classified and can be retried`() =
        runTest {
            fares.watches.value = listOf(fare(1))
            liveChecks.result = AppResult.Failure(AppError.Server(503, "AI limit reached for this month — try again on the 1st.", "ai_limit"))
            val vm = vm()
            advanceUntilIdle()
            vm.onEvent(AlertsEvent.LiveCheck("watch-1"))
            advanceUntilIdle()
            assertEquals(
                LiveError(LiveFailure.AI_LIMIT, "AI limit reached for this month — try again on the 1st.", "ai_limit"),
                vm.state.value.liveCheck?.error,
            )
            liveChecks.result = AppResult.Failure(AppError.Network())
            vm.onEvent(AlertsEvent.RetryLiveCheck)
            advanceUntilIdle()
            assertEquals(LiveFailure.OFFLINE, vm.state.value.liveCheck?.error?.failure)
            assertEquals(2, liveChecks.requests.size)
        }
}
