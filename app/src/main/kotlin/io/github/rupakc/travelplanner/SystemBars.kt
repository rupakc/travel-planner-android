package io.github.rupakc.travelplanner

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode

/** True when the app draws dark: the Settings theme, or the system's when it follows the system. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

/**
 * Edge-to-edge system bars that follow the app's theme (not only the system's): light icons on a dark
 * app, dark icons on a light one. `enableEdgeToEdge()` alone reads the system night mode, so with
 * Settings → Dark on a light phone the status-bar icons stayed dark on the dark top bar.
 */
@Composable
fun EdgeToEdgeSystemBars(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, dark) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
        )
        onDispose {}
    }
}

// androidx.activity's own defaults for the three-button navigation bar scrim.
private val LIGHT_NAV_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_NAV_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
