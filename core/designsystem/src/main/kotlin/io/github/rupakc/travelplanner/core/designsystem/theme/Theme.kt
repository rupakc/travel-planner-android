package io.github.rupakc.travelplanner.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode

/**
 * The app theme. [dynamicColor] (Android 12+) is off by default to keep the brand (spec §5).
 * Section accents and extended colours stay brand colours even with dynamic colour.
 */
@Composable
fun TravelTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val accents = remember(dark) { SectionAccents(dark) }
    CompositionLocalProvider(
        LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors,
        LocalSectionAccents provides accents,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(colorScheme = colors, typography = TravelTypography, shapes = TravelShapes, content = content)
    }
}

/** Accessors: `TravelTheme.colors.primary`, `TravelTheme.spacing.lg`, `TravelTheme.sectionAccents[SectionId.HOTELS]`. */
object TravelTheme {
    val colors: ColorScheme
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme

    val typography: Typography
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes

    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    val sectionAccents: SectionAccents
        @Composable @ReadOnlyComposable
        get() = LocalSectionAccents.current
}
