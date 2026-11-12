package io.github.rupakc.travelplanner.feature.trips.recap

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCountry
import io.github.rupakc.travelplanner.core.model.trips.RecapSpend
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2000dp")
class RecapScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<RecapEvent>()
    private var next = 0

    private fun show(state: RecapUiState, whereNext: (() -> Unit)? = { next++ }) =
        compose.setContent {
            TravelTheme(ThemeMode.LIGHT) { RecapScreen(state, { events += it }, {}, whereNext) }
        }

    private val recap =
        Recap(
            title = "Lisbon",
            endDate = "2027-04-08",
            days = 8,
            visitedCount = 9,
            ratedCount = 7,
            avgStars = 4.3,
            distanceKm = null,
            countries = listOf(RecapCountry("PT", "Portugal", new = true), RecapCountry("ES", "Spain")),
            spend = RecapSpend(budgetUsd = 2000.0, spentUsd = "2140.50"),
        )

    @Test
    fun `a null distance says so and stats render`() {
        show(RecapUiState(loading = false, recap = recap))
        compose.onNodeWithText("Distance not available").assertIsDisplayed()
        compose.onNodeWithText("Days: 8").assertIsDisplayed()
        compose.onNodeWithText("Places visited: 9 · rated: 7").assertIsDisplayed()
    }

    @Test
    fun `over budget has its own text and the new country says new in words`() {
        show(RecapUiState(loading = false, recap = recap, newCountries = setOf("PT")))
        compose.onNodeWithText("Over budget by", substring = true).assertIsDisplayed()
        compose.onNodeWithText("New: Portugal").assertIsDisplayed()
        compose.onNodeWithText("Spain").assertIsDisplayed()
    }

    @Test
    fun `a trip without dates hides complete`() {
        show(RecapUiState(loading = false, recap = recap.copy(endDate = null)))
        compose.onNodeWithText("Mark recap complete").assertDoesNotExist()
    }

    @Test
    fun `complete sends the event and a completed recap hides the button`() {
        show(RecapUiState(loading = false, recap = recap))
        compose.onNodeWithText("Mark recap complete").performClick()
        assertEquals(listOf<RecapEvent>(RecapEvent.Complete), events)
    }

    @Test
    fun `completed recap shows the state and no button`() {
        show(RecapUiState(loading = false, recap = recap.copy(completedAt = "2027-04-09T10:00:00Z")))
        compose.onNodeWithText("Recap complete").assertIsDisplayed()
        compose.onNodeWithText("Mark recap complete").assertDoesNotExist()
    }

    @Test
    fun `not ready message and where next`() {
        show(RecapUiState(loading = false, recap = recap, completeFailure = CompleteFailure(notReady = true)))
        compose.onNodeWithText("This trip has not ended yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Where next?").performClick()
        assertEquals(1, next)
    }

    @Test
    fun `where next is hidden without a lambda`() {
        show(RecapUiState(loading = false, recap = recap), whereNext = null)
        compose.onNodeWithText("Where next?").assertDoesNotExist()
    }

    @Test
    fun `share opens the share dialog for the trip when sharing 2 is on, else the legacy share`() {
        val opened = mutableListOf<Pair<io.github.rupakc.travelplanner.core.model.share.ShareKind, Long?>>()
        val launcher = io.github.rupakc.travelplanner.core.ui.share.ShareLauncher { kind, id -> opened += kind to id }
        val recap = Recap(tripId = 12, title = "Lisbon")
        var legacy = 0
        assertEquals(true, shareRecap(launcher, recap) { legacy++ >= 0 })
        assertEquals(listOf(io.github.rupakc.travelplanner.core.model.share.ShareKind.RECAP to 12L), opened)
        assertEquals(0, legacy)
        assertEquals(false, shareRecap(null, recap) { legacy++ < 0 })
        assertEquals(1, legacy)
        assertEquals(1, opened.size)
    }
}
