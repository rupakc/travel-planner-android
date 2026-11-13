package io.github.rupakc.travelplanner.feature.auth.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.SocialProvider
import io.github.rupakc.travelplanner.core.designsystem.component.SocialSignInButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.server.ServerEvent
import io.github.rupakc.travelplanner.feature.auth.server.ServerSheet
import io.github.rupakc.travelplanner.feature.auth.server.ServerViewModel
import io.github.rupakc.travelplanner.feature.auth.ui.AuthBackdrop
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthHeader
import io.github.rupakc.travelplanner.feature.auth.ui.AuthTextField
import io.github.rupakc.travelplanner.feature.auth.ui.PasswordTextField

/** Login route: wires [LoginViewModel]; [onLoggedIn] fires once after a successful sign-in. */
@Composable
internal fun LoginRoute(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
    serverViewModel: ServerViewModel = hiltViewModel(),
    onSignUp: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
    onNeedsUsername: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val server by serverViewModel.state.collectAsStateWithLifecycle()
    var showServer by rememberSaveable { mutableStateOf(false) }
    val openLink = rememberLinkOpener()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onLoggedIn() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LoginEffect.OpenUrl -> openLink(effect.url)
                LoginEffect.NeedsUsername -> onNeedsUsername()
            }
        }
    }
    LaunchedEffect(server.done) { if (server.done) showServer = false }
    LoginScreen(
        state = state,
        onEvent = viewModel::onEvent,
        serverUrl = server.current,
        onOpenServer = {
            serverViewModel.onEvent(ServerEvent.Open)
            showServer = true
        },
        onSignUp = onSignUp,
        onForgotPassword = onForgotPassword,
    )
    if (showServer) {
        ServerSheet(
            state = server,
            onEvent = serverViewModel::onEvent,
            onDismiss = {
                serverViewModel.onEvent(ServerEvent.Dismiss)
                showServer = false
            },
        )
    }
}

/** Test tag of the always-composed polite live region that holds the "coming soon" note. */
internal const val COMING_SOON_REGION_TAG = "auth.coming-soon-region"

/**
 * Web LoginPage: logo, "Travel Planner", "Plan your perfect trip", card with the four social buttons,
 * the "coming soon" note, an "or" divider, username/password and Sign In; then the footer with Privacy choices.
 */
@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onEvent: (LoginEvent) -> Unit,
    modifier: Modifier = Modifier,
    serverUrl: String = "",
    onOpenServer: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onForgotPassword: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    AuthBackdrop(modifier) {
        AuthHeader(
            title = stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name),
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
                state.error?.let { error ->
                    AuthErrorBanner(error)
                    if ((error as? AuthMessage.Local)?.suggestServer == true) {
                        TpButton(stringResource(R.string.auth_server), onOpenServer, variant = TpButtonVariant.Tonal, tid = "auth.auth_server")
                    }
                }
                state.pending?.let { PendingAccountCard(it, onEvent) }
                SocialSignIn(state.comingSoonProvider, onEvent)
                OrDivider()
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
                if (state.resetAvailable) {
                    TpButton(
                        stringResource(R.string.auth_su_login_forgot),
                        onForgotPassword,
                        variant = TpButtonVariant.Text,
                        tid = "login.forgot",
                    )
                }
                TpButton(
                    text = stringResource(if (state.loading) R.string.auth_signing_in else R.string.auth_sign_in),
                    onClick = { onEvent(LoginEvent.Submit) },
                    enabled = state.canSubmit || state.loading,
                    loading = state.loading,
                    modifier = Modifier.fillMaxWidth(),
                    tid = "auth.auth_signing_in",
                )
            }
        }
        if (state.signupAvailable) {
            TpButton(
                stringResource(R.string.auth_su_login_create),
                onSignUp,
                variant = TpButtonVariant.Tonal,
                modifier = Modifier.fillMaxWidth(),
                tid = "login.create_account",
            )
        } else {
            Text(
                stringResource(R.string.auth_no_registration),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        TpButton(
            text =
                if (serverUrl.isBlank()) {
                    stringResource(R.string.auth_server_button_unset)
                } else {
                    stringResource(R.string.auth_server_button, serverUrl)
                },
            onClick = onOpenServer,
            variant = TpButtonVariant.Text,
            tid = "auth.auth_server_button_unset",
        )
        TpButton(
            text = stringResource(R.string.auth_privacy_choices),
            onClick = { onEvent(LoginEvent.OpenPrivacyChoices) },
            variant = TpButtonVariant.Text,
            tid = "privacy.choices",
        )
    }
}

