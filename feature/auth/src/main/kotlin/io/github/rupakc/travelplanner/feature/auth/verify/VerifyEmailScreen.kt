package io.github.rupakc.travelplanner.feature.auth.verify

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.ui.AuthCardScaffold
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthStatusText
import io.github.rupakc.travelplanner.feature.auth.ui.AuthTextField

/**
 * Verify route. The token is the route argument (deep link); without one the user pastes the link or code.
 * [onDone] leaves the screen: after a stored session the app's auth gate has already moved on, so it matters
 * for the confirmation-only case and for "Back to sign in".
 */
@Composable
internal fun VerifyEmailRoute(onDone: () -> Unit, viewModel: VerifyEmailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VerifyEmailScreen(state, viewModel::onEvent, onDone)
}

@Composable
internal fun VerifyEmailScreen(
    state: VerifyEmailUiState,
    onEvent: (VerifyEmailEvent) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        when (state.phase) {
            VerifyPhase.SignedIn, VerifyPhase.Confirmed -> R.string.auth_verify_confirmed
            VerifyPhase.Working -> R.string.auth_verify_working
            VerifyPhase.ConfirmSwitch -> R.string.auth_verify_switch_title
            else -> R.string.auth_verify_paste_title
        }
    AuthCardScaffold(title = stringResource(title), subtitle = null, modifier = modifier) {
        state.error?.let { AuthErrorBanner(it) }
        when (state.phase) {
            VerifyPhase.Working -> {
                AuthStatusText(stringResource(R.string.auth_verify_working))
            }

            VerifyPhase.ConfirmSwitch -> {
                AuthStatusText(stringResource(R.string.auth_verify_switch_body, state.currentUsername, state.newUsername))
                TpButton(
                    stringResource(R.string.auth_verify_switch_confirm),
                    { onEvent(VerifyEmailEvent.SignOutAndVerify) },
                    modifier = Modifier.fillMaxWidth(),
                    tid = "verify.switch_confirm",
                )
                TpButton(
                    stringResource(R.string.auth_verify_switch_cancel),
                    { onEvent(VerifyEmailEvent.CancelSwitch) },
                    variant = TpButtonVariant.Text,
                    modifier = Modifier.fillMaxWidth(),
                    tid = "verify.switch_cancel",
                )
            }

            VerifyPhase.SignedIn, VerifyPhase.Confirmed -> {
                TpButton(stringResource(R.string.auth_verify_to_account), onDone, modifier = Modifier.fillMaxWidth(), tid = "verify.done")
            }

            VerifyPhase.NeedsToken, VerifyPhase.Failed -> {
                if (state.canResend) ResendBlock(state, onEvent)
                PasteBlock(state, onEvent)
                TpButton(
                    stringResource(R.string.auth_check_back),
                    onDone,
                    variant = TpButtonVariant.Text,
                    modifier = Modifier.fillMaxWidth(),
                    tid = "auth.auth_check_back",
                )
            }
        }
    }
}

@Composable
private fun PasteBlock(state: VerifyEmailUiState, onEvent: (VerifyEmailEvent) -> Unit) {
    AuthTextField(
        value = state.pasted,
        onValueChange = { onEvent(VerifyEmailEvent.PastedChanged(it)) },
        label = stringResource(R.string.auth_link_paste_label),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onEvent(VerifyEmailEvent.SubmitPasted) }),
    )
    TpButton(
        stringResource(R.string.auth_verify_confirm),
        { onEvent(VerifyEmailEvent.SubmitPasted) },
        enabled = state.canSubmitPaste,
        modifier = Modifier.fillMaxWidth(),
        tid = "verify.submit",
    )
}

@Composable
private fun ResendBlock(state: VerifyEmailUiState, onEvent: (VerifyEmailEvent) -> Unit) {
    AuthTextField(
        value = state.email,
        onValueChange = { onEvent(VerifyEmailEvent.EmailChanged(it)) },
        label = stringResource(R.string.auth_su_email),
        placeholder = stringResource(R.string.auth_su_email_placeholder),
        leadingIcon = Icons.Filled.Email,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, autoCorrectEnabled = false),
    )
    TpButton(
        text =
            if (state.cooldownSeconds > 0) {
                stringResource(R.string.auth_check_resend_in, state.cooldownSeconds)
            } else {
                stringResource(R.string.auth_verify_resend)
            },
        onClick = { onEvent(VerifyEmailEvent.Resend) },
        enabled = state.canSendResend,
        loading = state.sending,
        variant = TpButtonVariant.Tonal,
        modifier = Modifier.fillMaxWidth(),
        tid = "verify.resend",
    )
    if (state.resent) AuthStatusText(stringResource(R.string.auth_verify_resent))
}
