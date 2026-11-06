package io.github.rupakc.travelplanner.feature.results.ui

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.SectionUi
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The jump chip row keeps the user's sideways scroll while sections stream in; it only follows a new active section. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class QuickJumpRowTest {
    @get:Rule val compose = createComposeRule()

    private var sections by mutableStateOf(sectionsWith(SectionStatus.LOADING))
    private var active by mutableStateOf<SectionId?>(SectionId.FLIGHTS)

    private fun sectionsWith(status: SectionStatus) =
        ResultsSection.entries.map { SectionUi(section = it, status = status, data = null, relevant = true, expanded = true) }

    private fun chip(title: String) = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasContentDescription(title)))

    private fun show() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { QuickJumpRow(sections, active = active, onJump = {}, modifier = Modifier.width(360.dp)) }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a swiped chip row is not scrolled back when section data updates`() {
        show()
        compose.onNodeWithTag("results_jump_row").performScrollToIndex(ResultsSection.entries.lastIndex)
        compose.waitForIdle()
        chip("Packing List").assertIsDisplayed()
        // Streaming: statuses and data change while the active section stays the same (always so in the expanded layout).
        sections = sectionsWith(SectionStatus.ENHANCING)
        compose.waitForIdle()
        sections = sectionsWith(SectionStatus.DONE)
        compose.waitForIdle()
        chip("Packing List").assertIsDisplayed()
        chip("Flights").assertIsNotDisplayed()
    }

    @Test
    fun `a new active section is still brought into the row`() {
        show()
        active = SectionId.PACKING_LIST
        compose.waitForIdle()
        chip("Packing List").assertIsDisplayed()
        active = SectionId.FLIGHTS
        compose.waitForIdle()
        chip("Flights").assertIsDisplayed()
    }
}
