package io.github.rupakc.travelplanner.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.ui.AuthBackdrop
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthHeader
import io.github.rupakc.travelplanner.feature.auth.ui.AuthTextField
import io.github.rupakc.travelplanner.feature.auth.ui.PasswordTextField

/** Login route: wires [LoginViewModel]; [onLoggedIn] fires once after a successful sign-in. */
@Composable
internal fun LoginRoute(onLoggedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onLoggedIn() }
    LoginScreen(state, viewModel::onEvent)
}

/** Web LoginPage: logo, "Travel Planner", "Plan your perfect trip", card with username/password, Sign In. */
@Composable
internal fun LoginScreen(state: LoginUiState, onEvent: (LoginEvent) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    AuthBackdrop(modifier) {
        AuthHeader(
            title = stringResource(R.string.auth_app_name),
            subtitle = stringResource(R.string.auth_tagline),
            icon = painterResource(R.drawable.ic_flight),
        )
        TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Large) {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
                Text(
                    stringResource(R.string.auth_login_title),
                    style = TravelTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                state.error?.let { AuthErrorBanner(it) }
                AuthTextField(
                    value = state.username,
                    onValueChange = { onEvent(LoginEvent.UsernameChanged(it)) },
                    label = stringResource(R.string.auth_username),
                    placeholder = stringResource(R.string.auth_username_placeholder),
                    leadingIcon = Icons.Filled.Person,
                    contentType = ContentType.Username,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                PasswordTextField(
                    value = state.password,
                    onValueChange = { onEvent(LoginEvent.PasswordChanged(it)) },
                    label = stringResource(R.string.auth_password),
                    placeholder = stringResource(R.string.auth_password_placeholder),
                    visible = state.passwordVisible,
                    onToggleVisible = { onEvent(LoginEvent.TogglePasswordVisibility) },
                    leadingIcon = Icons.Filled.Lock,
                    enabled = !state.loading,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        focus.clearFocus()
                        onEvent(LoginEvent.Submit)
                    },
                )
                TpButton(
                    text = stringResource(if (state.loading) R.string.auth_signing_in else R.string.auth_sign_in),
                    onClick = { onEvent(LoginEvent.Submit) },
                    enabled = state.canSubmit || state.loading,
                    loading = state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            stringResource(R.string.auth_no_registration),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Login – light")
@Composable
private fun LoginPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { LoginScreen(LoginUiState(username = "alice"), {}) }
}

@Preview(name = "Login – error, dark")
@Composable
private fun LoginPreviewDarkError() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        LoginScreen(LoginUiState(username = "alice", password = "secret", error = AuthMessage.Server("Invalid username or password")), {})
    }
}

@Preview(name = "Login – loading")
@Composable
private fun LoginPreviewLoading() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { LoginScreen(LoginUiState(username = "alice", password = "secret", loading = true), {}) }
}
