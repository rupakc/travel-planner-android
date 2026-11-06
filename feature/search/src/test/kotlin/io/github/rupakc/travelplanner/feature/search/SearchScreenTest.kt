package io.github.rupakc.travelplanner.feature.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.CompareErrorReason
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.describeRecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.feature.search.compare.CompareColumn
import io.github.rupakc.travelplanner.feature.search.compare.CompareEvent
import io.github.rupakc.travelplanner.feature.search.compare.CompareIssue
import io.github.rupakc.travelplanner.feature.search.compare.CompareUiState
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverUiState
import io.github.rupakc.travelplanner.feature.search.importlink.ImportUiState
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SearchScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<Any>()

    private fun show(
        state: SearchUiState,
        compare: CompareUiState = CompareUiState(TestToday),
        lookups: SearchLookups = SearchLookups.None,
        onEvent: (SearchFormEvent) -> Unit = { events += it },
    ) {
        compose.setContent {
            TravelTheme {
                SearchScreen(
                    state = state,
                    discover = DiscoverUiState(TestToday),
                    compare = compare,
                    import = ImportUiState(TestToday),
                    lookups = lookups,
                    onEvent = onEvent,
                    onDiscoverEvent = { events += it },
                    onCompareEvent = { events += it },
                    onImportEvent = { events += it },
                    onPlanDiscovered = {},
                    onPlanCompared = { events += "plan:$it" },
                    onPickImages = {},
                    wide = false,
                )
            }
        }
    }

    @Test
    fun `validation messages show after submit`() {
        show(SearchUiState(today = TestToday, issues = listOf(SearchFormIssue.ORIGIN_REQUIRED, SearchFormIssue.NATIONALITY_REQUIRED)))
        compose.onNodeWithTag("search_list").performScrollToNode(hasTestTag("search_issues"))
        compose.onNodeWithTag("search_issues").assertIsDisplayed()
        // On the field and in the summary above the button.
        compose.onAllNodesWithText("Add your nationality — it decides visa info", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithTag("search_list").performScrollToNode(hasTestTag("search_submit"))
        compose.onNodeWithTag("search_submit").performClick()
        assertEquals(SearchFormEvent.Submit, events.last())
    }

    @Test
    fun `picking an airport suggestion stores its IATA code`() {
        val lookups =
            object : SearchLookups {
                override suspend fun airports(query: String) = listOf(Airport("JFK", "John F. Kennedy Intl", "New York", "United States", "US"))

                override suspend fun nationalities(query: String) = emptyList<Nationality>()
            }
        var state by mutableStateOf(SearchUiState(today = TestToday))
        show(state, lookups = lookups, onEvent = { e -> if (e is SearchFormEvent.UpdateForm) state = state.copy(form = e.form) })
        compose.onNodeWithText("From").performTextInput("new y")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithTag("suggestion:JFK", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("JFK", state.form.origin)
        compose.onNodeWithText("JFK — New York, United States").assertExists()
    }

    @Test
    fun `the spinner never outlives a search cancelled by backspacing below two characters`() {
        val lookups =
            object : SearchLookups {
                override suspend fun airports(query: String): List<Airport> = awaitCancellation()

                override suspend fun nationalities(query: String) = emptyList<Nationality>()
            }
        var state by mutableStateOf(SearchUiState(today = TestToday))
        show(state, lookups = lookups, onEvent = { e -> if (e is SearchFormEvent.UpdateForm) state = state.copy(form = e.form) })
        compose.onNodeWithText("From").performTextInput("lon")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        // Searching: the spinner replaces Clear.
        compose.onNodeWithContentDescription("Clear From").assertDoesNotExist()
        compose.onNodeWithText("From").performTextReplacement("l")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Clear From").assertExists()
    }

    @Test
    fun `hidden features drop their mode chips`() {
        show(SearchUiState(today = TestToday, visibleModes = listOf(SearchMode.KNOWN, SearchMode.IMPORT)))
        compose.onNodeWithTag("mode_known").assertExists()
        compose.onNodeWithTag("mode_import").assertExists()
        compose.onNodeWithTag("mode_compare").assertDoesNotExist()
        compose.onNodeWithTag("mode_surprise").assertDoesNotExist()
        compose.onNodeWithTag("mode_import").performClick()
        assertEquals(SearchFormEvent.SelectMode(SearchMode.IMPORT), events.last())
    }

    @Test
    fun `recent searches load, and remove has an accessible label`() {
        val entry = RecentSearch("r1", RecentSearchIn("New York", "Lisbon, Portugal", "2026-12-01"))
        show(SearchUiState(today = TestToday, recent = listOf(RecentSearchCard(entry, describeRecentSearch(entry.search), false))))
        compose.onNodeWithContentDescription("Remove recent search New York → Lisbon").performClick()
        assertEquals(SearchFormEvent.RemoveRecent("r1"), events.last())
        compose.onNodeWithText("Search again").performClick()
        assertEquals(SearchFormEvent.RerunRecent("r1"), events.last())
    }

    @Test
    fun `a recent search saved with airport codes shows the cities and its traveller count on one line`() {
        val entry = RecentSearch("r2", RecentSearchIn("JFK", "LIS", "2026-12-01", interests = listOf("food", "history", "culture"), adults = 2))
        val names = mapOf("JFK" to "New York", "LIS" to "Lisbon")
        show(SearchUiState(today = TestToday, recent = listOf(RecentSearchCard(entry, describeRecentSearch(entry.search, names = names), false))))
        compose.onNodeWithText("New York → Lisbon").assertIsDisplayed()
        compose.onNodeWithText("2 travelers").assertIsDisplayed()
        val travellers = compose.onNodeWithText("2 travelers").fetchSemanticsNode().boundsInRoot
        val route = compose.onNodeWithText("New York → Lisbon").fetchSemanticsNode().boundsInRoot
        // One line of text, not a letter-wide column (the old row squeezed it next to the interest chips).
        assertTrue(travellers.height < route.height * 2)
    }

    @Test
    fun `compare columns show retry for a failed destination and plan for a loaded one`() {
        val form = SearchForm.initial(TestToday).copy(origin = "JFK", nationality = "American")
        show(
            SearchUiState(today = TestToday, mode = SearchMode.COMPARE),
            compare =
                CompareUiState(
                    TestToday,
                    form = form,
                    destinations = listOf("Lisbon", "Porto"),
                    columns =
                        listOf(
                            CompareColumn("Lisbon", CompareColumn.Status.DONE, CompareSummary(label = "Lisbon, Portugal")),
                            CompareColumn("Porto", CompareColumn.Status.ERROR, error = CompareErrorReason.CheckDetails),
                        ),
                ),
        )
        compose.onNodeWithTag("search_list").performScrollToNode(hasTestTag("compare_retry_1"))
        compose.onNodeWithText("Please check the trip details").assertExists()
        compose.onNodeWithTag("compare_retry_1").performClick()
        assertEquals(CompareEvent.Retry(1), events.last())
        compose.onNodeWithTag("search_list").performScrollToNode(hasTestTag("compare_plan_0"))
        compose.onNodeWithTag("compare_plan_0").performClick()
        assertEquals("plan:0", events.last())
    }

    @Test
    fun `compare needs two destinations message`() {
        val form = SearchForm.initial(TestToday)
        show(
            SearchUiState(today = TestToday, mode = SearchMode.COMPARE),
            compare = CompareUiState(TestToday, form = form, issue = CompareIssue.NEED_TWO_DESTINATIONS),
        )
        compose.onNodeWithTag("search_list").performScrollToNode(hasTestTag("compare_issue"))
        compose.onNodeWithText("Add at least two different destinations to compare.").assertExists()
    }

    @Test
    fun `import submit is disabled until there is something to read`() {
        show(SearchUiState(today = TestToday, mode = SearchMode.IMPORT))
        compose.onNodeWithTag("search_list").performScrollToNode(hasTestTag("import_submit"))
        compose.onNodeWithTag("import_submit").assertIsNotEnabled()
    }
}
