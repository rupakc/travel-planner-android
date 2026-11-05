package io.github.rupakc.travelplanner.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

/**
 * Opens [url] in a Custom Tab tinted with the app colour (spec §2: "Book" always means
 * open the provider's page). Falls back to any browser; ignores non-http(s) URLs.
 */
fun openInCustomTab(context: Context, url: String, toolbarColor: Int? = null): Boolean {
    val uri = url.toUri()
    if (uri.scheme != "https" && uri.scheme != "http") return false
    val intent =
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .apply { toolbarColor?.let { setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(it).build()) } }
            .build()
    return try {
        intent.launchUrl(context, uri)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

/** Remembered opener for composables: `val open = rememberLinkOpener(); open(url)`. */
@Composable
fun rememberLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    val color = TravelTheme.colors.surface.toArgb()
    return { url -> openInCustomTab(context, url, color) }
}

/** "View & book" / "Open <provider>" button; hidden when [url] is null/blank. */
@Composable
fun BookingLinkButton(
    url: String?,
    modifier: Modifier = Modifier,
    providerName: String? = null,
    variant: TpButtonVariant = TpButtonVariant.Secondary,
) {
    if (url.isNullOrBlank()) return
    val open = rememberLinkOpener()
    val label = providerName?.let { stringResource(R.string.booking_open, it) } ?: stringResource(R.string.booking_open_generic)
    TpButton(label, onClick = { open(url) }, modifier = modifier, variant = variant, leadingIcon = Icons.AutoMirrored.Filled.ExitToApp)
}
