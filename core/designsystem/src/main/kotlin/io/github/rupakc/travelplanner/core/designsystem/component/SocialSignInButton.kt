package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.R
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode

/** Social sign-in providers (ids match the backend's `/api/auth/providers`). Brand names are not translated. */
enum class SocialProvider(val id: String, val displayName: String) {
    APPLE("apple", "Apple"),
    GOOGLE("google", "Google"),
    FACEBOOK("facebook", "Facebook"),
    GITHUB("github", "GitHub"),
}

private data class BrandStyle(val container: Color, val content: Color, val border: Color, val tintIcon: Boolean)

/**
 * The web's literal brand colours (`SocialSignInButtons.jsx`): each provider's published light button and
 * its dark-theme variant. Literal on purpose, so palette changes never recolour a brand.
 */
private object SocialBrandColors {
    fun style(provider: SocialProvider, dark: Boolean): BrandStyle =
        when (provider) {
            SocialProvider.APPLE -> {
                if (dark) {
                    BrandStyle(Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFD4D4D4), true)
                } else {
                    BrandStyle(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF000000), true)
                }
            }

            SocialProvider.GOOGLE -> {
                if (dark) {
                    BrandStyle(Color(0xFF131314), Color(0xFFE3E3E3), Color(0xFF8E918F), false)
                } else {
                    BrandStyle(Color(0xFFFFFFFF), Color(0xFF1F1F1F), Color(0xFF747775), false)
                }
            }

            SocialProvider.FACEBOOK -> {
                BrandStyle(Color(0xFF1877F2), Color(0xFFFFFFFF), Color(0xFF1877F2), false)
            }

            SocialProvider.GITHUB -> {
                if (dark) {
                    BrandStyle(Color(0xFFF6F8FA), Color(0xFF24292F), Color(0xFFD0D7DE), true)
                } else {
                    BrandStyle(Color(0xFF24292F), Color(0xFFFFFFFF), Color(0xFF24292F), true)
                }
            }
        }
}

@DrawableRes
private fun SocialProvider.icon(): Int =
    when (this) {
        SocialProvider.APPLE -> R.drawable.ic_brand_apple
        SocialProvider.GOOGLE -> R.drawable.ic_brand_google
        SocialProvider.FACEBOOK -> R.drawable.ic_brand_facebook
        SocialProvider.GITHUB -> R.drawable.ic_brand_github
    }

/**
 * Full-width "Continue with X" button in the provider's brand colours. [label] is a resolved string.
 * Presentation only: the caller decides whether the provider is available. Reports TAP
 * `auth.provider.<id>` to [LocalUiEventSink], then calls [onClick].
 */
@Composable
fun SocialSignInButton(provider: SocialProvider, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sink = LocalUiEventSink.current
    val style = SocialBrandColors.style(provider, dark = TravelTheme.colors.surface.luminance() < 0.5f)
    Button(
        onClick = {
            sink.report(UiEventKind.TAP, "auth.provider.${provider.id}")
            onClick()
        },
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget),
        shape = TravelShape.Input,
        colors = ButtonDefaults.buttonColors(containerColor = style.container, contentColor = style.content),
        border = BorderStroke(1.dp, style.border),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(provider.icon()),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (style.tintIcon) style.content else Color.Unspecified,
            )
            Text(label, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AllProviders() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialProvider.entries.forEach { SocialSignInButton(it, "Continue with ${it.displayName}", onClick = {}) }
    }
}

@Preview(showBackground = true)
@Composable
private fun SocialSignInButtonsLightPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { AllProviders() }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun SocialSignInButtonsDarkPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { AllProviders() }
}
