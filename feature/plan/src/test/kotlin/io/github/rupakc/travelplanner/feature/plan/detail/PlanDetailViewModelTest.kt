package io.github.rupakc.travelplanner.feature.plan.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppBuildInfo
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.feature.plan.FixedClock
import io.github.rupakc.travelplanner.feature.plan.export.ExportRequest
import io.github.rupakc.travelplanner.feature.plan.model.plan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlanDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val saved =
        plan(
            7,
            "Japan",
            "Tokyo",
            "2027-04-01",
            "2027-04-04",
            "2026-11-01 10:00:00",
            selections =
                PlanSelections(
                    hotel = Hotel(name = "Park Hyatt", totalPriceUsd = 1200.0),
                    activities = listOf(Activity(name = "Sushi", priceUsd = 80.0)),
                ),
        )

    private val repo = FakePlansRepository(listOf(saved))
    private val store = FakeCurrentPlanStore(repo)

    private fun viewModel(id: Long = 7, webBaseUrl: String = "https://voyager.example/") =
        PlanDetailViewModel(
            SavedStateHandle(mapOf(PlanDetailViewModel.PLAN_ID_ARG to id)),
            repo,
            store,
            AppBuildInfo(
                defaultBaseUrl = "http://10.0.2.2:8001",
                flavor = "local",
                versionName = "t",
                debug = true,
                cleartextAllowed = true,
                webBaseUrl = webBaseUrl,
            ),
            FixedClock(),
            TestAppDispatchers(main.testDispatcher),
            PlaceNameLookup(search),
        )

    private val search = FakeSearchRepository()

    @Test
    fun `the header names the cities of airport codes`() =
        runTest {
            search.airports = listOf(Airport(iataCode = "LIS", city = "Lisbon"), Airport(iataCode = "JFK", city = "New York"))
            val created = repo.create("Weekend", TravelJson.parseToJsonElement("""{"origin":"JFK","destination":"LIS"}""") as JsonObject, PlanSelections())
            viewModel(id = (created as AppResult.Success).value.id).state.test {
                val summary = expectMostRecentItem().content!!.summary
                assertEquals("Lisbon", summary.destination)
                assertEquals("New York", summary.origin)
            }
        }

    @Test
    fun `shows the cached plan with groups, cost and summary`() =
        runTest {
            viewModel().state.test {
                val c = expectMostRecentItem().content!!
                assertEquals("Tokyo", c.summary.destination)
                assertEquals(1280.0, c.summary.totalCostUsd, 0.0)
                assertEquals(2, c.groups.size)
            }
        }

    @Test
    fun `missing plan shows the not found error`() =
        runTest {
            viewModel(id = 99).state.test {
                val s = expectMostRecentItem()
                assertNull(s.content)
                assertTrue(s.error is AppError.NotFound)
            }
        }

    @Test
    fun `share link creates a token and shares the web link`() =
        runTest {
            val vm = viewModel()
            vm.exportRequests.test {
                vm.onEvent(PlanDetailEvent.ShareLink)
                assertEquals(ExportRequest.Link("Japan", "https://voyager.example/share/share-token-7"), awaitItem())
            }
            assertEquals("share-token-7", repo.plans.first().single().shareToken)
        }

    @Test
    fun `revoking needs confirmation and clears the token`() =
        runTest {
            repo.share(7)
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlanDetailEvent.RevokeShare)
                assertEquals(DetailConfirm.REVOKE_SHARE, expectMostRecentItem().confirm)
                vm.onEvent(PlanDetailEvent.Confirm)
                val s = expectMostRecentItem()
                assertNull(s.confirm)
                assertEquals(DetailMessage.LinkRevoked, s.message)
                assertNull(s.content!!.summary.shareToken)
            }
        }

    @Test
    fun `export emits an ics file named after the plan`() =
        runTest {
            val vm = viewModel()
            vm.exportRequests.test {
                vm.onEvent(PlanDetailEvent.ExportCalendar)
                val ics = awaitItem() as ExportRequest.Ics
                assertEquals("japan.ics", ics.fileName)
                assertTrue(ics.content.contains("SUMMARY:🏨 Park Hyatt"))
                assertTrue(ics.content.contains("UID:plan-7-hotel@voyager-travel-planner"))
            }
        }

    @Test
    fun `calendar sheet lists events and adds one`() =
        runTest {
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlanDetailEvent.ShowCalendarEvents)
                val events = expectMostRecentItem().calendarEvents
                assertNotNull(events)
                assertEquals(1, events!!.size)
            }
        }

    @Test
    fun `open in results loads the cached plan into the draft without the network`() =
        runTest {
            val vm = viewModel()
            vm.state.test { expectMostRecentItem() }
            repo.failNext = AppError.Network()
            vm.navigationRequests.test {
                vm.onEvent(PlanDetailEvent.OpenInResults)
                assertEquals(DetailNavigation.OpenResults, awaitItem())
            }
            assertEquals(7L, store.draft.value.planId)
            assertEquals("Park Hyatt", store.draft.value.selections.hotel?.name)
            // Opened from a saved plan: Results locks itinerary editing (web loadedPlanId).
            assertTrue(store.draft.value.loadedPlan)
        }

    @Test
    fun `open in results asks before discarding an unsaved draft`() =
        runTest {
            store.restore(PlanDraft(selections = PlanSelections(activities = listOf(Activity(name = "Unsaved"))), dirty = true))
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlanDetailEvent.OpenInResults)
                assertEquals(DetailConfirm.DISCARD_DRAFT, expectMostRecentItem().confirm)
                assertEquals("Unsaved", store.draft.value.selections.activities.single().name)
                vm.onEvent(PlanDetailEvent.Confirm)
                expectMostRecentItem()
            }
            assertEquals(7L, store.draft.value.planId)
        }

    @Test
    fun `delete confirms then removes and navigates away`() =
        runTest {
            val vm = viewModel()
            vm.navigationRequests.test {
                vm.onEvent(PlanDetailEvent.Delete)
                vm.onEvent(PlanDetailEvent.Confirm)
                assertEquals(DetailNavigation.Deleted, awaitItem())
            }
            assertTrue(repo.plans.first().isEmpty())
        }

    @Test
    fun `share link uses the web app address, else the app link — never the API server`() {
        assertEquals(ShareLink("https://voyager.example/share/abc", appOnly = false), PlanDetailViewModel.shareLink("https://voyager.example/", "abc"))
        assertEquals(ShareLink("travelplanner://share/abc", appOnly = true), PlanDetailViewModel.shareLink("", "abc"))
    }

    @Test
    fun `without a web address the shared link is the app link and says so`() =
        runTest {
            val vm = viewModel(webBaseUrl = "")
            vm.exportRequests.test {
                vm.onEvent(PlanDetailEvent.ShareLink)
                assertEquals(ExportRequest.Link("Japan", "travelplanner://share/share-token-7", appOnly = true), awaitItem())
            }
        }

    @Test
    fun `a cached token is re-checked with the server so a revoked link is never re-shared`() =
        runTest {
            val stale = FakePlansRepository(listOf(saved.copy(shareToken = "revoked-on-web")))
            val vm =
                PlanDetailViewModel(
                    SavedStateHandle(mapOf(PlanDetailViewModel.PLAN_ID_ARG to 7L)),
                    stale,
                    store,
                    AppBuildInfo("", "local", "t", true, true, webBaseUrl = "https://voyager.example"),
                    FixedClock(),
                    TestAppDispatchers(main.testDispatcher),
                    PlaceNameLookup(search),
                )
            vm.exportRequests.test {
                vm.onEvent(PlanDetailEvent.ShareLink)
                assertEquals("https://voyager.example/share/share-token-7", (awaitItem() as ExportRequest.Link).url)
            }
        }

    @Test
    fun `offline uses the cached token`() =
        runTest {
            val offline = FakePlansRepository(listOf(saved.copy(shareToken = "cached")))
            val vm =
                PlanDetailViewModel(
                    SavedStateHandle(mapOf(PlanDetailViewModel.PLAN_ID_ARG to 7L)),
                    offline,
                    store,
                    AppBuildInfo("", "local", "t", true, true, webBaseUrl = "https://voyager.example"),
                    FixedClock(),
                    TestAppDispatchers(main.testDispatcher),
                    PlaceNameLookup(search),
                )
            vm.state.test { expectMostRecentItem() }
            offline.failNext = AppError.Network()
            vm.exportRequests.test {
                vm.onEvent(PlanDetailEvent.ShareLink)
                assertEquals("https://voyager.example/share/cached", (awaitItem() as ExportRequest.Link).url)
            }
        }

    @Test
    fun `offline refresh shows the cached copy as stale and blocks server actions`() =
        runTest {
            repo.failNext = AppError.Network()
            val vm = viewModel()
            vm.state.test {
                val s = expectMostRecentItem()
                assertNotNull(s.content)
                assertTrue(s.offline)
                vm.onEvent(PlanDetailEvent.Delete)
                vm.onEvent(PlanDetailEvent.Confirm)
                assertEquals(DetailMessage.NeedsConnection, expectMostRecentItem().message)
            }
            assertEquals(1, repo.plans.first().size)
        }

    @Test
    fun `server error keeps the cached copy flagged stale but not offline`() =
        runTest {
            repo.failNext = AppError.Server(500, null)
            viewModel().state.test {
                val s = expectMostRecentItem()
                assertNotNull(s.content)
                assertTrue(s.staleError is AppError.Server)
                assertFalse(s.offline)
            }
        }

    @Test
    fun `a 404 hides the cached copy and shows not found`() =
        runTest {
            repo.failNext = AppError.NotFound("Plan not found")
            viewModel().state.test {
                val s = expectMostRecentItem()
                assertNull(s.content)
                assertTrue(s.error is AppError.NotFound)
            }
        }

    @Test
    fun `a plan deleted elsewhere after loading turns into not found, not an endless skeleton`() =
        runTest {
            val vm = viewModel()
            vm.state.test {
                assertNotNull(expectMostRecentItem().content)
                repo.delete(7)
                val s = expectMostRecentItem()
                assertNull(s.content)
                assertFalse(s.isLoading)
                assertTrue(s.error is AppError.NotFound)
            }
        }

    @Test
    fun `open in results confirms before dropping unsaved edits of the same plan`() =
        runTest {
            store.restore(PlanDraft(planId = 7, selections = PlanSelections(), dirty = true))
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlanDetailEvent.OpenInResults)
                assertEquals(DetailConfirm.DISCARD_DRAFT, expectMostRecentItem().confirm)
            }
        }
}
