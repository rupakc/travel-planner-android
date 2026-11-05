package io.github.rupakc.travelplanner.feature.settings

import androidx.lifecycle.SavedStateHandle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeHealthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import io.github.rupakc.travelplanner.feature.settings.preferences.EmailStatus
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesEvent
import io.github.rupakc.travelplanner.feature.settings.preferences.PreferencesViewModel
import io.github.rupakc.travelplanner.feature.settings.preferences.SaveStatus
import io.github.rupakc.travelplanner.feature.settings.preferences.TravellerField
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PreferencesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = ScriptedPreferencesRepository()
    private val search = FakeSearchRepository().apply { nationalities = listOf(Nationality("American", "United States", "US")) }
    private val auth = FakeAuthRepository(Session("t", TestUser.copy(email = "alice@example.com")))
    private val health = FakeHealthRepository(Health(status = "ok", emailAlertsAvailable = true))

    private fun vm(handle: SavedStateHandle = SavedStateHandle()) = PreferencesViewModel(handle, repo, search, auth, health)

    @Test
    fun `loads server preferences, account email and email capability`() {
        val s = vm().state.value
        assertFalse(s.loading)
        assertEquals("American", s.prefs.nationality)
        assertEquals("alice@example.com", s.email)
        assertTrue(s.emailAlertsAvailable)
    }

    @Test
    fun `load failure shows retry and never saves defaults`() {
        repo.getResult = AppResult.Failure(AppError.Network())
        val model = vm()
        assertEquals(AppError.Network(), model.state.value.loadError)
        repo.getResult = AppResult.Success(Preferences(nationality = "Indian"))
        model.onEvent(PreferencesEvent.Retry)
        assertNull(model.state.value.loadError)
        assertEquals("Indian", model.state.value.prefs.nationality)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `edits follow the web rules and save sends num_travelers`() =
        runTest {
            val model = vm()
            model.onEvent(PreferencesEvent.SetBudget("high"))
            model.onEvent(PreferencesEvent.ChangeCount(TravellerField.CHILDREN, 1))
            model.onEvent(PreferencesEvent.ChangeCount(TravellerField.ADULTS, 1))
            model.onEvent(PreferencesEvent.ToggleInterest("food"))
            model.onEvent(PreferencesEvent.ToggleAccessibility("wheelchair"))
            model.onEvent(PreferencesEvent.AddPermits("Schengen, UK"))
            model.onEvent(PreferencesEvent.RemovePermit("UK"))
            model.onEvent(PreferencesEvent.AddVisas("US"))
            model.onEvent(PreferencesEvent.ResidenceChanged("Munich, Germany"))
            model.onEvent(PreferencesEvent.Save)
            val sent = repo.saved.single()
            assertEquals("high", sent.budgetCategory)
            assertEquals(3, sent.numTravelers)
            assertEquals(listOf("food"), sent.interests)
            assertEquals(listOf("wheelchair"), sent.accessibilityNeeds)
            assertEquals(listOf("Schengen"), sent.residencePermits)
            assertEquals(listOf("US"), sent.existingVisas)
            assertEquals("Munich, Germany", sent.currentResidence)
            assertEquals(SaveStatus.Saved, model.state.value.saveStatus)
            advanceTimeBy(3_500)
            runCurrent()
            assertNull(model.state.value.saveStatus)
        }

    @Test
    fun `save error keeps the form and shows the backend detail`() {
        repo.saveResult = { AppResult.Failure(AppError.Client(422, "budget_category is invalid")) }
        val model = vm()
        model.onEvent(PreferencesEvent.ToggleInterest("art"))
        model.onEvent(PreferencesEvent.Save)
        val s = model.state.value
        assertEquals("budget_category is invalid", (s.saveStatus as SaveStatus.Failed).error.message)
        assertEquals(listOf("art"), s.prefs.interests)
        assertFalse(s.saving)
    }

    @Test
    fun `nationality suggestions are debounced and picking fills the field`() =
        runTest {
            val model = vm()
            model.onEvent(PreferencesEvent.NationalityChanged("Am"))
            assertTrue(model.state.value.nationalitySuggestions.isEmpty())
            advanceTimeBy(250)
            runCurrent()
            val suggestion = model.state.value.nationalitySuggestions.single()
            model.onEvent(PreferencesEvent.NationalityPicked(suggestion))
            assertEquals("American", model.state.value.prefs.nationality)
            assertTrue(model.state.value.nationalitySuggestions.isEmpty())
        }

    @Test
    fun `email is validated before saving and saved on its own`() {
        val model = vm()
        model.onEvent(PreferencesEvent.EmailChanged("not-an-email"))
        model.onEvent(PreferencesEvent.SaveEmail)
        assertEquals(EmailStatus.Invalid, model.state.value.emailStatus)
        auth.emailResult = AppResult.Success("new@example.com")
        model.onEvent(PreferencesEvent.EmailChanged("new@example.com"))
        model.onEvent(PreferencesEvent.SaveEmail)
        assertEquals(EmailStatus.Saved, model.state.value.emailStatus)
        assertEquals("new@example.com", model.state.value.email)
        assertTrue(repo.saved.isEmpty())
    }

    @Test
    fun `an unsaved draft survives process death`() {
        val handle = SavedStateHandle()
        vm(handle).onEvent(PreferencesEvent.ToggleInterest("nature"))
        repo.getResult = AppResult.Failure(AppError.Network())
        val restored = vm(handle).state.value
        assertEquals(listOf("nature"), restored.prefs.interests)
        assertNull(restored.loadError)
    }
}
