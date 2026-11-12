package io.github.rupakc.travelplanner.feature.auth.verify

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.ui.AuthCardScaffold
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthStatusText

/** Check-email route: [onBackToLogin] returns to Login. */
@Composable
internal fun CheckEmailRoute(onBackToLogin: () -> Unit, viewModel: CheckEmailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckEmailScreen(state, viewModel::onEvent, onBackToLogin)
}

/** "We sent a link to <email>. It works for 30 minutes." with a resend button that waits 30 s between sends. */
@Composable
internal fun CheckEmailScreen(
    state: CheckEmailUiState,
    onEvent: (CheckEmailEvent) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthCardScaffold(title = stringResource(R.string.auth_check_title), subtitle = null, modifier = modifier) {
        Text(
            state.email?.let { stringResource(R.string.auth_check_sent, it) } ?: stringResource(R.string.auth_check_sent_no_email),
            style = TravelTheme.typography.bodyLarge,
        )
        Text(stringResource(R.string.auth_check_mail_hint), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        state.error?.let { AuthErrorBanner(it) }
        if (state.resent) AuthStatusText(stringResource(R.string.auth_check_resent))
        TpButton(
            text =
                if (state.cooldownSeconds > 0) {
                    stringResource(R.string.auth_check_resend_in, state.cooldownSeconds)
                } else {
                    stringResource(R.string.auth_check_resend)
                },
            onClick = { onEvent(CheckEmailEvent.Resend) },
            enabled = state.canResend,
            loading = state.sending,
            variant = TpButtonVariant.Tonal,
            modifier = Modifier.fillMaxWidth(),
            tid = "check_email.resend",
        )
        TpButton(stringResource(R.string.auth_check_back), onBackToLogin, variant = TpButtonVariant.Text, modifier = Modifier.fillMaxWidth())
    }
}
