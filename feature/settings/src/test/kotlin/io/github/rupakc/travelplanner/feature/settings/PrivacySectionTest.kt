package io.github.rupakc.travelplanner.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.root.SettingsEvent
import io.github.rupakc.travelplanner.feature.settings.root.SettingsNav
import io.github.rupakc.travelplanner.feature.settings.root.SettingsScreen
import io.github.rupakc.travelplanner.feature.settings.root.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w411dp-h2400dp")
class PrivacySectionTest {
    @get:Rule val compose = createComposeRule()

    private fun show(allowed: Boolean, events: MutableList<SettingsEvent>, navs: MutableList<SettingsNav> = mutableListOf()) {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(SettingsUiState(loading = false, privacyAllowed = allowed), onEvent = { events += it }, onNav = { navs += it }, onBack = {})
            }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("privacy.switch"))
    }

    @Test
    fun `switch exposes its state description`() {
        show(true, mutableListOf())
        compose.onNode(hasTestTag("privacy.switch")).assertIsOn()
        compose.onNode(hasTestTag("privacy.switch") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On")).assertExists()
        compose.onNodeWithText("Help improve Voyager").assertExists()
    }

    @Test
    fun `switch off reports SetPrivacy and policy row opens the link`() {
        val events = mutableListOf<SettingsEvent>()
        val navs = mutableListOf<SettingsNav>()
        show(false, events, navs)
        compose.onNode(hasTestTag("privacy.switch")).assertIsOff().performClick()
        compose.onNodeWithText("Privacy policy").performClick()
        compose.onNodeWithText("Privacy choices").performClick()
        assertEquals(listOf(SettingsEvent.SetPrivacy(true), SettingsEvent.OpenPrivacyChoices), events)
        assertEquals(listOf<SettingsNav>(SettingsNav.OpenLink("https://github.com/rupakc/travel-planner/blob/main/docs/Privacy.md")), navs)
    }
}
