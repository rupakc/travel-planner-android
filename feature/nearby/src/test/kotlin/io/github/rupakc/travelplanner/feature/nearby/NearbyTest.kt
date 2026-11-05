package io.github.rupakc.travelplanner.feature.nearby

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.NearbyRepository
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakePreferencesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NearbyTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val compose = createComposeRule()

    private class FakeLocation(var permitted: Boolean, var fix: LocationFix = LocationFix.Found(35.0, 135.7)) : LocationClient {
        override fun hasPermission() = permitted

        override suspend fun current() = if (permitted) fix else LocationFix.NoPermission
    }

    private class RecordingNearby(var result: AppResult<NearbyResponse>) : NearbyRepository {
        var interests: List<String>? = listOf("unset")

        var remembered: Pair<Double, Double>? = null

        override suspend fun nearby(lat: Double, lng: Double, interests: List<String>?, limit: Int): AppResult<NearbyResponse> {
            this.interests = interests
            return result
        }

        override suspend fun rememberLocation(lat: Double, lng: Double) {
            remembered = lat to lng
        }

        override suspend fun lastLocation() = null

        override suspend fun forgetLocation() {
            remembered = null
        }
    }

    private val nearbyOn = FakeSettingsRepository(AppSettings(nearbyNotifications = true))

    private val kyoto = NearbyPlace(name = "Kyoto", region = "Kyoto", country = "Japan", distanceKm = 1.4)
    private val hike =
        Activity(name = "Fushimi Inari Hike", description = "Temples and street food at dawn", category = "culture", bookingUrl = "https://example.com")

    @Test
    fun `rules derive interests, why-this and labels from data only`() {
        val profile = TravellerProfile(answers = mapOf("loves" to listOf("food", "beaches"), "pace" to listOf("relaxed"), "x" to listOf("Culture")))
        assertEquals(listOf("food", "culture"), profileInterests(profile))
        assertEquals(listOf("food", "culture"), whyThis(hike, listOf("food", "culture", "nightlife")))
        assertTrue(whyThis(hike, listOf("art")).isEmpty())
        // Whole words only; a plural still counts.
        assertTrue(whyThis(Activity(name = "Artificial intelligence museum"), listOf("art")).isEmpty())
        assertEquals(listOf("art"), whyThis(Activity(name = "Street arts walk"), listOf("art")))
        assertEquals("Kyoto, Japan", placeLabel(kyoto))
        assertEquals("Portland, Maine, United States", placeLabel(NearbyPlace(name = "Portland", region = "Maine", country = "United States")))
        assertEquals("Fushimi Inari Hike, Kyoto, Japan", mapsQuery(hike, kyoto))
    }

    @Test
    fun `without permission it asks, and a grant loads suggestions with profile interests`() =
        runTest {
            val location = FakeLocation(permitted = false)
            val repo = RecordingNearby(AppResult.Success(NearbyResponse(kyoto, listOf(hike))))
            val profile = FakeProfileRepository(TravellerProfile(answers = mapOf("loves" to listOf("food"))))
            val vm = NearbyViewModel(location, repo, FakePreferencesRepository(), profile, FakeCurrentPlanStore(), nearbyOn)
            assertEquals(NearbyPhase.NeedsPermission, vm.state.value.phase)
            vm.onEvent(NearbyEvent.PermissionResult(granted = false))
            assertEquals(NearbyPhase.Denied, vm.state.value.phase)
            location.permitted = true
            vm.onEvent(NearbyEvent.PermissionResult(granted = true))
            advanceUntilIdle()
            assertEquals(NearbyPhase.Loaded, vm.state.value.phase)
            assertEquals(listOf("food"), repo.interests)
            // The foreground fix is remembered for background suggestions.
            assertEquals(35.0 to 135.7, repo.remembered)
            assertEquals(listOf("food"), vm.state.value.items.single().why)
            vm.onEvent(NearbyEvent.TogglePlan(vm.state.value.items.single().key))
            advanceUntilIdle()
            assertTrue(vm.state.value.items.single().inPlan)
        }

    @Test
    fun `the location is remembered only while nearby suggestions are on`() =
        runTest {
            val repo = RecordingNearby(AppResult.Success(NearbyResponse(kyoto, listOf(hike))))
            NearbyViewModel(FakeLocation(true), repo, FakePreferencesRepository(), FakeProfileRepository(), FakeCurrentPlanStore(), FakeSettingsRepository())
            advanceUntilIdle()
            assertNull(repo.remembered)
            NearbyViewModel(FakeLocation(true), repo, FakePreferencesRepository(), FakeProfileRepository(), FakeCurrentPlanStore(), nearbyOn)
            advanceUntilIdle()
            assertEquals(35.0 to 135.7, repo.remembered)
        }

    @Test
    fun `server interests win and are not resent`() =
        runTest {
            val repo = RecordingNearby(AppResult.Success(NearbyResponse(kyoto, listOf(hike))))
            val prefs = FakePreferencesRepository(Preferences(username = "alice", interests = listOf("culture")))
            val vm = NearbyViewModel(FakeLocation(true), repo, prefs, FakeProfileRepository(), FakeCurrentPlanStore(), FakeSettingsRepository())
            advanceUntilIdle()
            assertNull(repo.interests)
            assertEquals(listOf("culture"), vm.state.value.interests)
        }

    @Test
    fun `no place, location off and errors map to their states`() =
        runTest {
            val repo = RecordingNearby(AppResult.Failure(AppError.NotFound("We don't know a town or city near you yet.", "no_place")))
            val location = FakeLocation(true)
            val vm = NearbyViewModel(location, repo, FakePreferencesRepository(), FakeProfileRepository(), FakeCurrentPlanStore(), FakeSettingsRepository())
            advanceUntilIdle()
            assertEquals(NearbyPhase.NoPlace("We don't know a town or city near you yet."), vm.state.value.phase)
            repo.result = AppResult.Failure(AppError.RateLimited("Too many requests — please wait 30 seconds and try again.", 30))
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(NearbyPhase.Failed("Too many requests — please wait 30 seconds and try again."), vm.state.value.phase)
            location.fix = LocationFix.Off
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(NearbyPhase.LocationOff, vm.state.value.phase)
        }

    private fun show(state: NearbyUiState, onPermission: () -> Unit = {}, onSettings: () -> Unit = {}) =
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { NearbyScreen(state, {}, {}, onPermission, onSettings, {}, {}) }
        }

    @Test
    fun `permission rationale asks in context`() {
        var asked = false
        show(NearbyUiState(NearbyPhase.NeedsPermission), onPermission = { asked = true })
        compose.onNodeWithText("See things to do near you").assertIsDisplayed()
        compose.onNodeWithText("Allow location").performScrollTo().performClick()
        assertTrue(asked)
    }

    @Test
    fun `denied state offers settings`() {
        var settings = false
        show(NearbyUiState(NearbyPhase.Denied), onSettings = { settings = true })
        compose.onNodeWithText("Location is off for Voyager").assertIsDisplayed()
        compose.onNodeWithText("Open settings").performScrollTo().performClick()
        assertTrue(settings)
    }

    @Test
    fun `loaded list shows the place, why this and actions`() {
        show(NearbyUiState(NearbyPhase.Loaded, kyoto, listOf(NearbyItem("0", hike, listOf("culture"), false)), listOf("culture")))
        compose.onNodeWithText("Kyoto, Japan").assertIsDisplayed()
        compose.onNodeWithText("1.4 km from you · 1 suggestion").assertIsDisplayed()
        compose.onNodeWithText("Why this? It matches culture.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Open in Maps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add to plan").performScrollTo().assertIsDisplayed()
    }
}
