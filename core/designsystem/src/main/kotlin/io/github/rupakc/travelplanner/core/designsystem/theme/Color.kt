package io.github.rupakc.travelplanner.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Material 3 light scheme from the web tokens (spec §5): teal primary, sky secondary, violet tertiary. */
internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Palette.Teal600,
        onPrimary = Palette.White,
        primaryContainer = Palette.Teal100,
        onPrimaryContainer = Palette.Teal900,
        inversePrimary = Palette.Teal400,
        secondary = Palette.Sky600,
        onSecondary = Palette.White,
        secondaryContainer = Palette.Sky100,
        onSecondaryContainer = Palette.Sky900,
        tertiary = Palette.Violet600,
        onTertiary = Palette.White,
        tertiaryContainer = Palette.Violet100,
        onTertiaryContainer = Palette.Violet900,
        error = Palette.Red700,
        onError = Palette.White,
        errorContainer = Palette.Red50,
        onErrorContainer = Palette.Red900,
        background = Palette.Gray50,
        onBackground = Palette.Gray900,
        surface = Palette.White,
        onSurface = Palette.Gray900,
        surfaceVariant = Palette.Slate100,
        onSurfaceVariant = Palette.Gray600,
        surfaceTint = Palette.Teal600,
        inverseSurface = Palette.Slate800,
        inverseOnSurface = Palette.Slate100,
        outline = Palette.Gray300,
        outlineVariant = Palette.Gray200,
        surfaceBright = Palette.White,
        surfaceDim = Palette.Gray100,
        surfaceContainerLowest = Palette.White,
        surfaceContainerLow = Palette.Gray50,
        surfaceContainer = Palette.Gray100,
        surfaceContainerHigh = Palette.Slate100,
        surfaceContainerHighest = Palette.Slate200,
    )

/** Dark scheme (no web reference; teal-400 primary per spec §5, slate surfaces, AA contrast). */
internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Palette.Teal400,
        onPrimary = Palette.Teal950,
        primaryContainer = Palette.Teal800,
        onPrimaryContainer = Palette.Teal100,
        inversePrimary = Palette.Teal600,
        secondary = Palette.Sky400,
        onSecondary = Palette.Sky950,
        secondaryContainer = Palette.Sky800,
        onSecondaryContainer = Palette.Sky100,
        tertiary = Palette.Violet400,
        onTertiary = Palette.Violet950,
        tertiaryContainer = Palette.Violet800,
        onTertiaryContainer = Palette.Violet100,
        error = Palette.Red400,
        onError = Palette.Red950,
        errorContainer = Palette.Red900,
        onErrorContainer = Palette.Red100,
        background = Palette.Slate950,
        onBackground = Palette.Slate100,
        surface = Palette.Slate900,
        onSurface = Palette.Slate100,
        surfaceVariant = Palette.Slate800,
        onSurfaceVariant = Palette.Slate400,
        surfaceTint = Palette.Teal400,
        inverseSurface = Palette.Slate100,
        inverseOnSurface = Palette.Slate800,
        outline = Palette.Slate600,
        outlineVariant = Palette.Slate700,
        surfaceBright = Palette.Slate800,
        surfaceDim = Palette.Slate950,
        surfaceContainerLowest = Palette.Slate950,
        surfaceContainerLow = Palette.Slate900,
        surfaceContainer = Palette.Slate900,
        surfaceContainerHigh = Palette.Slate800,
        surfaceContainerHighest = Palette.Slate700,
    )

/** Semantic colours Material 3 lacks: success / warning ("Estimate", over budget) / info. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
    val warningBorder: Color,
    val info: Color,
    val infoContainer: Color,
    val skeletonBase: Color,
    val skeletonHighlight: Color,
)

internal val LightExtendedColors =
    ExtendedColors(
        success = Palette.Green600,
        onSuccessContainer = Palette.Emerald700,
        successContainer = Palette.Green50,
        warning = Palette.Amber700,
        onWarningContainer = Palette.Amber700,
        warningContainer = Palette.Amber50,
        warningBorder = Palette.Amber200,
        info = Palette.Sky600,
        infoContainer = Palette.Sky50,
        skeletonBase = Palette.Slate100,
        skeletonHighlight = Palette.Slate200,
    )

internal val DarkExtendedColors =
    ExtendedColors(
        success = Palette.Emerald400,
        onSuccessContainer = Palette.Emerald300,
        successContainer = Palette.Emerald900,
        warning = Palette.Amber400,
        onWarningContainer = Palette.Amber300,
        warningContainer = Palette.Amber900,
        warningBorder = Palette.Amber700,
        info = Palette.Sky400,
        infoContainer = Palette.Sky900,
        skeletonBase = Palette.Slate800,
        skeletonHighlight = Palette.Slate700,
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
