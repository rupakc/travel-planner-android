package io.github.rupakc.travelplanner.feature.results.sections.commerce

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.search.FareCalendarDate
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.FareCalendarUi
import io.github.rupakc.travelplanner.feature.results.LoadStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** R4 ui M2: the cheapest date is named in words (visible and to TalkBack), not only by its green fill. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w800dp-h1200dp")
class FareCalendarStripTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `cheapest date is labelled, not colour-only`() {
        val dates =
            listOf(
                FareCalendarDate("2026-12-01", "2026-12-08", 420.0, isSelected = true),
                FareCalendarDate("2026-12-02", "2026-12-09", 380.0),
                FareCalendarDate("2026-12-03", "2026-12-10", 450.0),
            )
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                FareCalendarStrip(FareCalendarUi(available = true, open = true, status = LoadStatus.DONE, dates = dates), onEvent = {})
            }
        }
        compose.onNodeWithText("Cheapest", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Cheapest", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("$380", substring = true) and hasContentDescription("Cheapest", substring = true)).assertIsDisplayed()
    }
}
