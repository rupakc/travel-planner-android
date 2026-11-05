package io.github.rupakc.travelplanner.feature.home.search

import android.app.Application
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearch
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind
import io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class GlobalSearchScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<GlobalSearchEvent>()
    private val items =
        listOf(
            SearchableItem(GlobalSearchKind.TRIP, "7", "Tokyo spring", "Tokyo"),
            SearchableItem(GlobalSearchKind.FEATURE, "near_me", "Near me tokyo", "Things to do"),
        )

    private fun show(state: GlobalSearchUiState) {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { GlobalSearchScreen(state, onEvent = { events += it }, onBack = {}, autoFocus = false) }
        }
    }

    @Test
    fun `empty query explains what can be searched`() {
        show(GlobalSearchUiState())
        compose.onNodeWithText("Search everything").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("to")
        assertEquals(GlobalSearchEvent.QueryChanged("to"), events.single())
    }

    @Test
    fun `results are grouped with counts, filters and hidden-feature note`() {
        show(GlobalSearchUiState(query = "tokyo", results = GlobalSearch.search("tokyo", items), hiddenFeatures = setOf("near_me")))
        compose.onNodeWithText("All (2)").assertIsSelected()
        compose.onNodeWithText("2 results").assertExists()
        compose.onNodeWithText("Off: opening it turns it on", substring = true).assertExists()
        compose.onNodeWithText("Tokyo spring").performClick()
        compose.onNodeWithText("Chats (0)").performClick()
        assertTrue(events.first() is GlobalSearchEvent.Open)
        assertEquals(GlobalSearchEvent.ToggleFilter(GlobalSearchKind.CHAT), events[1])
    }

    @Test
    fun `no matches shows the query and clear action`() {
        show(GlobalSearchUiState(query = "zzz", filter = setOf(GlobalSearchKind.TRIP)))
        compose.onNodeWithText("No matches for “zzz”").assertExists()
        compose.onNodeWithText("Show all results").performClick()
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(listOf(GlobalSearchEvent.ClearFilter, GlobalSearchEvent.QueryChanged("")), events)
    }
}
