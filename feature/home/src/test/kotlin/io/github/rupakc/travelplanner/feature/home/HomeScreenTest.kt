package io.github.rupakc.travelplanner.feature.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<HomeEvent>()

    private fun show(state: HomeUiState, dark: Boolean = false) {
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { HomeScreen(state, onEvent = { events += it }) }
        }
    }

    private val full =
        HomeUiState(
            loading = false,
            userName = "alice",
            travellerTypes = listOf(TravellerType.CULTURE_FOODIE),
            upcomingTrip = UpcomingTrip(7, "Spring break", "Tokyo", LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 8), 22),
            tiles = listOf(FeatureTile(FeatureId.BUDGET_PLANNER, TileReason.SEARCHED_WITH_BUDGET), FeatureTile(FeatureId.CHAT)),
            becauseYouSearched = BecauseYouSearched("rs-1", "Lisbon", listOf(FeatureTile(FeatureId.BUDGET_PLANNER, TileReason.SEARCHED_WITH_BUDGET))),
            recentSearches = listOf(recent("rs-1", "Lisbon")),
        )

    @Test
    fun `shows greeting, type, trip, tiles and personalised row`() {
        show(full)
        compose.onNodeWithText("Hello, alice").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your traveller type: Culture seeker and foodie. Opens Settings.").performClick()
        compose.onNodeWithText("Tokyo").performClick()
        compose.onNodeWithText("In 22 days").assertExists()
        compose.onNodeWithText("Because you searched Lisbon").assertExists()
        assertEquals(listOf(HomeEvent.OpenTravellerType, HomeEvent.OpenTrip(7)), events)
    }

    @Test
    fun `tiles and recent searches send their events`() {
        show(full, dark = true)
        compose.onNodeWithText("Chat").performClick()
        compose.onRoot().assertExists()
        compose.onNodeWithText("New York → Lisbon").performClick()
        assertTrue(HomeEvent.OpenFeature(FeatureId.CHAT) in events)
        assertTrue(HomeEvent.OpenRecentSearch("rs-1") in events)
    }

    @Test
    fun `empty state offers plan a trip and the search bar opens search`() {
        show(HomeUiState(loading = false, userName = "alice"))
        compose.onNodeWithText("Plan your next trip").assertIsDisplayed()
        compose.onNodeWithText("Plan a trip").performClick()
        compose.onNodeWithText("Search trips, chats, features and settings").performClick()
        compose.onNodeWithText("No tools turned on").assertExists()
        assertEquals(listOf(HomeEvent.PlanTrip, HomeEvent.OpenSearch), events)
    }

    @Test
    fun `refresh error shows retry`() {
        show(full.copy(refreshError = io.github.rupakc.travelplanner.core.common.AppError.Network()))
        compose.onNodeWithText("Couldn't refresh. Showing what's saved on this device.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf(HomeEvent.Refresh), events)
    }

    @Test
    fun `loading shows a skeleton`() {
        show(HomeUiState())
        compose.onNodeWithContentDescription("Loading your home").assertExists()
    }
}
