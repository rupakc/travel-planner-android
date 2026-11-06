package io.github.rupakc.travelplanner

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** E2E finding O1: the system-bar icons follow the app's theme setting, not only the system's dark mode. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SystemBarsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun lightIcons(): Boolean {
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // "Light status bar" = dark icons; a dark app needs light icons.
        return !controller.isAppearanceLightStatusBars
    }

    @Test
    fun `theme setting wins over the system mode`() {
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun `dark app gets light status-bar icons and switching back restores dark icons`() {
        var dark by mutableStateOf(true)
        compose.setContent { EdgeToEdgeSystemBars(dark = dark) }
        compose.waitForIdle()
        assertTrue("dark theme on a light system shows light icons", lightIcons())
        dark = false
        compose.waitForIdle()
        assertFalse("light theme shows dark icons", lightIcons())
    }
}
