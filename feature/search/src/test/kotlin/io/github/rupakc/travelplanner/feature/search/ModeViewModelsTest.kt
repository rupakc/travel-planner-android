package io.github.rupakc.travelplanner.feature.search

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.CompareRepository
import io.github.rupakc.travelplanner.core.domain.search.CompareErrorReason
import io.github.rupakc.travelplanner.core.domain.search.CompareRow
import io.github.rupakc.travelplanner.core.domain.search.ImportAction
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreviewSource
import io.github.rupakc.travelplanner.core.model.importlink.PreviewDestination
import io.github.rupakc.travelplanner.core.model.importlink.PreviewPlace
import io.github.rupakc.travelplanner.core.model.importlink.ResolvedPlace
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.CompareEstimate
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.search.DiscoveryResponse
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeDiscoverRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeImportRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePreferencesRepository
import io.github.rupakc.travelplanner.feature.search.compare.CompareColumn
import io.github.rupakc.travelplanner.feature.search.compare.CompareEvent
import io.github.rupakc.travelplanner.feature.search.compare.CompareIssue
import io.github.rupakc.travelplanner.feature.search.compare.CompareViewModel
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverEvent
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverViewModel
import io.github.rupakc.travelplanner.feature.search.importlink.ImportEffect
import io.github.rupakc.travelplanner.feature.search.importlink.ImportEvent
import io.github.rupakc.travelplanner.feature.search.importlink.ImportSourceKind
import io.github.rupakc.travelplanner.feature.search.importlink.ImportViewModel
import io.github.rupakc.travelplanner.feature.search.importlink.TownProblem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class DiscoverViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakeDiscoverRepository()
    private val vm = DiscoverViewModel(repo, FakePreferencesRepository(Preferences(nationality = "American", interests = listOf("art"))), FixedClock)

    @Test
    fun `needs origin and nationality, then shows the picks`() {
        assertEquals("American", vm.state.value.form.nationality)
        assertFalse(vm.state.value.canSubmit)
        repo.result = AppResult.Success(DiscoveryResponse(listOf(DiscoveredDestination(city = "Lisbon", country = "Portugal"))))
        vm.onEvent(DiscoverEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK")))
        vm.onEvent(DiscoverEvent.Submit)
        assertEquals("Lisbon", vm.state.value.destinations?.single()?.city)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `a seniors-only party still sends one adult (backend adults ge 1)`() {
        assertEquals(
            1,
            io.github.rupakc.travelplanner.core.domain.search.toDiscoveryRequest(
                SearchForm(travellers = io.github.rupakc.travelplanner.core.domain.search.TravellerCounts(adults = 0, seniors = 2)),
            ).adults,
        )
    }

    @Test
    fun `an empty answer and a failure are distinct states`() {
        vm.onEvent(DiscoverEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK")))
        vm.onEvent(DiscoverEvent.Submit)
        assertEquals(emptyList<DiscoveredDestination>(), vm.state.value.destinations)
        repo.result = AppResult.Failure(AppError.Server(504, "Discovery timed out — please try again"))
        vm.onEvent(DiscoverEvent.Submit)
        assertNull(vm.state.value.destinations)
        assertEquals("Discovery timed out — please try again", vm.state.value.error?.message)
    }
}

/** Compare repository answering per destination, optionally held until [release]. */
private class ScriptedCompare : CompareRepository {
    val answers = mutableMapOf<String, AppResult<CompareSummary>>()
    var gate: CompletableDeferred<Unit>? = null
    val calls = mutableListOf<String>()

    override suspend fun compare(request: TravelSearchRequest): AppResult<CompareSummary> {
        calls += request.destination
        gate?.await()
        return answers[request.destination] ?: AppResult.Success(CompareSummary(destination = request.destination))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CompareViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = ScriptedCompare()
    private val vm = CompareViewModel(repo, FixedClock)
    private val known = SearchForm.initial(TestToday).copy(origin = "JFK", destination = "Lisbon", nationality = "American", budgetUsd = "3000")

    @Test
    fun `seeds once from the known form`() {
        vm.seed(known)
        assertEquals(listOf("Lisbon", ""), vm.state.value.destinations)
        assertEquals("", vm.state.value.form?.destination)
        vm.seed(known.copy(origin = "LHR"))
        assertEquals("JFK", vm.state.value.form?.origin)
    }

    @Test
    fun `late preferences fill the shared fields until the user edits them`() {
        vm.seed(SearchForm.initial(TestToday))
        vm.applyPreferences(Preferences(nationality = "Indian"))
        assertEquals("Indian", vm.state.value.form?.nationality)
        vm.onEvent(CompareEvent.UpdateForm(vm.state.value.form!!.copy(nationality = "Chilean")))
        vm.applyPreferences(Preferences(nationality = "Indian"))
        assertEquals("Chilean", vm.state.value.form?.nationality)
    }

    @Test
    fun `needs two different destinations`() {
        vm.seed(known)
        vm.onEvent(CompareEvent.UpdateDestination(1, " lisbon "))
        vm.onEvent(CompareEvent.Run)
        assertEquals(CompareIssue.NEED_TWO_DESTINATIONS, vm.state.value.issue)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun `runs one call per destination, marks best, and retries a failed column`() {
        vm.seed(known)
        vm.onEvent(CompareEvent.UpdateDestination(1, "Porto"))
        vm.onEvent(CompareEvent.AddDestination)
        vm.onEvent(CompareEvent.UpdateDestination(2, "Madrid"))
        repo.answers["Lisbon"] = AppResult.Success(CompareSummary(estimate = CompareEstimate(totalUsd = 1800.0)))
        repo.answers["Porto"] = AppResult.Success(CompareSummary(estimate = CompareEstimate(totalUsd = 1500.0)))
        repo.answers["Madrid"] = AppResult.Failure(AppError.Server(502, null))
        vm.onEvent(CompareEvent.Run)
        val columns = vm.state.value.columns.orEmpty()
        assertEquals(listOf(CompareColumn.Status.DONE, CompareColumn.Status.DONE, CompareColumn.Status.ERROR), columns.map { it.status })
        assertEquals(CompareErrorReason.Unavailable, columns[2].error)
        assertEquals(setOf(1), vm.state.value.best[CompareRow.ESTIMATE])
        repo.answers["Madrid"] = AppResult.Success(CompareSummary(label = "Madrid, Spain"))
        vm.onEvent(CompareEvent.Retry(2))
        assertEquals(CompareColumn.Status.DONE, vm.state.value.columns!![2].status)
        assertEquals(listOf("Lisbon", "Porto", "Madrid", "Madrid"), repo.calls)
        assertEquals("Porto", vm.requestAt(1)?.destination)
        assertEquals(3000.0, vm.requestAt(1)?.budgetUsd)
    }

    @Test
    fun `columns show loading while their calls are in flight`() {
        repo.gate = CompletableDeferred()
        vm.seed(known)
        vm.onEvent(CompareEvent.UpdateDestination(1, "Porto"))
        vm.onEvent(CompareEvent.Run)
        assertTrue(vm.state.value.loading)
        repo.gate?.complete(Unit)
        assertFalse(vm.state.value.loading)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ImportViewModelTest {
    @get:Rule val main = MainDispatcherRule(StandardTestDispatcher())

    private val repo = FakeImportRepository()
    private val vm = ImportViewModel(repo, FakeScreenshotReader(), FixedClock)
    private val known = SearchForm.initial(TestToday).copy(origin = "LHR", nationality = "British", interests = listOf("history"))

    private val preview =
        ImportPreview(
            source = ImportPreviewSource(kind = "url", url = "https://wander.example/lisbon"),
            title = "48 hours in Lisbon",
            destinations = listOf(PreviewDestination("Lisbon, Portugal", "Lisbon", "Portugal", nights = 2, placeCount = 1)),
            places = listOf(PreviewPlace("p1", "Time Out Market", "food", "Lisbon", destination = "Lisbon, Portugal")),
            unresolved = listOf(PreviewPlace("p2", "Mystery Bar", "drink")),
            suggestedDays = 2,
            interests = listOf("food"),
        )

    @Test
    fun `a deep-linked url is read straight away and the review opens a month out`() =
        runTest(main.testDispatcher) {
            repo.extractResult = AppResult.Success(preview)
            vm.seed(known, "https://wander.example/lisbon")
            assertEquals(ImportSourceKind.URL, vm.state.value.busy?.kind)
            runCurrent()
            val s = vm.state.value
            assertNull(s.busy)
            assertNotNull(s.draft)
            assertEquals("2026-12-05", s.trip.departureDate)
            assertNull(s.planIssue)
        }

    @Test
    fun `progress steps follow the web timings and cancel stops the call`() =
        runTest(main.testDispatcher) {
            val gate = CompletableDeferred<Unit>()
            val slow =
                object : io.github.rupakc.travelplanner.core.data.repository.ImportRepository by repo {
                    override suspend fun extract(request: io.github.rupakc.travelplanner.core.model.importlink.ImportRequest): AppResult<ImportPreview> {
                        gate.await()
                        return AppResult.Success(preview)
                    }
                }
            val vm = ImportViewModel(slow, FakeScreenshotReader(), FixedClock)
            vm.onEvent(ImportEvent.TextChanged("Belém Tower and LX Factory"))
            vm.onEvent(ImportEvent.Submit)
            assertEquals(ImportSourceKind.TEXT, vm.state.value.busy?.kind)
            advanceTimeBy(2600)
            assertEquals(1, vm.state.value.busy?.step)
            advanceTimeBy(4500)
            assertEquals(2, vm.state.value.busy?.step)
            assertEquals(7, vm.state.value.busy?.elapsedSeconds)
            vm.onEvent(ImportEvent.Cancel)
            assertNull(vm.state.value.busy)
            gate.complete(Unit)
            runCurrent()
            assertNull(vm.state.value.preview)
        }

    @Test
    fun `failures offer the web's next steps`() =
        runTest(main.testDispatcher) {
            repo.extractResult = AppResult.Failure(AppError.Client(422, "Paywalled.", code = "paywall"))
            vm.onEvent(ImportEvent.TextChanged("https://news.example/story"))
            vm.onEvent(ImportEvent.Submit)
            runCurrent()
            assertEquals(listOf(ImportAction.TEXT, ImportAction.IMAGES), vm.state.value.error?.actions)
            vm.effect.test {
                vm.onEvent(ImportEvent.RunAction(ImportAction.IMAGES))
                assertEquals(ImportEffect.PickImages, awaitItem())
            }
            vm.onEvent(ImportEvent.RunAction(ImportAction.TEXT))
            assertEquals("", vm.state.value.text)
        }

    @Test
    fun `screenshots win over text and are capped at four`() =
        runTest(main.testDispatcher) {
            vm.onEvent(ImportEvent.TextChanged("some text"))
            vm.onEvent(ImportEvent.AddShots(List(5) { Uri.parse("content://shots/$it") }))
            runCurrent()
            assertEquals(4, vm.state.value.shots.size)
            assertEquals(4, vm.state.value.keptShots)
            assertEquals(ImportSourceKind.IMAGES, vm.state.value.sourceKind)
        }

    @Test
    fun `review edits feed the planned search with must_include and import_source`() =
        runTest(main.testDispatcher) {
            repo.extractResult = AppResult.Success(preview)
            vm.seed(known, "https://wander.example/lisbon")
            runCurrent()
            vm.onEvent(ImportEvent.SetPlaceStop("p2", "Lisbon, Portugal"))
            vm.onEvent(ImportEvent.ChangeNights("Lisbon, Portugal", 1))
            vm.effect.test {
                vm.onEvent(ImportEvent.Plan)
                val ready = awaitItem() as ImportEffect.PlanReady
                val form = ready.search.form
                assertEquals("Lisbon, Portugal", form.destination)
                assertEquals(listOf("Time Out Market", "Mystery Bar"), form.mustInclude?.map { it.name })
                assertEquals("https://wander.example/lisbon", form.importSource?.url)
                assertEquals("2026-12-08", form.returnDate)
                assertEquals(listOf("history", "food"), form.interests)
            }
        }

    @Test
    fun `placing an unresolved place by town adds it, or explains the 3-stop limit`() =
        runTest(main.testDispatcher) {
            repo.extractResult =
                AppResult.Success(
                    preview.copy(
                        destinations =
                            listOf("Lisbon", "Porto", "Faro").map { PreviewDestination("$it, Portugal", it, "Portugal", nights = 1) },
                    ),
                )
            vm.seed(known, "https://wander.example/lisbon")
            runCurrent()
            repo.resolveResult =
                AppResult.Success(
                    ResolvedPlace(PreviewPlace("p2", "Mystery Bar", "drink", "Évora"), PreviewDestination("Évora, Portugal", "Évora", "Portugal")),
                )
            vm.onEvent(ImportEvent.TownChanged("p2", "Évora"))
            vm.onEvent(ImportEvent.PlaceIt("p2"))
            runCurrent()
            assertEquals(TownProblem.TooManyStops("Évora, Portugal"), vm.state.value.towns["p2"]?.problem)
            repo.resolveResult =
                AppResult.Success(
                    ResolvedPlace(
                        PreviewPlace("p2", "Mystery Bar", "drink", "Porto", destination = "Porto, Portugal"),
                        PreviewDestination("Porto, Portugal", "Porto", "Portugal"),
                    ),
                )
            vm.onEvent(ImportEvent.PlaceIt("p2"))
            runCurrent()
            assertTrue(vm.state.value.draft!!.unresolved.isEmpty())
            assertEquals("Mystery Bar", vm.state.value.draft!!.places.last().place.name)
        }
}
