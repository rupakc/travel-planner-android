package io.github.rupakc.travelplanner.feature.nearby

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.live.LiveFailure
import io.github.rupakc.travelplanner.core.model.grounding.GroundingSource
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlaceItem
import io.github.rupakc.travelplanner.core.model.nearby.NearbyResponse
import io.github.rupakc.travelplanner.core.model.nearby.NearbySection
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeNearbyRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.CompletableDeferred
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

    private class FakeLocation(var permitted: Boolean, var fix: LocationFix = LocationFix.Found(38.72, -9.14)) : LocationClient {
        /** When set, [current] waits for it (to observe the Locating phase). */
        var gate: CompletableDeferred<Unit>? = null

        override fun hasPermission() = permitted

        override suspend fun current(): LocationFix {
            gate?.await()
            return if (permitted) fix else LocationFix.NoPermission
        }
    }

    private val nearbyOn = FakeSettingsRepository(AppSettings(nearbyNotifications = true))

    private val lisbon = NearbyPlace(name = "Lisbon", country = "Portugal", distanceKm = 0.4)
    private val market = NearbyPlaceItem("Time Out Market", "Food hall with local chefs", "https://maps.google.com/?cid=1", "ChIJ1", "eat")
    private val v2 =
        NearbyResponse(
            place = lisbon,
            sections =
                listOf(
                    NearbySection("eat", "Places to eat", listOf(market, NearbyPlaceItem(name = " "), market.copy(summary = null))),
                    NearbySection("see", "Places to see", listOf(NearbyPlaceItem("Castelo", null, "javascript:alert(1)", null))),
                    NearbySection("nightlife", "Bars", listOf(NearbyPlaceItem("Bar", null, "https://maps.google.com/?cid=9"))),
                ),
            attribution = "Google Maps",
            sources = listOf(GroundingSource("Time Out Market", "https://maps.google.com/?cid=1"), GroundingSource("bad", "intent://x")),
        )

    @Test
    fun `sections are always the four, in order, with checked links and unique keys`() {
        val sections = sectionsOf(v2)
        assertEquals(listOf("see", "eat", "shop", "stay"), sections.map { it.id })
        assertNull(sections[0].cards.single().mapsUri)
        val eat = sections[1].cards
        assertEquals(listOf("Time Out Market", "Time Out Market"), eat.map { it.name })
        assertEquals(eat.size, eat.map { it.key }.toSet().size)
        assertEquals("https://maps.google.com/?cid=1", eat.first().mapsUri)
        assertTrue(sections[2].cards.isEmpty() && sections[3].cards.isEmpty())
        // A v1-shaped answer (no sections) gives four empty sections.
        assertTrue(sectionsOf(NearbyResponse(place = lisbon)).all { it.cards.isEmpty() })
        assertEquals("Lisbon, Portugal", placeLabel(lisbon))
        assertEquals("Portland, Maine, United States", placeLabel(NearbyPlace(name = "Portland", region = "Maine", country = "United States")))
        assertEquals("Castelo, Lisbon, Portugal", mapsQuery("Castelo", lisbon))
    }

    @Test
    fun `without permission it asks, and a grant loads v2 sections into memory`() =
        runTest {
            val location = FakeLocation(permitted = false)
            val repo = FakeNearbyRepository(AppResult.Success(v2))
            val vm = NearbyViewModel(location, repo, nearbyOn)
            assertEquals(NearbyPhase.NeedsPermission, vm.state.value.phase)
            vm.onEvent(NearbyEvent.PermissionResult(granted = false))
            assertEquals(NearbyPhase.Denied, vm.state.value.phase)
            location.permitted = true
            vm.onEvent(NearbyEvent.PermissionResult(granted = true))
            advanceUntilIdle()
            val s = vm.state.value
            assertEquals(NearbyPhase.Loaded, s.phase)
            assertEquals(1, repo.requests.size)
            assertEquals(38.72, repo.requests.single().lat, 0.0)
            assertEquals(6, repo.requests.single().limit)
            assertEquals(lisbon, s.place)
            assertEquals("Google Maps", s.attribution)
            // Only http(s) sources are listed.
            assertEquals(listOf("https://maps.google.com/?cid=1"), s.sources.map { it.uri })
            // The foreground fix is remembered (as a cell) only for the background invitation.
            assertTrue(repo.last != null)
        }

    @Test
    fun `refresh always asks the server again (no cache)`() =
        runTest {
            val repo = FakeNearbyRepository(AppResult.Success(v2))
            val vm = NearbyViewModel(FakeLocation(true), repo, FakeSettingsRepository())
            advanceUntilIdle()
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(2, repo.requests.size)
            // Not remembered while nearby suggestions are off.
            assertNull(repo.last)
        }

    @Test
    fun `phases follow with Turbine`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val location = FakeLocation(false).apply { this.gate = gate }
            val vm = NearbyViewModel(location, FakeNearbyRepository(AppResult.Success(v2)), FakeSettingsRepository())
            vm.state.test {
                assertEquals(NearbyPhase.NeedsPermission, awaitItem().phase)
                location.permitted = true
                vm.onEvent(NearbyEvent.PermissionResult(granted = true))
                assertEquals(NearbyPhase.Locating, awaitItem().phase)
                gate.complete(Unit)
                var next = awaitItem()
                if (next.phase == NearbyPhase.Loading) next = awaitItem()
                assertEquals(NearbyPhase.Loaded, next.phase)
                assertEquals(4, next.sections.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no place, rate limit, AI limit, offline and location off map to their states`() =
        runTest {
            val repo = FakeNearbyRepository(AppResult.Failure(AppError.NotFound("We don't know a town or city near you yet.", "no_place")))
            val location = FakeLocation(true)
            val vm = NearbyViewModel(location, repo, FakeSettingsRepository())
            advanceUntilIdle()
            assertEquals(NearbyPhase.NoPlace("We don't know a town or city near you yet."), vm.state.value.phase)
            repo.result = AppResult.Failure(AppError.RateLimited("Too many requests — please wait 30 seconds and try again.", 30))
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(NearbyPhase.Failed("Too many requests — please wait 30 seconds and try again.", LiveFailure.RATE_LIMITED), vm.state.value.phase)
            repo.result = AppResult.Failure(AppError.Server(503, "AI limit reached for this month — try again on the 1st.", "ai_limit"))
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(LiveFailure.AI_LIMIT, (vm.state.value.phase as NearbyPhase.Failed).failure)
            repo.result = AppResult.Failure(AppError.Server(503, null, "grounding_unavailable"))
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(NearbyPhase.Failed(null, LiveFailure.GROUNDING_UNAVAILABLE), vm.state.value.phase)
            repo.result = AppResult.Failure(AppError.Network("Unable to resolve host"))
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(NearbyPhase.Failed(null, LiveFailure.OFFLINE), vm.state.value.phase)
            assertTrue(vm.state.value.sections.isEmpty())
            location.fix = LocationFix.Off
            vm.onEvent(NearbyEvent.Refresh)
            advanceUntilIdle()
            assertEquals(NearbyPhase.LocationOff, vm.state.value.phase)
        }

    private fun show(
        state: NearbyUiState,
        onPermission: () -> Unit = {},
        onSettings: () -> Unit = {},
        onMaps: (NearbyCard) -> Unit = {},
        onSource: (String) -> Unit = {},
    ) = compose.setContent {
        TravelTheme(themeMode = ThemeMode.LIGHT) { NearbyScreen(state, {}, {}, onPermission, onSettings, {}, onMaps, onSource) }
    }

    @Test
    fun `permission rationale asks in context`() {
        var asked = false
        show(NearbyUiState(NearbyPhase.NeedsPermission), onPermission = { asked = true })
        compose.onNodeWithText("See what’s near you").assertIsDisplayed()
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
    fun `loaded screen shows four sections, empty states, attribution, maps button and sources`() {
        var opened: NearbyCard? = null
        var source: String? = null
        val state =
            NearbyUiState(
                NearbyPhase.Loaded,
                lisbon,
                sectionsOf(v2),
                "Google Maps",
                listOf(GroundingSource("Time Out Market source", "https://maps.google.com/?cid=1")),
            )
        show(state, onMaps = { opened = it }, onSource = { source = it })
        compose.onNodeWithText("Lisbon, Portugal").assertIsDisplayed()
        compose.onNodeWithText("0.4 km from you").assertIsDisplayed()
        compose.onNodeWithText("Places from Google Maps").assertIsDisplayed()
        compose.onNodeWithText("Places to see").assertIsDisplayed()
        scrollTo(hasText("Food hall with local chefs"))
        scrollTo(hasContentDescription("Open Time Out Market in Google Maps"))
        compose.onAllNodes(hasContentDescription("Open Time Out Market in Google Maps"))[0].performClick()
        assertEquals("Time Out Market", opened?.name)
        assertEquals("https://maps.google.com/?cid=1", opened?.mapsUri)
        scrollTo(hasText("Places to stay"))
        compose.onNodeWithText("Places to stay").assertIsDisplayed()
        compose.onAllNodesWithText("Nothing found nearby for this one.")[0].assertExists()
        scrollTo(hasText("Time Out Market source"))
        compose.onNodeWithText("Time Out Market source").performClick()
        assertEquals("https://maps.google.com/?cid=1", source)
    }

    @Test
    fun `failures show the backend words, or our copy when there are none`() {
        show(NearbyUiState(NearbyPhase.Failed(null, LiveFailure.OFFLINE)))
        compose.onNodeWithText("You’re offline. Check your connection and try again.").assertIsDisplayed()
    }

    @Test
    fun `ai limit without a message uses the friendly copy`() {
        show(NearbyUiState(NearbyPhase.Failed(null, LiveFailure.AI_LIMIT)))
        compose.onNodeWithText("AI limit reached for this month — try again on the 1st.").assertIsDisplayed()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }
}
