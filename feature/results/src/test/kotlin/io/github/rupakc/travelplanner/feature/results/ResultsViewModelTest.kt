package io.github.rupakc.travelplanner.feature.results

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureDescriptor
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.results.RemixPreset
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.HotelFilters
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.DataSource
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionDecoder
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.model.stream.SearchEvent
import io.github.rupakc.travelplanner.core.testing.Fixtures
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareCalendarRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFareWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResultsViewModelTest {
    @get:Rule val main = MainDispatcherRule(UnconfinedTestDispatcher())

    private val request =
        TravelSearchRequest(
            origin = "NYC",
            destination = "Tokyo",
            departureDate = "2026-12-01",
            returnDate = "2026-12-08",
            nationality = "American",
            budgetUsd = 6000.0,
        )

    private val clock =
        object : AppClock {
            override fun now(): Instant = LocalDate.parse("2026-11-05").atStartOfDay().toInstant(ZoneOffset.UTC)

            override fun zone(): ZoneId = ZoneOffset.UTC
        }

    private val search = FakeSearchRepository()
    private val plan = FakeCurrentPlanStore()
    private val health = FakeHealthRepository(Health(status = "ok", serpapiConfigured = true, fareWatchAvailable = true))
    private val fareWatches = FakeFareWatchesRepository()
    private val priceWatches = FakePriceWatchesRepository()
    private val profile = FakeProfileRepository()

    private fun vm(catalogue: FeatureCatalogue? = null) =
        ResultsViewModel(
            search = search,
            plan = plan,
            health = health,
            fareCalendar = FakeFareCalendarRepository(),
            fareWatches = fareWatches,
            priceWatches = priceWatches,
            profile = profile,
            catalogue = Optional.ofNullable(catalogue),
            clock = clock,
            dispatchers = TestAppDispatchers(main.testDispatcher),
        )

    private fun ResultsUiState.section(id: SectionId) = sections.first { it.id == id }

    @Test
    fun `full stream ends done with static sections replaced by AI and the draft attached`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                val s = expectMostRecentItem()
                assertEquals(StreamPhase.DONE, s.phase)
                assertEquals(SectionStatus.DONE, s.section(SectionId.VISA).status)
                // The AI visa (fee + official url) replaced the static one.
                assertEquals("https://www.mofa.go.jp", (s.section(SectionId.VISA).data as VisaData).requirement?.officialUrl)
                assertEquals(82, s.confidence?.total)
                assertNotNull(s.pricingAdvisor)
                assertNotNull(s.bestValue)
                assertEquals(request, plan.draft.value.searchRequest)
                assertTrue(plan.draft.value.searchData!!.containsKey("hotels"))
            }
        }

    @Test
    fun `static section shows as enhancing until the AI result arrives`() =
        runTest {
            val staticVisa = Fixtures.searchEvents(Fixtures.SEARCH_FULL).first { it is SearchEvent.Section && it.source == DataSource.STATIC }
            search.events = listOf(staticVisa)
            val vm = vm()
            vm.state.test {
                search.start(request)
                val s = expectMostRecentItem()
                assertEquals(SectionStatus.DONE, s.section(SectionId.VISA).status) // stream end marks everything done
                assertNotNull(s.section(SectionId.VISA).data)
            }
        }

    @Test
    fun `AI error never replaces existing static data and the interrupted stream offers retry`() =
        runTest {
            search.events = Fixtures.searchEvents(Fixtures.SEARCH_INTERRUPTED)
            val vm = vm()
            vm.state.test {
                search.start(request)
                val s = expectMostRecentItem()
                assertEquals(StreamPhase.INTERRUPTED, s.phase)
                assertFalse(s.section(SectionId.VISA).data!!.isError)
                assertTrue(s.section(SectionId.FLIGHTS).data!!.isError)
                search.events = Fixtures.searchEvents(Fixtures.SEARCH_FULL)
                vm.onEvent(ResultsEvent.RetryStream)
                assertEquals(StreamPhase.DONE, expectMostRecentItem().phase)
                assertEquals(2, search.started.size)
            }
        }

    @Test
    fun `failed stream exposes the backend message`() =
        runTest {
            search.failure = AppError.RateLimited("Too many requests — please wait 60 seconds", 60)
            val vm = vm()
            vm.state.test {
                search.start(request)
                val s = expectMostRecentItem()
                assertEquals(StreamPhase.FAILED, s.phase)
                assertEquals("Too many requests — please wait 60 seconds", s.error?.message)
            }
        }

    @Test
    fun `after process death the results come back from the draft without streaming`() =
        runTest {
            val hotels = Json.parseToJsonElement("""{"results":[{"name":"Park Hyatt Tokyo","price_per_night_usd":650}]}""").jsonObject
            plan.restore(PlanDraft(searchRequest = request, searchData = kotlinx.serialization.json.JsonObject(mapOf("hotels" to hotels))))
            val vm = vm()
            vm.state.test {
                val s = expectMostRecentItem()
                assertTrue(s.fromSnapshot)
                assertEquals("Park Hyatt Tokyo", (s.section(SectionId.HOTELS).data as HotelsData).results.single().name)
                assertTrue(search.started.isEmpty())
            }
        }

    @Test
    fun `unsaved draft request without results starts the stream, a saved plan does not`() =
        runTest {
            plan.restore(PlanDraft(searchRequest = request))
            vm()
            assertEquals(listOf(request), search.started)

            val other = FakeSearchRepository()
            val planStore = FakeCurrentPlanStore().apply { restore(PlanDraft(planId = 7, searchRequest = request)) }
            val saved =
                ResultsViewModel(
                    other,
                    planStore,
                    health,
                    FakeFareCalendarRepository(),
                    fareWatches,
                    priceWatches,
                    profile,
                    Optional.empty(),
                    clock,
                    TestAppDispatchers(main.testDispatcher),
                )
            saved.state.test {
                assertTrue(expectMostRecentItem().canRunSavedSearch)
                assertTrue(other.started.isEmpty())
                saved.onEvent(ResultsEvent.RunSavedSearch)
                assertEquals(listOf(request), other.started)
                val after = expectMostRecentItem()
                assertFalse(after.canRunSavedSearch)
                assertEquals(StreamPhase.DONE, after.phase)
            }
        }

    @Test
    fun `attaches to the stream Search already started and never starts a second one`() =
        runTest {
            search.start(request)
            plan.restore(PlanDraft(searchRequest = request))
            val vm = vm()
            vm.state.test {
                assertEquals(StreamPhase.DONE, expectMostRecentItem().phase)
                assertEquals(listOf(request), search.started)
            }
        }

    @Test
    fun `opening a saved plan after a finished stream for another trip renders the stored results, no stream`() =
        runTest {
            search.start(request)
            val hotels = Json.parseToJsonElement("""{"results":[{"name":"Saved Hotel","price_per_night_usd":90}]}""").jsonObject
            val saved = request.copy(destination = "Lisbon")
            val vm = vm()
            vm.state.test {
                plan.restore(PlanDraft(planId = 9, searchRequest = saved, searchData = kotlinx.serialization.json.JsonObject(mapOf("hotels" to hotels))))
                val s = expectMostRecentItem()
                assertTrue(s.fromSnapshot)
                assertEquals("Lisbon", s.request?.destination)
                assertEquals("Saved Hotel", (s.section(SectionId.HOTELS).data as HotelsData).results.single().name)
                assertEquals(1, search.started.size)
                // The stale stream never overwrites the saved plan's stored results.
                assertEquals(saved, plan.draft.value.searchRequest)
            }
        }

    @Test
    fun `source selection rules`() {
        val draftWithData =
            PlanDraft(
                planId = 1,
                searchRequest = request.copy(destination = "Rome"),
                searchData =
                    kotlinx.serialization.json.JsonObject(
                        mapOf("hotels" to kotlinx.serialization.json.JsonObject(emptyMap())),
                    ),
            )
        val idle = io.github.rupakc.travelplanner.core.model.search.SearchResultsState()
        val streaming = idle.copy(request = request, phase = StreamPhase.STREAMING)
        val done = streaming.copy(phase = StreamPhase.DONE)
        assertFalse(ResultsStateBuilder.useLiveStream(idle, draftWithData))
        assertTrue(ResultsStateBuilder.useLiveStream(streaming, draftWithData))
        assertFalse(ResultsStateBuilder.useLiveStream(done, draftWithData))
        assertTrue(ResultsStateBuilder.useLiveStream(done, draftWithData.copy(searchRequest = request)))
        assertTrue(ResultsStateBuilder.useLiveStream(done, PlanDraft()))
    }

    @Test
    fun `toggle adds to the plan and the plan bar sums the cost`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                val hotel = (expectMostRecentItem().section(SectionId.HOTELS).data as HotelsData).results.first()
                vm.onEvent(ResultsEvent.Toggle(PlanItem.HotelItem(hotel)))
                val s = expectMostRecentItem()
                assertEquals(1, s.plan.count)
                assertTrue(s.plan.total > 0)
                vm.onEvent(ResultsEvent.Toggle(PlanItem.HotelItem(hotel)))
                assertEquals(0, expectMostRecentItem().plan.count)
            }
        }

    @Test
    fun `best value apply then undo restores the previous picks`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                assertFalse(expectMostRecentItem().bestValue!!.applied)
                vm.onEvent(ResultsEvent.ApplyPackage)
                val applied = expectMostRecentItem()
                assertTrue(applied.bestValue!!.applied)
                assertTrue(applied.bestValue!!.canUndo)
                assertTrue(plan.draft.value.selections.count > 0)
                vm.onEvent(ResultsEvent.UndoPackage)
                assertEquals(0, expectMostRecentItem().plan.count)
            }
        }

    @Test
    fun `best value is disabled while a saved plan is open`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                plan.restore(plan.draft.value.copy(planId = 3))
                assertTrue(expectMostRecentItem().bestValue!!.disabled)
                vm.onEvent(ResultsEvent.ApplyPackage)
                assertEquals(0, plan.draft.value.selections.count)
            }
        }

    @Test
    fun `hotel filter replaces the section and records the active filter count`() =
        runTest {
            search.hotelsResult =
                AppResult.Success(
                    SectionDecoder.decode(
                        SectionId.HOTELS,
                        Json.parseToJsonElement("""{"results":[{"name":"Cheap Inn","price_per_night_usd":80}]}""").jsonObject,
                    ) as HotelsData,
                )
            val vm = vm()
            vm.state.test {
                search.start(request)
                vm.onEvent(ResultsEvent.ApplyHotelFilters(HotelFilters(maxPricePerNightUsd = 100.0, privateWashroom = true)))
                val s = expectMostRecentItem()
                assertEquals(listOf("Cheap Inn"), (s.section(SectionId.HOTELS).data as HotelsData).results.map { it.name })
                assertEquals(2, s.section(SectionId.HOTELS).activeFilterCount)
            }
        }

    @Test
    fun `failed filter keeps the old results and shows the backend message`() =
        runTest {
            search.flightsResult = AppResult.Failure(AppError.Server(503, "Live fares are not set up"))
            val vm = vm()
            vm.state.test {
                search.start(request)
                vm.onEvent(ResultsEvent.ApplyFlightFilters(io.github.rupakc.travelplanner.core.model.search.FlightFilters(maxStops = 0)))
                val flights = expectMostRecentItem().section(SectionId.FLIGHTS)
                assertEquals("Live fares are not set up", flights.filterError?.message)
                assertTrue((flights.data as FlightsData).results.isNotEmpty())
            }
        }

    @Test
    fun `budget show only what fits and find cheaper hotels`() =
        runTest {
            search.hotelsResult = AppResult.Success(HotelsData())
            val vm = vm()
            vm.state.test {
                search.start(request)
                val budget = expectMostRecentItem().budget!!
                assertFalse(budget.cardsReact)
                vm.onEvent(ResultsEvent.ShowOnlyFits(true))
                assertTrue(expectMostRecentItem().budget!!.cardsReact)
                vm.onEvent(ResultsEvent.BudgetShare(io.github.rupakc.travelplanner.core.domain.results.BudgetCategory.STAY, 100.0))
                val small = expectMostRecentItem().budget!!
                assertEquals(100.0, small.allocation.stay, 0.0)
                assertTrue(small.offerCheaperHotels)
                vm.onEvent(ResultsEvent.FindCheaperHotels)
                assertFalse(expectMostRecentItem().budget!!.findingCheaperHotels)
            }
        }

    @Test
    fun `remix reruns with the preset, starts a new plan and shows the diff`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                vm.onEvent(ResultsEvent.Remix(RemixPreset.WEEK_LATER))
                val s = expectMostRecentItem()
                assertEquals("2026-12-08", search.started.last().departureDate)
                assertEquals(RemixPreset.WEEK_LATER, s.remix?.preset)
                assertNotNull(s.remix?.rows)
            }
        }

    @Test
    fun `text filter narrows sections and hides the ones without matches`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                vm.onEvent(ResultsEvent.Query("hyatt"))
                val s = expectMostRecentItem()
                assertTrue(s.sections.any { it.id == SectionId.HOTELS })
                assertFalse(s.sections.any { it.id == SectionId.SIM })
                assertTrue(s.hiddenByQuery > 0)
            }
        }

    @Test
    fun `fare watch success and price watch no-live-price`() =
        runTest {
            val vm = vm()
            vm.messages.test {
                search.start(request)
                vm.onEvent(ResultsEvent.WatchFare)
                assertTrue(awaitItem() is ResultsMessage.FareWatched)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, fareWatches.watches.value.size)
            priceWatches.createResult =
                AppResult.Failure(
                    AppError.Client(
                        422,
                        "No live price for this hotel",
                        code = "no_live_price",
                        extras = mapOf("suggestions" to """[{"title":"Park Hyatt","price":700,"spec":{"name":"Park Hyatt"}}]"""),
                    ),
                )
            vm.state.test {
                val hotel = (expectMostRecentItem().section(SectionId.HOTELS).data as HotelsData).results.first()
                vm.onEvent(ResultsEvent.WatchHotel(hotel))
                val noLive = expectMostRecentItem().noLivePrice
                assertEquals("No live price for this hotel", noLive?.message)
                assertEquals("Park Hyatt", noLive?.suggestions?.single()?.title)
            }
        }

    @Test
    fun `traveller type puts hidden features last and collapsed`() =
        runTest {
            profile.profileState.value = TravellerProfile(onboardingCompleted = true, primaryType = TravellerType.LUXURY.name)
            val catalogue =
                object : FeatureCatalogue {
                    override val all: List<FeatureDescriptor> = emptyList()

                    override fun defaultsFor(types: Collection<TravellerType>): Set<FeatureId> = setOf(FeatureId.VISA_CHECKER)
                }
            val vm = vm(catalogue)
            vm.state.test {
                search.start(request)
                val s = expectMostRecentItem()
                assertEquals(SectionId.entries.size, s.sections.size)
                val events = s.section(SectionId.EVENTS)
                assertFalse(events.relevant)
                assertFalse(events.expanded)
                assertTrue(s.sections.indexOfFirst { !it.relevant } > s.sections.indexOfFirst { it.id == SectionId.VISA })
                vm.onEvent(ResultsEvent.ToggleSection(SectionId.EVENTS))
                assertTrue(expectMostRecentItem().section(SectionId.EVENTS).expanded)
            }
        }

    @Test
    fun `verification detail opens for a section`() =
        runTest {
            val vm = vm()
            vm.state.test {
                search.start(request)
                vm.onEvent(ResultsEvent.ShowVerification(SectionId.HOTELS))
                assertEquals("verified", expectMostRecentItem().verificationDetail?.verification?.level)
                vm.onEvent(ResultsEvent.DismissVerification)
                assertNull(expectMostRecentItem().verificationDetail)
            }
        }
}
