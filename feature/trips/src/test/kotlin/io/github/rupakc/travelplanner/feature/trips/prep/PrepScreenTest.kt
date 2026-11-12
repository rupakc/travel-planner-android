package io.github.rupakc.travelplanner.feature.trips.prep

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.journey.PrepRules
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2000dp")
class PrepScreenTest {
    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<PrepEvent>()
    private val opened = mutableListOf<String>()

    private val items =
        listOf(
            PrepItem(
                "visa",
                "todo",
                due = "2027-03-11",
                overdue = true,
                facts = JsonObject(mapOf("visa_type" to JsonPrimitive("e-Visa"), "official_url" to JsonPrimitive("https://example.gov/visa"))),
            ),
            PrepItem("passport", "done"),
            PrepItem("offline_pack", "todo", auto = true),
            PrepItem("sim", "na"),
        )

    private fun show(state: PrepUiState) =
        compose.setContent {
            TravelTheme(ThemeMode.LIGHT) { PrepScreen(state, { events += it }, { opened += it }) }
        }

    private fun state() = PrepUiState(loading = false, items = items, summary = PrepRules.summarize(items))

    @Test
    fun `items are listed with overdue text, facts and a checkbox only where it can be ticked`() {
        show(state())
        compose.onNodeWithText("Sort out your visa").assertIsDisplayed()
        compose.onNodeWithText("Overdue", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Sort out your visa").assertIsOff()
        compose.onNodeWithContentDescription("Check your passport").assertIsOn()
        compose.onNodeWithText("Save the offline pack").assertIsDisplayed()
        compose.onNodeWithText("Saved automatically when you save the offline pack").assertIsDisplayed()
        compose.onNodeWithText("Not saved yet").assertIsDisplayed()
    }

    @Test
    fun `a tap sends a toggle and the visa link opens`() {
        show(state())
        compose.onNodeWithContentDescription("Sort out your visa").performClick()
        compose.onNodeWithText("Official site").performClick()
        assertEquals(listOf<PrepEvent>(PrepEvent.Toggle("visa", true)), events)
        assertEquals(listOf("https://example.gov/visa"), opened)
    }

    @Test
    fun `na items sit in a collapsed group`() {
        show(state())
        compose.onNodeWithText("Not needed (1)").assertIsDisplayed()
        compose.onNodeWithText("Choose a SIM or eSIM").assertDoesNotExistCompat()
        compose.onNodeWithText("Not needed (1)").performClick()
        compose.onNodeWithText("Choose a SIM or eSIM").assertIsDisplayed()
    }

    @Test
    fun `progress and ready state are shown`() {
        show(state())
        compose.onNodeWithText("1 of 3 done").assertIsDisplayed()
        compose.onNodeWithText("Trip ready 33 %").assertIsDisplayed()
    }

    @Test
    fun `complete shows ready to go`() {
        val done = listOf(PrepItem("passport", "done"))
        show(PrepUiState(loading = false, items = done, summary = PrepRules.summarize(done), celebrate = true))
        compose.onNodeWithText("Ready to go").assertIsDisplayed()
    }

    @Test
    fun `a notice is announced and error state offers retry`() {
        show(state().copy(notice = PrepNotice.ToggleFailed))
        compose.onNodeWithText("Couldn't save that change. Try again.").assertIsDisplayed()
    }

    @Test
    fun `load failure shows the backend message and retries`() {
        show(PrepUiState(loading = false, failed = true, errorMessage = "Slow down"))
        compose.onNodeWithText("Slow down").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<PrepEvent>(PrepEvent.Retry), events)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = this.assertDoesNotExist()
}
