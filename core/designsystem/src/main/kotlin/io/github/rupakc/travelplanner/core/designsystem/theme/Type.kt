package io.github.rupakc.travelplanner.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import io.github.rupakc.travelplanner.core.designsystem.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFamily(name: String, vararg weights: FontWeight): FontFamily =
    FontFamily(weights.map { Font(googleFont = GoogleFont(name), fontProvider = provider, weight = it) })

/** Sora 600/700/800 for display + headline; falls back to the system sans when the provider is unavailable. */
internal val Sora: FontFamily = googleFamily("Sora", FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)

/** Inter 400/500/600/700 for title, body, label. */
internal val Inter: FontFamily = googleFamily("Inter", FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight = FontWeight.Bold) = copy(fontFamily = Sora, fontWeight = weight, letterSpacing = (-0.25).sp)

private fun TextStyle.body(weight: FontWeight? = null) = copy(fontFamily = Inter, fontWeight = weight ?: fontWeight)

/** Web scale: hero 3xl–5xl bold, page h1 2xl bold, section title base semibold, body sm, meta xs. */
internal val TravelTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.display(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.display(),
    headlineLarge = base.headlineLarge.display(),
    headlineMedium = base.headlineMedium.display(),
    headlineSmall = base.headlineSmall.display(FontWeight.SemiBold),
    titleLarge = base.titleLarge.body(FontWeight.SemiBold),
    titleMedium = base.titleMedium.body(FontWeight.SemiBold),
    titleSmall = base.titleSmall.body(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.body(),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body(FontWeight.SemiBold),
    labelMedium = base.labelMedium.body(FontWeight.Medium),
    labelSmall = base.labelSmall.body(FontWeight.SemiBold),
)
