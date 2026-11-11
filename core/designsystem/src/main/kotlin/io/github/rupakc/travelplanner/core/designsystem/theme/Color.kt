package io.github.rupakc.travelplanner.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3 light scheme from the web tokens (spec §5): teal primary, sky secondary, violet tertiary.
 * Primary is teal-700 (the web's hover shade) and secondary sky-700 so text on and in them meets WCAG AA;
 * outline is gray-500 so input borders reach 3:1. ContrastTest guards every pair.
 */
internal val LightColors: ColorScheme =
    lightColorScheme(
        primary = Palette.Teal700,
        onPrimary = Palette.White,
        primaryContainer = Palette.Teal100,
        onPrimaryContainer = Palette.Teal900,
        inversePrimary = Palette.Teal400,
        secondary = Palette.Sky700,
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
        surfaceTint = Palette.Teal700,
        inverseSurface = Palette.Slate800,
        inverseOnSurface = Palette.Slate100,
        outline = Palette.Gray500,
        outlineVariant = Palette.Gray200,
        surfaceBright = Palette.White,
        surfaceDim = Palette.Gray100,
        surfaceContainerLowest = Palette.White,
        surfaceContainerLow = Palette.Gray50,
        surfaceContainer = Palette.Gray100,
        surfaceContainerHigh = Palette.Slate100,
        surfaceContainerHighest = Palette.Slate200,
    )

/** Dark scheme: teal-400 primary; surfaces and neutrals from the web dark tokens (DarkPalette), AA contrast. */
internal val DarkColors: ColorScheme =
    darkColorScheme(
        primary = Palette.Teal400,
        onPrimary = Palette.Teal950,
        primaryContainer = Palette.Teal900,
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
        background = DarkPalette.Gray50,
        onBackground = DarkPalette.Gray900,
        surface = DarkPalette.White,
        onSurface = DarkPalette.Gray900,
        surfaceVariant = DarkPalette.Gray100,
        onSurfaceVariant = DarkPalette.Gray700,
        surfaceTint = Palette.Teal400,
        inverseSurface = DarkPalette.Gray900,
        inverseOnSurface = DarkPalette.Gray100,
        outline = DarkPalette.Gray600,
        outlineVariant = DarkPalette.Gray200,
        surfaceBright = DarkPalette.Gray200,
        surfaceDim = DarkPalette.Gray50,
        surfaceContainerLowest = DarkPalette.Gray50,
        surfaceContainerLow = DarkPalette.White,
        surfaceContainer = DarkPalette.Gray100,
        surfaceContainerHigh = DarkPalette.Gray200,
        surfaceContainerHighest = DarkPalette.Gray300,
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
        success = Palette.Emerald700,
        onSuccessContainer = Palette.Emerald700,
        successContainer = Palette.Green50,
        warning = Palette.Amber700,
        onWarningContainer = Palette.Amber700,
        warningContainer = Palette.Amber50,
        warningBorder = Palette.Amber200,
        info = Palette.Sky700,
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
        infoContainer = Palette.Sky950,
        skeletonBase = DarkPalette.Gray100,
        skeletonHighlight = DarkPalette.Gray200,
    )

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
