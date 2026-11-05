package io.github.rupakc.travelplanner.core.ui.itinerary

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.LinkExtra
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ItineraryDayCardTest {
    @get:Rule val compose = createComposeRule()

    private val itinerary =
        ItineraryData(
            days =
                listOf(
                    ItineraryDay(
                        dayNumber = 2,
                        date = "2027-05-02",
                        city = "Lisbon, Portugal",
                        theme = "Belém",
                        linkExtras = listOf(LinkExtra(name = "Pastéis de Belém")),
                        slots =
                            listOf(
                                ItinerarySlot(
                                    timeOfDay = "morning",
                                    activity = "Jerónimos",
                                    notes = "Monastery",
                                    durationHours = 5.0,
                                    lat = 38.6979,
                                    lng = -9.2068,
                                ),
                                ItinerarySlot(timeOfDay = "afternoon", activity = "Tower", lat = 38.6916, lng = -9.2160),
                            ),
                    ),
                ),
        )

    private fun show(edits: Map<String, SlotEdit> = emptyMap(), notes: Map<String, String> = emptyMap(), dark: Boolean = false) {
        val day = ItinerarySchedule.scheduleDays(ItineraryLayouts.arrange(itinerary, null, edits).days, edits).single()
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) { ItineraryDayCard(day, edits = edits, notes = notes) }
        }
    }

    @Test
    fun `renders header, derived times, transit, warnings and link extras`() {
        show()
        compose.onNode(isHeading()).assertIsDisplayed()
        compose.onNodeWithText("Day 2").assertExists()
        compose.onNodeWithText("Belém").assertExists()
        compose.onNodeWithText("09:00–14:00").assertExists()
        compose.onNodeWithText("Monastery").assertExists()
        compose.onNodeWithText("🚶", substring = true).assertExists()
        compose.onNodeWithText("Tight schedule").assertExists()
        compose.onNodeWithText("Tower starts at 14:20 — later than planned").assertExists()
        compose.onNodeWithText("Also from your link: Pastéis de Belém — fit it in around the day.").assertExists()
    }

    @Test
    fun `edited activity hides the original description and shows the note`() {
        show(edits = mapOf("2-morning" to SlotEdit(activity = "Monastery tour")), notes = mapOf("2-morning" to "Buy tickets online"), dark = true)
        compose.onNodeWithText("Monastery tour").assertExists()
        compose.onNodeWithText("Monastery").assertDoesNotExist()
        compose.onNodeWithText("📝 Buy tickets online").assertExists()
    }
}
