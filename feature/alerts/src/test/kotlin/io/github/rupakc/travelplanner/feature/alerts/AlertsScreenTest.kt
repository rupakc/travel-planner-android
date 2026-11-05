package io.github.rupakc.travelplanner.feature.alerts

import android.app.Application
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTab
import io.github.rupakc.travelplanner.core.domain.alerts.CheckOutcome
import io.github.rupakc.travelplanner.core.domain.alerts.CheckType
import io.github.rupakc.travelplanner.core.domain.alerts.Tone
import io.github.rupakc.travelplanner.core.model.alerts.AppNotification
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AlertsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<AlertsEvent>()
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val fare =
        FareWatch(
            id = 7,
            status = "active",
            originLabel = "Berlin, Germany",
            destinationLabel = "Lisbon, Portugal",
            departureDate = "2026-12-01",
            returnDate = "2026-12-08",
            numTravelers = 2,
            baselinePrice = 500.0,
            lastPrice = 454.0,
            lastCheckedAt = "2026-11-05T10:00:00Z",
        )
    private val hotel =
        PriceWatch(id = 3, kind = "hotel", status = "active", title = "Lumen Hotel", currency = "EUR", lastPrice = 398.0, expiresOn = "2026-12-14")

    private fun show(state: AlertsUiState, dark: Boolean = false) =
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { AlertsScreen(state, { events += it }, {}) }
        }

    @Test
    fun `tabs show counts and selecting one sends an event`() {
        show(AlertsUiState(fares = listOf(fare), prices = listOf(hotel), livePrices = true, now = now))
        compose.onNodeWithContentDescription("Flights, 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("All, 2").assertIsDisplayed()
        compose.onNodeWithText("Showing 2 watches").assertIsDisplayed()
        // R4 ui M9: the search box offers the Search IME action.
        compose.onNode(androidx.compose.ui.test.hasImeAction(androidx.compose.ui.text.input.ImeAction.Search)).assertExists()
        compose.onNodeWithContentDescription("Hotels, 1").performClick()
        assertEquals(AlertsEvent.SelectTab(AlertTab.HOTELS), events.last())
    }

    @Test
    fun `fare card shows the price hero, change and actions`() {
        show(AlertsUiState(fares = listOf(fare), prices = emptyList(), livePrices = true, now = now))
        compose.onNodeWithText("Berlin → Lisbon").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$454").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("$46 less than when you started").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Price dropped").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check now").performScrollTo().performClick()
        assertEquals(AlertsEvent.Check("watch-7"), events.last())
        compose.onNodeWithContentDescription("Delete the Berlin → Lisbon watch").performScrollTo().performClick()
        compose.onNodeWithText("Stop watching?").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `check outcome is read out on the card`() {
        val msg = CardMessage.Checked(CheckOutcome(CheckType.CHECKED_PRICE, Tone.NEUTRAL, price = 398.0), "EUR", fare = false)
        show(AlertsUiState(fares = emptyList(), prices = listOf(hotel), messages = mapOf("price-watch-3" to msg), now = now), dark = true)
        compose.onNodeWithText("Checked just now: €398. Nothing worth an alert yet.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `empty and unavailable copy is honest`() {
        show(AlertsUiState(fares = emptyList(), prices = emptyList(), livePrices = false, now = now))
        compose.onNodeWithText("Nothing watched yet").assertIsDisplayed()
        compose
            .onNodeWithText("Watching flights, hotels, activities and trips needs live prices, which aren’t set up on this server yet.")
            .assertIsDisplayed()
        compose.onNodeWithText(
            "Live prices are unavailable on this server right now, so we can’t check or start watches. " +
                "Your watches are kept and resume when live prices are back.",
        ).assertIsDisplayed()
    }

    @Test
    fun `per-tab empty copy`() {
        show(AlertsUiState(fares = listOf(fare), prices = emptyList(), tab = AlertTab.TRIPS, now = now))
        compose.onNodeWithText("No trips watched yet. Save a plan, then choose “Watch whole trip”.").assertIsDisplayed()
    }

    @Test
    fun `inbox shows unread state and opens a notification`() {
        val n =
            AppNotification(id = 42, kind = "fare_drop", title = "Berlin → Lisbon dropped to $454", body = "For 1–8 Dec", createdAt = "2026-11-05T11:00:00Z")
        val sent = mutableListOf<NotificationsEvent>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                NotificationsScreen(NotificationsUiState(items = listOf(n), unread = 1, now = now), { sent += it }, {})
            }
        }
        compose.onNodeWithText("See fare").assertIsDisplayed()
        compose.onNodeWithText("1 h ago").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mark all as read").performClick()
        compose.onNodeWithText("Berlin → Lisbon dropped to $454").performClick()
        assertEquals(listOf(NotificationsEvent.MarkAllRead, NotificationsEvent.Open(n)), sent)
    }

    @Test
    fun `bell caps the badge and labels the count`() {
        var opened = false
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { NotificationBell(12, { opened = true }) } }
        compose.onNodeWithText("9+").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notifications, 12 unread").performClick()
        assertTrue(opened)
    }

    @Test
    fun `Save waits, with a reason, while Check now runs`() {
        val saved = mutableListOf<WatchEdit>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                androidx.compose.foundation.layout.Column(
                    androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    WatchCard(
                        item = io.github.rupakc.travelplanner.core.domain.alerts.AnyWatch.Fare(fare),
                        busy = CardAction.CHECK,
                        message = null,
                        highlighted = false,
                        emailAvailable = false,
                        userEmail = null,
                        now = now,
                        callbacks = CardCallbacks({}, {}, { saved += it }, {}, {}, {}),
                    )
                }
            }
        }
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.onNodeWithText("Saving is available once the check finishes.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled().performClick()
        assertTrue(saved.isEmpty())
    }
}
