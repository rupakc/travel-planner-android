package io.github.rupakc.travelplanner.core.ui

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SectionScaffoldTest {
    @get:Rule val compose = createComposeRule()

    /**
     * Regression (E2E on a 411 dp phone): Flights with Fare calendar / Watch fare / Filters + the verification chip
     * left the weighted title about one character wide, so "Flights" wrapped one letter per line.
     */
    @Test
    fun `title keeps its width when the header has many actions`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                SectionScaffold(
                    section = SectionId.FLIGHTS,
                    status = SectionStatus.DONE,
                    expanded = true,
                    onToggle = {},
                    modifier = Modifier.width(360.dp),
                    badge = { Text("AI-generated") },
                    headerActions = {
                        TextButton(onClick = {}) { Text("Fare calendar") }
                        TextButton(onClick = {}) { Text("Watch fare") }
                        TextButton(onClick = {}) { Text("Filters") }
                    },
                ) { Text("content") }
            }
        }
        val title = compose.onNodeWithText("Flights", useUnmergedTree = true).assertExists()
        val bounds = title.getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        val height = bounds.bottom - bounds.top
        assertTrue("title width $width", width >= 120.dp)
        assertTrue("title on one line, height $height", height <= 40.dp)
        compose.onNodeWithText("Watch fare").assertExists()
        compose.onNode(isHeading(), useUnmergedTree = true).assertExists()
    }
}
