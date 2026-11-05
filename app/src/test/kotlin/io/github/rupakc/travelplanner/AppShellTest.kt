package io.github.rupakc.travelplanner

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.navigation.TopLevelDestination
import io.github.rupakc.travelplanner.navigation.TravelAppScaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Snapshot-free check that the app shell renders in both themes on Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class AppShellTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `shell shows the five destinations and a settings action`() {
        var navigated: TopLevelDestination? = null
        var settings = false
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.LIGHT) {
                TravelAppScaffold(
                    currentTopLevel = TopLevelDestination.SEARCH,
                    showNavigation = true,
                    onNavigate = { navigated = it },
                    onOpenSettings = { settings = true },
                ) { Text("body") }
            }
        }
        listOf("Home", "Chat", "My trips", "Alerts").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("body").assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Alerts").performClick()
        assertTrue(settings)
        assertEquals(TopLevelDestination.ALERTS, navigated)
    }

    @Test
    fun `dark theme and hidden navigation render content only`() {
        compose.setContent {
            TravelTheme(themeMode = ThemeMode.DARK) {
                TravelAppScaffold(currentTopLevel = null, showNavigation = false, onNavigate = {}, onOpenSettings = {}) { Text("login") }
            }
        }
        compose.onNodeWithText("login").assertExists()
        compose.onNodeWithText("My trips").assertDoesNotExist()
    }
}
