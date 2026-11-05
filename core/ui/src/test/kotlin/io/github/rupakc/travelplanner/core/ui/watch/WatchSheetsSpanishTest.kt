package io.github.rupakc.travelplanner.core.ui.watch

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.FareWatchDialogState
import io.github.rupakc.travelplanner.core.domain.alerts.WatchTrip
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** R4 ui I2/I3: in Spanish, prices read "454 US$" and a typed target is sent with the UI locale. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "es-rES")
class WatchSheetsSpanishTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<WatchSheetEvent>()
    private val trip = WatchTrip("Berlin", "Lisbon", "2026-12-01", "2026-12-08", 2)

    private fun fare(state: FareWatchDialogState) =
        compose.setContent { TravelTheme(themeMode = ThemeMode.LIGHT) { FareWatchSheetContent(state, null, { events += it }, {}, {}) } }

    @Test
    fun `prices use the Spanish format and the copy fills in the same amount format`() {
        fare(FareWatchDialogState(trip, price = 1454.0))
        // es-ES: dot thousands, code after the number, non-breaking space ("1.454 US$"), never "$1,454".
        compose.onNodeWithText("1.454\u00a0US$").assertIsDisplayed()
        compose.onNodeWithText("al menos 15\u00a0US$ y un 5 %", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a typed target carries the UI locale`() {
        fare(FareWatchDialogState(trip, price = 500.0, mode = AlertMode.BELOW_TARGET))
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performScrollTo().performTextInput("450,50")
        val set = events.filterIsInstance<WatchSheetEvent.SetTarget>().last()
        assertEquals("450,50", set.text)
        assertEquals(Locale.forLanguageTag("es-ES").language, set.locale.language)
    }
}
