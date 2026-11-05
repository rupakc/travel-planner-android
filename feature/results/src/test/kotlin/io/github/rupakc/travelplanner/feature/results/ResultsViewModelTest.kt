package io.github.rupakc.travelplanner.feature.results

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.cash.turbine.turbineScope
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
import io.github.rupakc.travelplanner.core.model.section.Hotel
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
import io.github.rupakc.travelplanner.core.testing.fake.FakeFeatureVisibility
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePriceWatchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchLauncher
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
    private val visibility = FakeFeatureVisibility()

    private fun vm(store: io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore = plan) =
        ResultsViewModel(
            search = search,
            searchLauncher = FakeSearchLauncher(store, search),
            plan = store,
            health = health,
            fareCalendar = FakeFareCalendarRepository(),
            fareWatches = fareWatches,
            priceWatches = priceWatches,
            featureVisibility = visibility,
            clock = clock,
            dispatchers = TestAppDispatchers(main.testDispatcher),
            savedState = SavedStateHandle(),
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
    fun `a draft request without results shows Run this search and never streams on its own`() =
        runTest {
            plan.restore(PlanDraft(planId = 7, searchRequest = request))
            val vm = vm()
            vm.state.test {
                val s = expectMostRecentItem()
                assertTrue(s.canRunSavedSearch)
                assertEquals(ResultsSource.RUN_SAVED, s.source)
                assertTrue(s.sections.all { it.notLoaded })
                assertTrue(search.started.isEmpty())
                vm.onEvent(ResultsEvent.RunSavedSearch)
                assertEquals(listOf(request), search.started)
                val after = expectMostRecentItem()
                assertFalse(after.canRunSavedSearch)
                assertEquals(StreamPhase.DONE, after.phase)
                assertEquals(ResultsSource.LIVE, after.source)
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
        val hotels = kotlinx.serialization.json.JsonObject(mapOf("hotels" to kotlinx.serialization.json.JsonObject(emptyMap())))
        val other = request.copy(destination = "Rome")
        val idle = io.github.rupakc.travelplanner.core.model.search.SearchResultsState()
        val streaming = idle.copy(request = request, phase = StreamPhase.STREAMING)
        val done = streaming.copy(phase = StreamPhase.DONE)
        assertEquals(ResultsSource.NONE, ResultsStateBuilder.selectSource(idle, PlanDraft()))
        assertEquals(ResultsSource.LIVE, ResultsStateBuilder.selectSource(streaming, PlanDraft()))
        assertEquals(ResultsSource.LIVE, ResultsStateBuilder.selectSource(done, PlanDraft(searchRequest = request)))
        assertEquals(ResultsSource.LIVE, ResultsStateBuilder.selectSource(streaming, PlanDraft(planId = 1, searchRequest = request, searchData = hotels)))
        assertEquals(ResultsSource.SNAPSHOT, ResultsStateBuilder.selectSource(done, PlanDraft(planId = 1, searchRequest = other, searchData = hotels)))
        assertEquals(ResultsSource.SNAPSHOT, ResultsStateBuilder.selectSource(streaming, PlanDraft(searchRequest = other, searchData = hotels)))
        assertEquals(ResultsSource.SNAPSHOT, ResultsStateBuilder.selectSource(idle, PlanDraft(searchRequest = request, searchData = hotels)))
        assertEquals(ResultsSource.RUN_SAVED, ResultsStateBuilder.selectSource(done, PlanDraft(planId = 1, searchRequest = other)))
        assertEquals(ResultsSource.RUN_SAVED, ResultsStateBuilder.selectSource(idle, PlanDraft(searchRequest = request)))
    }

    @Test
    fun `finished stream A then open saved plan B without stored results shows B and never writes A into it`() =
        runTest {
            search.start(request)
            val b = request.copy(destination = "Lisbon")
            val vm = vm()
            vm.state.test {
                expectMostRecentItem()
                plan.restore(PlanDraft(planId = 11, name = "Lisbon trip", searchRequest = b))
                val s = expectMostRecentItem()
                assertEquals("Lisbon", s.request?.destination)
                assertTrue(s.canRunSavedSearch)
                assertNull(s.section(SectionId.HOTELS).data)
                assertEquals(b, plan.draft.value.searchRequest)
                assertNull(plan.draft.value.searchData)
                assertEquals(1, search.started.size)
            }
        }

    @Test
    fun `plan saved from chat with a request only after another trip streamed shows Run this search`() =
        runTest {
            search.start(request)
            val chat = request.copy(destination = "Kyoto")
            plan.attachSearch(chat, null)
            plan.save("Kyoto from chat")
            val vm = vm()
            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(ResultsSource.RUN_SAVED, s.source)
                assertEquals("Kyoto", s.request?.destination)
                assertEquals(chat, plan.draft.value.searchRequest)
                assertNull(plan.draft.value.searchData)
            }
        }

    @Test
    fun `process death mid-stream restores the stored sections and marks the rest not loaded`() =
        runTest {
            val partial =
                Fixtures.searchEvents(Fixtures.SEARCH_FULL).filterIsInstance<SearchEvent.Section>().filter {
                    it.id == SectionId.FLIGHTS ||
                        it.id == SectionId.VISA
                }
            val data = kotlinx.serialization.json.JsonObject(partial.associate { it.id.wire to it.raw })
            plan.restore(PlanDraft(searchRequest = request, searchData = data))
            val vm = vm()
            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(ResultsSource.SNAPSHOT, s.source)
                assertNotNull(s.section(SectionId.FLIGHTS).data)
                assertFalse(s.section(SectionId.FLIGHTS).notLoaded)
                assertTrue(s.section(SectionId.HOTELS).notLoaded)
                assertTrue(search.started.isEmpty())
            }
        }

    @Test
    fun `a filter result for the previous search is dropped after a remix`() =
        runTest {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val slow =
                object : io.github.rupakc.travelplanner.core.data.repository.SearchRepository by search {
                    override suspend fun hotelsFiltered(request: TravelSearchRequest, filters: HotelFilters): AppResult<HotelsData> {
                        gate.await()
                        return AppResult.Success(HotelsData(results = listOf(io.github.rupakc.travelplanner.core.model.section.Hotel(name = "Stale Inn"))))
                    }
                }
            val vm =
                ResultsViewModel(
                    slow,
                    FakeSearchLauncher(plan, slow),
                    plan,
                    health,
                    FakeFareCalendarRepository(),
                    fareWatches,
                    priceWatches,
                    FakeFeatureVisibility(),
                    clock,
                    TestAppDispatchers(main.testDispatcher),
                    SavedStateHandle(),
                )
            vm.state.test {
                search.start(request)
                vm.onEvent(ResultsEvent.ApplyHotelFilters(HotelFilters(numBeds = 2)))
                assertTrue(expectMostRecentItem().section(SectionId.HOTELS).filterLoading)
                vm.onEvent(ResultsEvent.Remix(RemixPreset.WEEK_LATER))
                gate.complete(Unit)
                val s = expectMostRecentItem()
                assertEquals("2026-12-08", s.request?.departureDate)
                assertFalse((s.section(SectionId.HOTELS).data as HotelsData).results.any { it.name == "Stale Inn" })
                assertFalse(s.section(SectionId.HOTELS).filterLoading)
            }
        }

    @Test
    fun `Travel Ease and the pricing advisor survive process death via saved state`() =
        runTest {
            val saved = SavedStateHandle()
            val first =
                ResultsViewModel(
                    search,
                    FakeSearchLauncher(plan, search),
                    plan,
                    health,
                    FakeFareCalendarRepository(),
                    fareWatches,
                    priceWatches,
                    FakeFeatureVisibility(),
                    clock,
                    TestAppDispatchers(main.testDispatcher),
                    saved,
                )
            first.state.test {
                search.start(request)
                assertEquals(82, expectMostRecentItem().confidence?.total)
            }
            // New process: the stream is gone, the draft and the saved state remain.
            val draft = plan.draft.value
            val newPlan = FakeCurrentPlanStore().apply { restore(draft) }
            val restored =
                ResultsViewModel(
                    FakeSearchRepository(),
                    FakeSearchLauncher(newPlan),
                    newPlan,
                    health,
                    FakeFareCalendarRepository(),
                    fareWatches,
                    priceWatches,
                    FakeFeatureVisibility(),
                    clock,
                    TestAppDispatchers(main.testDispatcher),
                    saved,
                )
            restored.state.test {
                val s = expectMostRecentItem()
                assertEquals(ResultsSource.SNAPSHOT, s.source)
                assertEquals(82, s.confidence?.total)
                assertNotNull(s.pricingAdvisor)
            }
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
    fun `two Results screens share one filter result and stop writing the draft (single writer)`() =
        runTest {
            search.hotelsResult =
                AppResult.Success(
                    SectionDecoder.decode(SectionId.HOTELS, Json.parseToJsonElement("""{"results":[{"name":"Cheap Inn"}]}""").jsonObject) as HotelsData,
                )
            var attaches = 0
            val counting =
                object : io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStore by plan {
                    override fun attachSearch(request: TravelSearchRequest, data: kotlinx.serialization.json.JsonObject?) {
                        attaches++
                        plan.attachSearch(request, data)
                    }
                }
            val first = vm(counting)
            val second = vm(counting) // e.g. Results kept alive on another tab's back stack
            turbineScope {
                val a = first.state.testIn(backgroundScope)
                val b = second.state.testIn(backgroundScope)
                search.start(request)
                first.onEvent(ResultsEvent.ApplyHotelFilters(HotelFilters(numBeds = 2)))
                assertEquals(listOf("Cheap Inn"), (a.expectMostRecentItem().section(SectionId.HOTELS).data as HotelsData).results.map { it.name })
                assertEquals(listOf("Cheap Inn"), (b.expectMostRecentItem().section(SectionId.HOTELS).data as HotelsData).results.map { it.name })
                val settled = attaches
                assertTrue("attachSearch ran $settled times", settled <= 4)
                // Unrelated changes (a toggle, typing in the filter box) do not write the draft again.
                first.onEvent(ResultsEvent.Query("inn"))
                plan.toggle(PlanItem.HotelItem(Hotel(name = "Cheap Inn")))
                a.expectMostRecentItem()
                b.expectMostRecentItem()
                assertEquals(settled, attaches)
                val stored = plan.draft.value.searchData!!["hotels"]!!.jsonObject["results"] as kotlinx.serialization.json.JsonArray
                assertEquals("\"Cheap Inn\"", stored.single().jsonObject["name"].toString())
                a.cancel()
                b.cancel()
            }
        }

    @Test
    fun `a new search from Search replaces a draft that holds another trip's sections and Results shows it live`() =
        runTest {
            val paris = request.copy(destination = "Paris")
            plan.attachSearch(paris, Json.parseToJsonElement("""{"hotels":{"results":[{"name":"Paris hotel"}]}}""").jsonObject)
            plan.toggle(PlanItem.HotelItem(Hotel(name = "Paris hotel")))
            val vm = vm()
            vm.state.test {
                assertEquals("Paris", expectMostRecentItem().request?.destination)
                FakeSearchLauncher(plan, search).startNewSearch(request) // what Search submit does
                val s = expectMostRecentItem()
                assertEquals("Tokyo", s.request?.destination)
                assertEquals(ResultsSource.LIVE, ResultsStateBuilder.selectSource(search.current.value, plan.draft.value))
                assertFalse((s.section(SectionId.HOTELS).data as HotelsData).results.any { it.name == "Paris hotel" })
                assertNull(plan.draft.value.selections.hotel)
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
            visibility.state.value = setOf(FeatureId.VISA_CHECKER)
            val vm = vm()
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
    fun `a skipped-quiz user who turned Events off gets Events collapsed and last, everything else relevant`() =
        runTest {
            // The shared rule (core:data) for no traveller type: every feature minus explicit disables.
            visibility.state.value = FeatureId.entries.toSet() - FeatureId.EVENTS
            val vm = vm()
            vm.state.test {
                search.start(request)
                val s = expectMostRecentItem()
                assertFalse(s.section(SectionId.EVENTS).relevant)
                assertTrue(s.sections.filter { it.id != SectionId.EVENTS }.all { it.relevant })
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
