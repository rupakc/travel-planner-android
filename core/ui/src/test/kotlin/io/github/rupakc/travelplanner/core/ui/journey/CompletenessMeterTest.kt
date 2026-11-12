package io.github.rupakc.travelplanner.core.ui.journey

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class CompletenessMeterTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shows the percent for TalkBack and the five parts`() {
        val result = PlanCompleteness.of(PlanSelections(flight = Flight()), null, visaChecked = false)
        compose.setContent { TravelTheme(ThemeMode.LIGHT) { CompletenessMeter(result) } }
        compose.onNodeWithContentDescription("20%").assertIsDisplayed()
        compose.onNodeWithText("Plan completeness").assertIsDisplayed()
        listOf("Flights", "Stay", "Days planned", "Visa checked", "Packing list").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
}
