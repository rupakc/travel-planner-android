package io.github.rupakc.travelplanner.core.ui.privacy

import android.app.Application
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ConsentPromptTest {
    @get:Rule val compose = createComposeRule()

    private var allowed = 0
    private var denied = 0
    private var closed = 0

    private fun show(current: Boolean?) {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ConsentPrompt(current, onAllow = { allowed++ }, onDeny = { denied++ }, onClose = { closed++ })
            }
        }
    }

    @Test
    fun `first run has no close button`() {
        show(null)
        compose.onNodeWithText("Help improve Voyager").assertExists()
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        // No pressed state at all on first run (not merely "not selected").
        compose.onNodeWithText("Allow").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithText("No thanks").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun `reopened shows close and marks Allow selected when allowed`() {
        show(true)
        compose.onNodeWithContentDescription("Close").assertExists().performClick()
        assertEquals(1, closed)
        compose.onNodeWithText("Allow").assertIsSelected()
        compose.onNodeWithText("No thanks").assertIsNotSelected()
    }

    @Test
    fun `reopened marks No thanks selected when denied`() {
        show(false)
        compose.onNodeWithText("No thanks").assertIsSelected()
        compose.onNodeWithText("Allow").assertIsNotSelected()
    }

    @Test
    fun `allow and deny call their lambdas`() {
        show(null)
        compose.onNodeWithText("Allow").performClick()
        compose.onNodeWithText("No thanks").performClick()
        assertEquals(1, allowed)
        assertEquals(1, denied)
    }

    @Test
    fun `policy link opens the policy URL`() {
        show(null)
        compose.onNodeWithText("Privacy policy").performClick()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val started: Intent? = shadowOf(app).nextStartedActivity
        assertNotNull("a Custom Tab launch", started)
        assertEquals(PrivacyLinks.POLICY_URL, started!!.data.toString())
    }
}
