package io.github.rupakc.travelplanner.feature.search

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.testing.invoke
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.search.ExtraStop
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.TravellerCounts
import io.github.rupakc.travelplanner.core.model.navigation.SearchModes
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakeFeatureVisibility
import io.github.rupakc.travelplanner.core.testing.fake.FakePreferencesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeRecentSearchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchLauncher
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val search = FakeSearchRepository(events = emptyList())
    private val prefs =
        FakePreferencesRepository(
            Preferences(
                username = "alice",
                budgetCategory = "low",
                nationality = "Indian",
                interests = listOf("food"),
                numTravelers = 2,
                adults = 2,
                currentResidence = "Pune",
            ),
        )
    private val recent = FakeRecentSearchesRepository()
    private val plan = FakeCurrentPlanStore()
    private val launcher = FakeSearchLauncher(plan, search)
    private val visibility = FakeFeatureVisibility()
    private val profile = FakeProfileRepository()

    /** Stands in for the app scope: work launched on it outlives the ViewModel. */
    private val appScope = CoroutineScope(main.testDispatcher)

    private fun vm(route: SearchRoute = SearchRoute(), handle: SavedStateHandle = SavedStateHandle(route)) =
        SearchViewModel(handle, search, launcher, plan, prefs, recent, profile, FixedClock, visibility, appScope)

    @Test
    fun `pre-fills the form from preferences`() {
        val form = vm().state.value.form
        assertEquals("Indian", form.nationality)
        assertEquals("1000", form.budgetUsd)
        assertEquals(listOf("food"), form.interests)
        assertEquals(TravellerCounts(adults = 2), form.travellers)
        assertEquals("2026-11-12", form.departureDate)
        assertEquals("2026-11-19", form.returnDate)
    }

    @Test
    fun `submitting an empty route shows the web's required rules and starts nothing`() {
        val vm = vm()
        vm.onEvent(SearchFormEvent.Submit)
        assertEquals(listOf(SearchFormIssue.ORIGIN_REQUIRED, SearchFormIssue.DESTINATION_REQUIRED), vm.state.value.issues)
        assertTrue(search.started.isEmpty())
        // Fixing a field re-validates live.
        vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK")))
        assertEquals(listOf(SearchFormIssue.DESTINATION_REQUIRED), vm.state.value.issues)
    }

    @Test
    fun `a valid search starts the stream, saves the recent search, writes preferences back and navigates`() =
        runTest {
            val vm = vm()
            vm.effect.test {
                vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK", destination = "HND", budgetUsd = "9000")))
                vm.onEvent(SearchFormEvent.AddStop)
                vm.onEvent(SearchFormEvent.UpdateStop(0, ExtraStop("Seoul", "2")))
                vm.onEvent(SearchFormEvent.Submit)
                assertEquals(SearchEffect.SearchStarted, awaitItem())
            }
            val request = search.started.single()
            assertEquals(listOf("Seoul", "HND"), request.destinations)
            assertEquals(listOf(2, null), request.destinationNights)
            assertEquals(9000.0, request.budgetUsd)
            assertEquals("high", prefs.current.value.budgetCategory)
            assertEquals("Pune", prefs.current.value.currentResidence)
            assertEquals("JFK", recent.recent.first().single().search.origin)
        }

    @Test
    fun `the budget is read in the UI language`() =
        runTest {
            val vm = vm()
            vm.onEvent(SearchFormEvent.SetLocale(java.util.Locale.forLanguageTag("es-ES")))
            vm.effect.test {
                vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK", destination = "HND", budgetUsd = "1.500,50")))
                vm.onEvent(SearchFormEvent.Submit)
                assertEquals(SearchEffect.SearchStarted, awaitItem())
            }
            assertEquals(1500.5, search.started.single().budgetUsd)
            assertEquals("medium", prefs.current.value.budgetCategory)
        }

    @Test
    fun `a search over unsaved picks asks first, then starts a new plan for the new search`() {
        plan.attachSearch(TravelSearchRequest(origin = "NYC", destination = "Paris", departureDate = "2026-12-01", nationality = "Indian"), null)
        plan.toggle(PlanItem.HotelItem(Hotel(name = "Paris hotel")))
        val vm = vm()
        vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK", destination = "HND")))
        vm.onEvent(SearchFormEvent.Submit)
        assertTrue(vm.state.value.confirmNewPlan)
        assertTrue(search.started.isEmpty())

        vm.onEvent(SearchFormEvent.CancelNewPlan)
        assertFalse(vm.state.value.confirmNewPlan)
        assertTrue(search.started.isEmpty())
        assertEquals("Paris hotel", plan.draft.value.selections.hotel?.name)

        vm.onEvent(SearchFormEvent.Submit)
        vm.onEvent(SearchFormEvent.ConfirmNewPlan)
        val started = search.started.single()
        assertEquals("HND", started.destination)
        assertNull(plan.draft.value.selections.hotel)
        assertEquals(started.searchKey(), plan.draft.value.searchKey)
    }

    @Test
    fun `a search with nothing unsaved starts straight away through the launcher`() {
        val vm = vm()
        vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK", destination = "HND")))
        vm.onEvent(SearchFormEvent.Submit)
        assertFalse(vm.state.value.confirmNewPlan)
        assertEquals(1, launcher.launched.size)
    }

    @Test
    fun `unchanged preferences are not written back`() {
        val vm = vm()
        val before = prefs.current.value
        vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK", destination = "HND")))
        vm.onEvent(SearchFormEvent.Submit)
        assertEquals(1, search.started.size)
        assertTrue(before === prefs.current.value)
    }

    @Test
    fun `a recent search from the route fills the form, shifting past dates`() =
        runTest {
            val saved = (recent.add(RecentSearchIn("LHR", "Rome, Italy", "2026-10-01", "2026-10-05", nationality = "British")) as AppResult.Success).value
            val vm = vm(SearchRoute(recentSearchId = saved.id))
            val s = vm.state.value
            assertEquals("LHR", s.form.origin)
            assertEquals("British", s.form.nationality)
            assertEquals("2026-11-12", s.form.departureDate)
            assertEquals("2026-11-16", s.form.returnDate)
            assertEquals(RecentNotice("LHR → Rome", datesShifted = true), s.notice)
        }

    @Test
    fun `route mode opens that mode and hidden features drop their chips except the current one`() {
        visibility.state.value = setOf(FeatureId.SURPRISE_ME)
        val vm = vm(SearchRoute(mode = SearchModes.COMPARE))
        assertEquals(SearchMode.COMPARE, vm.state.value.mode)
        assertEquals(listOf(SearchMode.KNOWN, SearchMode.SURPRISE, SearchMode.COMPARE), vm.state.value.visibleModes)
        vm.onEvent(SearchFormEvent.SelectMode(SearchMode.KNOWN))
        assertEquals(listOf(SearchMode.KNOWN, SearchMode.SURPRISE), vm.state.value.visibleModes)
        visibility.state.value = FeatureId.entries.toSet()
        assertEquals(SearchMode.entries, vm.state.value.visibleModes)
    }

    @Test
    fun `removing a recent search hides it, undo brings it back, commit deletes it`() =
        runTest {
            recent.add(RecentSearchIn("A", "B", "2026-12-01"))
            val vm = vm()
            val id = vm.state.value.recent.single().id
            vm.effect.test {
                vm.onEvent(SearchFormEvent.RemoveRecent(id))
                assertEquals(SearchEffect.RecentRemoved(id, "A → B"), awaitItem())
            }
            assertTrue(vm.state.value.recent.isEmpty())
            vm.onEvent(SearchFormEvent.UndoRemoveRecent(id))
            assertEquals(1, vm.state.value.recent.size)
            vm.onEvent(SearchFormEvent.RemoveRecent(id))
            vm.onEvent(SearchFormEvent.CommitRemoveRecent(id))
            assertTrue(recent.recent.first().isEmpty())
            assertTrue(vm.state.value.recent.isEmpty())
        }

    @Test
    fun `leaving while the undo snackbar shows still deletes on the server`() =
        runTest {
            recent.add(RecentSearchIn("A", "B", "2026-12-01"))
            val store = ViewModelStore()
            val vm = ViewModelProvider.create(store, viewModelFactory { initializer { vm() } })[SearchViewModel::class]
            vm.onEvent(SearchFormEvent.RemoveRecent(vm.state.value.recent.single().id))
            store.clear() // navigated away mid-snackbar: no Undo, no Commit event ever arrives
            assertTrue(recent.recent.first().isEmpty())
        }

    @Test
    fun `undo inside the window keeps the recent search`() =
        runTest {
            recent.add(RecentSearchIn("A", "B", "2026-12-01"))
            val store = ViewModelStore()
            val vm = ViewModelProvider.create(store, viewModelFactory { initializer { vm() } })[SearchViewModel::class]
            val id = vm.state.value.recent.single().id
            vm.onEvent(SearchFormEvent.RemoveRecent(id))
            vm.onEvent(SearchFormEvent.UndoRemoveRecent(id))
            store.clear()
            assertEquals(1, recent.recent.first().size)
        }

    @Test
    fun `a double tap starts the search once`() {
        val vm = vm()
        vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(origin = "JFK", destination = "HND")))
        vm.onEvent(SearchFormEvent.Submit)
        vm.onEvent(SearchFormEvent.Submit)
        assertEquals(1, search.started.size)
    }

    @Test
    fun `the onboarding pace answer seeds the form unless something else set it`() {
        profile.profileState.value = TravellerProfile(onboardingCompleted = true, answers = mapOf("pace" to listOf("packed")))
        assertEquals("packed", vm().state.value.form.pace)
        profile.profileState.value = TravellerProfile(answers = mapOf("pace" to listOf("nonsense")))
        assertEquals("balanced", vm().state.value.form.pace)
    }

    @Test
    fun `the typed form survives process death`() {
        val handle = SavedStateHandle(SearchRoute())
        val first = vm(handle = handle)
        first.onEvent(SearchFormEvent.UpdateForm(first.state.value.form.copy(origin = "JFK", destination = "HND", nationality = "Chilean")))
        first.onEvent(SearchFormEvent.AddStop)
        first.onEvent(SearchFormEvent.UpdateStop(0, ExtraStop("Seoul", "2")))
        first.onEvent(SearchFormEvent.SelectMode(SearchMode.COMPARE))
        // A new ViewModel over the same saved state = restored after process death; preferences don't overwrite it.
        val restored = vm(handle = handle).state.value
        assertEquals("Chilean", restored.form.nationality)
        assertEquals("HND", restored.form.destination)
        assertEquals(listOf(ExtraStop("Seoul", "2")), restored.stops)
        assertEquals(SearchMode.COMPARE, restored.mode)
    }

    @Test
    fun `rerun is ignored for passed dates and runs the exact search otherwise`() {
        runBlocking { recent.add(RecentSearchIn("A", "B", "2026-01-01")) }
        val vm = vm()
        vm.onEvent(SearchFormEvent.RerunRecent(vm.state.value.recent.single().id))
        assertTrue(search.started.isEmpty())
        runBlocking { recent.add(RecentSearchIn("C", "D", "2026-12-01", nationality = "American")) }
        vm.onEvent(SearchFormEvent.RerunRecent(vm.state.value.recent.first().id))
        assertEquals("D", search.started.single().destination)
    }

    @Test
    fun `a Surprise me pick fills the known form without searching`() {
        val vm = vm()
        val discoverForm = vm.state.value.form.copy(origin = "JFK", budgetUsd = "1500", interests = listOf("nature"))
        vm.onEvent(SearchFormEvent.SelectMode(SearchMode.SURPRISE))
        vm.applyDiscovered(discoverForm, DiscoveredDestination(city = "Lisbon", country = "Portugal"))
        with(vm.state.value) {
            assertEquals(SearchMode.KNOWN, mode)
            assertEquals("Lisbon, Portugal", form.destination)
            assertEquals("JFK", form.origin)
            assertEquals(listOf("nature"), form.interests)
        }
        assertTrue(search.started.isEmpty())
    }

    @Test
    fun `planning a compared destination runs a full search with the known form's other fields`() {
        val vm = vm()
        vm.onEvent(SearchFormEvent.UpdateForm(vm.state.value.form.copy(existingVisas = listOf("US"), serendipity = 0.8)))
        val compared =
            TravelSearchRequest(
                origin = "JFK",
                destination = "Lisbon",
                departureDate = "2026-12-01",
                nationality = "American",
                budgetUsd = 2500.0,
                adults = 1,
                children = 2,
            )
        vm.planCompared(compared)
        val request = search.started.single()
        assertEquals("Lisbon", request.destination)
        assertEquals(listOf("US"), request.existingVisas)
        assertEquals(0.8, request.serendipity, 0.0)
        assertEquals(3, request.numTravelers)
        assertNull(request.returnDate)
        assertNull(request.destinations)
        assertFalse(vm.state.value.issues.isNotEmpty())
    }
}
