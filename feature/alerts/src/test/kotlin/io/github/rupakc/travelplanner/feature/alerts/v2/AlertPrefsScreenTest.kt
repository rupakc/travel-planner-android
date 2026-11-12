package io.github.rupakc.travelplanner.feature.alerts.v2

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.DailyCap
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AlertPrefsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val caps = mutableListOf<Pair<String, Int>>()
    private val actions = AlertPrefsActions(onCap = { k, v -> caps += k to v })
    private val prefs =
        AlertPrefs(
            digestWeekly = false,
            digestDay = 1,
            instant = "all",
            quietHours = QuietHours(true, "22:00", "07:00"),
            dailyCap = DailyCap(3, 1, 10, 3),
            groupWindowMin = 0,
            timezone = null,
        )

    private fun show(state: AlertPrefsUiState) = compose.setContent { TravelTheme { AlertPrefsScreen(state, actions, {}) } }

    @Test fun `the digest push switch waits for the weekly switch and a missing timezone is explained`() {
        show(AlertPrefsUiState.Content(prefs))
        compose.onNodeWithText("Also send it as a notification").assertIsDisplayed()
        compose.onNodeWithText("Timezone not set").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Quiet hours need a timezone. Set one in your account settings.").performScrollTo().assertIsDisplayed()
    }

    @Test fun `steppers respect the 1 to 10 bounds`() {
        show(AlertPrefsUiState.Content(prefs))
        compose.onNodeWithContentDescription("Fewer alerts a day for Hotels").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("More alerts a day for Activities").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("More alerts a day for Flights").performScrollTo().performClick()
        assertEquals(listOf("flight" to 4), caps)
    }

    @Test fun `an invalid save shows the translated message and a failed load offers retry`() {
        show(AlertPrefsUiState.Content(prefs, error = PrefsError.Invalid))
        compose.onNodeWithText("Those settings aren't valid. Check the times and limits.").assertIsDisplayed()
    }

    @Test fun `load failure shows retry`() {
        show(AlertPrefsUiState.Failed("Server said no"))
        compose.onNodeWithText("Server said no").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }
}