/** `403 account_pending`: the account still waits for its confirmation email; resend needs the full address. */
@Composable
private fun PendingAccountCard(pending: PendingAccount, onEvent: (LoginEvent) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TravelTheme.colors.secondaryContainer, TravelShape.Input)
            .padding(TravelTheme.spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Text(
            stringResource(R.string.auth_su_pending_title),
            style = TravelTheme.typography.titleSmall,
            color = TravelTheme.colors.onSecondaryContainer,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            pending.hint?.let { stringResource(R.string.auth_su_pending_body, it) } ?: stringResource(R.string.auth_su_pending_no_hint),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSecondaryContainer,
        )
        pending.error?.let { AuthErrorBanner(it) }
        if (pending.sent) {
            Text(stringResource(R.string.auth_su_pending_sent), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSecondaryContainer)
        }
        AuthTextField(
            value = pending.email,
            onValueChange = { onEvent(LoginEvent.PendingEmailChanged(it)) },
            label = stringResource(R.string.auth_su_pending_email),
            placeholder = stringResource(R.string.auth_su_email_placeholder),
            contentType = ContentType.EmailAddress,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onEvent(LoginEvent.ResendPending) }),
        )
        TpButton(
            text =
                when {
                    pending.sending -> stringResource(R.string.auth_su_pending_sending)
                    pending.cooldownSeconds > 0 -> stringResource(R.string.auth_check_resend_in, pending.cooldownSeconds)
                    else -> stringResource(R.string.auth_su_pending_resend)
                },
            onClick = { onEvent(LoginEvent.ResendPending) },
            enabled = pending.canResend,
            loading = pending.sending,
            variant = TpButtonVariant.Tonal,
            modifier = Modifier.fillMaxWidth(),
            tid = "login.pending_resend",
        )
    }
}

/** The four providers in the web order, then the "coming soon" note in a live region that is always composed. */
@Composable
private fun SocialSignIn(comingSoonProvider: String?, onEvent: (LoginEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SocialProvider.entries.forEach { provider ->
            SocialSignInButton(
                provider = provider,
                label = stringResource(R.string.auth_continue_with, provider.displayName),
                onClick = { onEvent(LoginEvent.SocialProviderTapped(provider)) },
            )
        }
        Box(Modifier.fillMaxWidth().testTag(COMING_SOON_REGION_TAG).semantics { liveRegion = LiveRegionMode.Polite }) {
            comingSoonProvider?.let { ComingSoonNote(it) }
        }
    }
}

@Composable
private fun ComingSoonNote(provider: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(TravelTheme.colors.secondaryContainer, TravelShape.Input)
            .padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = TravelTheme.colors.secondary)
        Text(
            stringResource(R.string.auth_coming_soon, provider),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSecondaryContainer,
        )
    }
}

/** "— or —" between the social buttons and the form (web `role="separator"`). */
@Composable
private fun OrDivider() {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = TravelTheme.colors.outlineVariant)
        Text(
            stringResource(R.string.auth_divider_or),
            style = TravelTheme.typography.labelSmall,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f), color = TravelTheme.colors.outlineVariant)
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

@Preview(name = "Login – coming soon, dark")
@Composable
private fun LoginPreviewDarkComingSoon() {
    TravelTheme(themeMode = ThemeMode.DARK) { LoginScreen(LoginUiState(comingSoonProvider = "Google"), {}) }
}

@Preview(name = "Login – coming soon, 200 %", fontScale = 2f)
@Composable
private fun LoginPreviewLargeFont() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { LoginScreen(LoginUiState(comingSoonProvider = "GitHub"), {}) }
}

@Preview(name = "Login – loading")
@Composable
private fun LoginPreviewLoading() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { LoginScreen(LoginUiState(username = "alice", password = "secret", loading = true), {}) }
}
