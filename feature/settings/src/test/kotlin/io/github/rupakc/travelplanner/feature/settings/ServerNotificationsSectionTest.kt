package io.github.rupakc.travelplanner.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.notifications.CategorySetting
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.PushStatus
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.notifications.NotificationsFailure
import io.github.rupakc.travelplanner.feature.settings.notifications.ServerNotificationsEvent
import io.github.rupakc.travelplanner.feature.settings.notifications.ServerNotificationsSection
import io.github.rupakc.travelplanner.feature.settings.notifications.ServerNotificationsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
class ServerNotificationsSectionTest {
    @get:Rule val compose = createComposeRule()

    private val ready =
        ServerNotificationsUiState(
            available = true,
            loading = false,
            settings =
                NotificationSettings(
                    categories = mapOf("weather" to CategorySetting(false)),
                    quietHours = QuietHours(true, "22:00", "07:00"),
                    dailyCap = 3,
                    timezone = "Asia/Kolkata",
                    quietHoursActive = true,
                    push = PushStatus(fcm = true),
                    devices = 2,
                ),
        )

    private fun show(state: ServerNotificationsUiState, events: MutableList<ServerNotificationsEvent> = mutableListOf()) {
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { ServerNotificationsSection(state, { events += it }) } }
    }

    private fun switchNamed(title: String) = compose.onNode(hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))

    @Test
    fun `unavailable renders nothing`() {
        show(ServerNotificationsUiState())
        compose.onNodeWithText("Price drops").assertDoesNotExist()
    }

    @Test
    fun `category switches show the state and send events`() {
        val events = mutableListOf<ServerNotificationsEvent>()
        show(ready, events)
        switchNamed("Price drops").assertIsOn()
        switchNamed("Weather").assertIsOff().performClick()
        assertEquals(listOf<ServerNotificationsEvent>(ServerNotificationsEvent.SetCategory(NotificationCategory.WEATHER, true)), events)
    }

    @Test
    fun `quiet hours, time zone, cap and push status show`() {
        val events = mutableListOf<ServerNotificationsEvent>()
        show(ready, events)
        compose.onNodeWithText("22:00").assertExists()
        compose.onNodeWithText("07:00").assertExists()
        compose.onNodeWithText("Quiet hours follow your account time zone: Asia/Kolkata.").assertExists()
        compose.onNodeWithText("Quiet hours are on right now.").assertExists()
        compose.onNodeWithText("3 per day").assertExists()
        compose.onNodeWithText("Push notifications are available").assertExists()
        compose.onNodeWithText("2 devices registered").assertExists()
        compose.onNodeWithContentDescription("More notifications per day").performClick()
        assertEquals(listOf<ServerNotificationsEvent>(ServerNotificationsEvent.SetCap(4)), events)
    }

    @Test
    fun `no time zone explains why quiet hours do nothing`() {
        show(ready.copy(settings = ready.settings!!.copy(timezone = null, quietHoursActive = false)))
        compose.onNodeWithText(
            "Quiet hours and the morning brief need a time zone on your account. Until one is set, quiet hours are not applied.",
        ).assertExists()
        compose.onNodeWithText("Not set").assertExists()
    }

    @Test
    fun `saving disables the controls`() {
        show(ready.copy(saving = true))
        switchNamed("Price drops").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More notifications per day").assertIsNotEnabled()
    }

    @Test
    fun `load failure shows the backend text with retry and a save failure shows its text`() {
        val events = mutableListOf<ServerNotificationsEvent>()
        show(ServerNotificationsUiState(available = true, loading = false, loadError = NotificationsFailure("down for now")), events)
        compose.onNodeWithText("down for now").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<ServerNotificationsEvent>(ServerNotificationsEvent.Retry), events)
    }

    @Test
    fun `save failure text is shown above the switches`() {
        show(ready.copy(saveError = NotificationsFailure(null)))
        compose.onNodeWithText("Could not save that change. It was undone.").assertExists()
        switchNamed("Price drops").assertIsOn()
    }
}
