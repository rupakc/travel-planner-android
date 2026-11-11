package io.github.rupakc.travelplanner.telemetry

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentPromptState
import io.github.rupakc.travelplanner.core.designsystem.component.LocalUiEventSink
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventKind
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventSink
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.navigation.ConsentHost
import io.github.rupakc.travelplanner.navigation.TopLevelDestination
import io.github.rupakc.travelplanner.navigation.TravelAppScaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The shell's telemetry wiring: nav taps reach the sink, and the consent host maps prompt states to choices. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class ShellTelemetryTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `nav items report nav taps through the sink`() {
        val taps = mutableListOf<Pair<UiEventKind, String>>()
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                CompositionLocalProvider(LocalUiEventSink provides UiEventSink { kind, tid, _ -> taps += kind to tid }) {
                    TravelAppScaffold(
                        currentTopLevel = TopLevelDestination.HOME,
                        showNavigation = true,
                        onNavigate = {},
                        onOpenSettings = {},
                    ) { Text("body") }
                }
            }
        }
        compose.onNodeWithText("My trips").performClick()
        compose.onNodeWithText("Alerts").performClick()
        assertEquals(listOf(UiEventKind.TAP to "nav.plans", UiEventKind.TAP to "nav.alerts"), taps)
    }

    @Test
    fun `first run shows the prompt without close and allow grants`() {
        val chosen = mutableListOf<ConsentChoice>()
        var prompt by mutableStateOf<ConsentPromptState>(ConsentPromptState.FirstRun)
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) { ConsentHost(prompt, onChoice = { chosen += it }, onClose = {}) }
        }
        compose.onNodeWithContentDescription("Close").assertDoesNotExist()
        compose.onNodeWithText("Allow").performClick()
        assertEquals(listOf(ConsentChoice.GRANTED), chosen)
        prompt = ConsentPromptState.Hidden
        compose.onNodeWithText("Allow").assertDoesNotExist()
    }

    @Test
    fun `reopened choices show the current choice and close hides them`() {
        val chosen = mutableListOf<ConsentChoice>()
        var closed = false
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                ConsentHost(ConsentPromptState.Reopened(allowed = false), onChoice = { chosen += it }, onClose = { closed = true })
            }
        }
        compose.onNodeWithText("No thanks").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf(ConsentChoice.DENIED), chosen)
        assertTrue(closed)
    }
}
