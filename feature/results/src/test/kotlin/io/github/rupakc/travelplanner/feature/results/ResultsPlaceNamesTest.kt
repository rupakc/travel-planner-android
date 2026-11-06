package io.github.rupakc.travelplanner.feature.results

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.ui.StreamBanner
import io.github.rupakc.travelplanner.feature.results.ui.TripSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** E2E finding O9: the search stores airport codes; the Results header names the cities instead ("LIS" → "Lisbon"). */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ResultsPlaceNamesTest {
    @get:Rule val compose = createComposeRule()

    private val request =
        TravelSearchRequest(origin = "JFK", destination = "LIS", departureDate = "2026-12-01", returnDate = "2026-12-04", nationality = "American")

    private fun show(state: ResultsUiState) =
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                Column {
                    TripSummary(state)
                    StreamBanner(state, onRetry = {}, onRunSaved = {})
                }
            }
        }

    @Test
    fun `header names the cities the airport lookup found`() {
        show(ResultsUiState(request = request, phase = StreamPhase.STREAMING, placeNames = mapOf("JFK" to "New York", "LIS" to "Lisbon")))
        compose.onNodeWithText("Lisbon").assertExists()
        compose.onNodeWithText("New York → Lisbon").assertExists()
        compose.onNodeWithText("Planning your trip to Lisbon").assertExists()
        compose.onAllNodesWithText("LIS", substring = true).assertCountEquals(0)
    }

    @Test
    fun `without a lookup result the code stays (nothing invented)`() {
        show(ResultsUiState(request = request, phase = StreamPhase.DONE))
        compose.onNodeWithText("LIS").assertExists()
        compose.onNodeWithText("JFK → LIS").assertExists()
    }
}
