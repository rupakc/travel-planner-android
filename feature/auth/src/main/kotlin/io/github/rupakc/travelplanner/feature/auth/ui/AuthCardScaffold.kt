package io.github.rupakc.travelplanner.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R

/** Backdrop + header + one card, the shape of every account screen (sign-up, check email, verify, forgot, reset). */
@Composable
internal fun AuthCardScaffold(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    below: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    AuthBackdrop(modifier) {
        AuthHeader(title = title, subtitle = subtitle, icon = painterResource(R.drawable.ic_flight))
        TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Large) {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg), content = content)
        }
        below()
    }
}

/** A short status line announced politely (sent, confirmed, done). */
@Composable
internal fun AuthStatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TravelTheme.typography.bodyMedium,
        color = TravelTheme.colors.onSurface,
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Resolves an [AuthMessage] to text. */
@Composable
internal fun AuthMessage.text(): String =
    when (this) {
        is AuthMessage.Server -> text
        is AuthMessage.Local -> stringResource(res)
    }
