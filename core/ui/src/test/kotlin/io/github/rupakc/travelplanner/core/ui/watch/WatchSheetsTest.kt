package io.github.rupakc.travelplanner.core.ui.watch

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.PriceWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.WatchTrip
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.NoLivePriceError
import io.github.rupakc.travelplanner.core.model.alerts.PriceSuggestion
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.alerts.TargetCurrencyMismatchError
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class WatchSheetsTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<WatchSheetEvent>()
    private val trip = WatchTrip("Berlin", "Lisbon", "2026-12-01", "2026-12-08", 2)

    private fun json(text: String) = TravelJson.parseToJsonElement(text) as JsonObject

    private fun fare(state: FareWatchDialogState) =
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { FareWatchSheetContent(state, null, { events += it }, {}, {}) } }

    private fun price(state: PriceWatchDialogState, dark: Boolean = false) =
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { PriceWatchSheetContent(state, "me@example.com", { events += it }, {}, {}) }
        }

    @Test
    fun `fare sheet shows today's price and sends events`() {
        fare(FareWatchDialogState(trip, price = 454.0))
        compose.onNodeWithText("Watch this fare").assertIsDisplayed()
        compose.onNodeWithText("$454").assertIsDisplayed()
        compose.onNodeWithText("Include nearby dates (±3 days)").performScrollTo().performClick()
        compose.onNodeWithText("When it’s below a price I choose").performScrollTo().performClick()
        compose.onNodeWithText("Start watching").performScrollTo().performClick()
        assertEquals(listOf(WatchSheetEvent.SetFlexible(true), WatchSheetEvent.SetMode(AlertMode.BELOW_TARGET), WatchSheetEvent.Submit), events)
    }

    @Test
    fun `fare sheet names the cities of airport codes`() {
        fare(FareWatchDialogState(WatchTrip("JFK", "LIS", "2026-12-01", null, 1), placeNames = mapOf("JFK" to "New York", "LIS" to "Lisbon")))
        compose.onNodeWithText("New York → Lisbon", substring = true).assertIsDisplayed()
    }

    @Test
    fun `fare sheet shows a target error once typed`() {
        fare(FareWatchDialogState(trip, price = 454.0, mode = AlertMode.BELOW_TARGET, target = "abc", targetTouched = true))
        compose.onNodeWithText("Enter a price, like 450.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `fare sheet confirmation`() {
        fare(FareWatchDialogState(trip, done = FareWatch(id = 1, baselinePrice = 454.0, numTravelers = 2, alreadyBelowTarget = true)))
        compose.onNodeWithText("Watching this fare").assertIsDisplayed()
        compose.onNodeWithText("Already below your target — we’ll tell you if it drops further.").assertIsDisplayed()
        compose.onNodeWithText("See your alerts").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `no live price lists suggestions and hides Start watching`() {
        val noMatch =
            NoLivePriceError(
                message = "We couldn't find Lumen Hotel on Google Hotels.",
                suggestions = listOf(PriceSuggestion(title = "Lumen Lisboa", subtitle = "4 nights", price = 398.0, currency = "EUR")),
                estimate = json("""{"price":410}"""),
            )
        price(PriceWatchDialogState(kind = "hotel", spec = json("""{"name":"Lumen Hotel"}"""), noMatch = noMatch))
        compose.onNodeWithText("Live prices we did find:").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("€398").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Our AI estimate was $410 — an estimate, not a live price, so we can’t watch it.").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTextCount("Start watching") == 0)
        compose.onNodeWithText("Watch this instead").performScrollTo().performClick()
        assertEquals(listOf<WatchSheetEvent>(WatchSheetEvent.PickSuggestion(0)), events)
    }

    @Test
    fun `currency mismatch asks for the target in the listing currency`() {
        val ask = TargetCurrencyMismatchError(message = "This is priced in EUR (now €120). Enter your target in EUR.", currency = "EUR", price = 120.0)
        price(PriceWatchDialogState(kind = "activity", spec = json("""{"name":"Sail","city":"Lisbon"}"""), mode = AlertMode.BELOW_TARGET, currencyAsk = ask))
        compose.onNodeWithText("This is priced in EUR (now €120). Enter your target in EUR.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Target price in EUR").assertExists()
    }

    @Test
    fun `trip watch shows Live and Estimate badges as text`() {
        val watch =
            PriceWatch(
                id = 1,
                kind = "trip",
                title = "Lisbon trip",
                baselinePrice = 911.0,
                subject =
                    json(
                        """{"components":[{"id":"f","type":"flight","label":"Flights","live":true,"status":"ok","price":542},""" +
                            """{"id":"s","label":"SIM / eSIM","live":false,"price":13}]}""",
                    ),
            )
        price(PriceWatchDialogState(kind = "trip", spec = json("""{"plan_id":3}"""), done = watch), dark = true)
        compose.onNodeWithText("Watching this trip").assertIsDisplayed()
        compose.onNodeWithText("Live").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Estimate").performScrollTo().assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
