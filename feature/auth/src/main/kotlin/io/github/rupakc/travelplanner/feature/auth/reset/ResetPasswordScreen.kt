package io.github.rupakc.travelplanner.feature.auth.reset

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.signup.PasswordRules
import io.github.rupakc.travelplanner.feature.auth.signup.PasswordRulesList
import io.github.rupakc.travelplanner.feature.auth.ui.AuthCardScaffold
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthStatusText
import io.github.rupakc.travelplanner.feature.auth.ui.AuthTextField
import io.github.rupakc.travelplanner.feature.auth.ui.PasswordTextField

/**
 * Reset route. The token is the route argument (deep link); without one the user pastes the link or code.
 * [onRequestNew] opens Forgot password, [onDone] leaves after success (the session is stored, the gate moves on).
 */
@Composable
internal fun ResetPasswordRoute(onDone: () -> Unit, onRequestNew: () -> Unit, viewModel: ResetPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResetPasswordScreen(state, viewModel::onEvent, onDone, onRequestNew)
}

@Composable
internal fun ResetPasswordScreen(
    state: ResetPasswordUiState,
    onEvent: (ResetPasswordEvent) -> Unit,
    onDone: () -> Unit,
    onRequestNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthCardScaffold(title = stringResource(R.string.auth_reset_title), subtitle = null, modifier = modifier) {
        state.error?.let { AuthErrorBanner(it) }
        when {
            state.done -> {
                AuthStatusText(stringResource(R.string.auth_reset_done))
                TpButton(stringResource(R.string.auth_reset_continue), onDone, modifier = Modifier.fillMaxWidth(), tid = "reset.done")
            }

            state.token == null -> {
                Text(stringResource(R.string.auth_reset_missing_token), style = TravelTheme.typography.bodyMedium)
                AuthTextField(
                    value = state.pasted,
                    onValueChange = { onEvent(ResetPasswordEvent.PastedChanged(it)) },
                    label = stringResource(R.string.auth_link_paste_label),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onDone = { onEvent(ResetPasswordEvent.UsePasted) }),
                )
                TpButton(
                    stringResource(R.string.auth_reset_use_link),
                    { onEvent(ResetPasswordEvent.UsePasted) },
                    enabled = state.pasted.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TpButton(stringResource(R.string.auth_reset_request_new), onRequestNew, variant = TpButtonVariant.Text, modifier = Modifier.fillMaxWidth())
            }

            else -> {
                PasswordTextField(
                    value = state.password,
                    onValueChange = { onEvent(ResetPasswordEvent.PasswordChanged(it)) },
                    label = stringResource(R.string.auth_new_password),
                    visible = state.passwordVisible,
                    onToggleVisible = { onEvent(ResetPasswordEvent.TogglePasswordVisibility) },
                    leadingIcon = Icons.Filled.Lock,
                    contentType = ContentType.NewPassword,
                    enabled = !state.loading,
                    imeAction = ImeAction.Next,
                )
                PasswordRulesList(
                    PasswordRules.check(state.password, state.minPasswordLength),
                    state.minPasswordLength,
                    state.password.isNotEmpty(),
                )
                PasswordTextField(
                    value = state.confirm,
                    onValueChange = { onEvent(ResetPasswordEvent.ConfirmChanged(it)) },
                    label = stringResource(R.string.auth_confirm_password),
                    visible = state.passwordVisible,
                    onToggleVisible = { onEvent(ResetPasswordEvent.TogglePasswordVisibility) },
                    leadingIcon = Icons.Filled.Lock,
                    contentType = ContentType.NewPassword,
                    enabled = !state.loading,
                    onImeAction = { onEvent(ResetPasswordEvent.Submit) },
                )
                if (state.mismatch) AuthStatusText(stringResource(R.string.auth_error_mismatch))
                TpButton(
                    text = stringResource(if (state.loading) R.string.auth_saving else R.string.auth_reset_submit),
                    onClick = { onEvent(ResetPasswordEvent.Submit) },
                    enabled = state.canSubmit || state.loading,
                    loading = state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    tid = "reset.submit",
                )
            }
        }
    }
}
