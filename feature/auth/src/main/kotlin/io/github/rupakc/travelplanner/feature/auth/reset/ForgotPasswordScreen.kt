package io.github.rupakc.travelplanner.feature.auth.reset

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
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

@Composable
internal fun ForgotPasswordRoute(onBackToLogin: () -> Unit, viewModel: ForgotPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForgotPasswordScreen(state, viewModel::onEvent, onBackToLogin)
}

/** "Forgot your password?": an email field and "Send reset link"; the answer never says whether the address exists. */
@Composable
internal fun ForgotPasswordScreen(
    state: ForgotPasswordUiState,
    onEvent: (ForgotPasswordEvent) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthCardScaffold(title = stringResource(R.string.auth_forgot_title), subtitle = null, modifier = modifier) {
        if (state.unavailable) {
            AuthStatusText(stringResource(R.string.auth_forgot_unavailable))
        } else if (state.sent) {
            AuthStatusText(stringResource(R.string.auth_forgot_sent))
        } else {
            Text(stringResource(R.string.auth_forgot_intro), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
            state.error?.let { AuthErrorBanner(it) }
            AuthTextField(
                value = state.email,
                onValueChange = { onEvent(ForgotPasswordEvent.EmailChanged(it)) },
                label = stringResource(R.string.auth_su_email),
                placeholder = stringResource(R.string.auth_su_email_placeholder),
                leadingIcon = Icons.Filled.Email,
                contentType = ContentType.EmailAddress,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onEvent(ForgotPasswordEvent.Submit) }),
            )
            TpButton(
                text = stringResource(if (state.loading) R.string.auth_forgot_sending else R.string.auth_forgot_submit),
                onClick = { onEvent(ForgotPasswordEvent.Submit) },
                enabled = state.canSubmit || state.loading,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
                tid = "forgot.submit",
            )
        }
        TpButton(
            stringResource(R.string.auth_check_back),
            onBackToLogin,
            variant = TpButtonVariant.Text,
            modifier = Modifier.fillMaxWidth(),
            tid = "auth.auth_check_back",
        )
    }
}
