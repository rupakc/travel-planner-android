package io.github.rupakc.travelplanner.feature.auth.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import io.github.rupakc.travelplanner.feature.auth.ui.AuthTextField
import io.github.rupakc.travelplanner.feature.auth.ui.PasswordTextField

/** Sign-up route: [onCheckEmail] gets the address the confirmation link went to; [onSignIn] goes back to Login. */
@Composable
internal fun SignUpRoute(
    onCheckEmail: (String) -> Unit,
    onSignIn: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SignUpEffect.CheckEmail -> onCheckEmail(effect.email)
            }
        }
    }
    SignUpScreen(state, viewModel::onEvent, onSignIn)
}

/** Username, email, password with live rule hints, display name and (invite-only servers) an invite code. */
@Composable
internal fun SignUpScreen(
    state: SignUpUiState,
    onEvent: (SignUpEvent) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val next = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) })
    AuthCardScaffold(
        title = stringResource(R.string.auth_su_title),
        subtitle = stringResource(R.string.auth_su_tagline),
        modifier = modifier,
        below = {
            Text(stringResource(R.string.auth_su_have_account), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            TpButton(stringResource(R.string.auth_su_sign_in), onSignIn, variant = TpButtonVariant.Text)
        },
    ) {
        state.error?.let { AuthErrorBanner(it) }
        AuthTextField(
            value = state.username,
            onValueChange = { onEvent(SignUpEvent.UsernameChanged(it)) },
            label = stringResource(R.string.auth_username),
            placeholder = stringResource(R.string.auth_su_username_placeholder),
            leadingIcon = Icons.Filled.Person,
            contentType = ContentType.NewUsername,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = next,
        )
        Text(stringResource(R.string.auth_su_username_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        AuthTextField(
            value = state.email,
            onValueChange = { onEvent(SignUpEvent.EmailChanged(it)) },
            label = stringResource(R.string.auth_su_email),
            placeholder = stringResource(R.string.auth_su_email_placeholder),
            leadingIcon = Icons.Filled.Email,
            contentType = ContentType.EmailAddress,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            keyboardActions = next,
        )
        PasswordTextField(
            value = state.password,
            onValueChange = { onEvent(SignUpEvent.PasswordChanged(it)) },
            label = stringResource(R.string.auth_password),
            placeholder = stringResource(R.string.auth_su_password_placeholder, state.minPasswordLength),
            visible = state.passwordVisible,
            onToggleVisible = { onEvent(SignUpEvent.TogglePasswordVisibility) },
            leadingIcon = Icons.Filled.Lock,
            contentType = ContentType.NewPassword,
            enabled = !state.loading,
            imeAction = ImeAction.Next,
            onImeAction = { focus.moveFocus(FocusDirection.Down) },
        )
        PasswordRulesList(state.passwordCheck, state.minPasswordLength, state.password.isNotEmpty())
        AuthTextField(
            value = state.displayName,
            onValueChange = { onEvent(SignUpEvent.DisplayNameChanged(it)) },
            label = stringResource(R.string.auth_su_display_name),
            placeholder = stringResource(R.string.auth_su_display_name_placeholder),
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(imeAction = if (state.inviteRequired) ImeAction.Next else ImeAction.Done),
            keyboardActions =
                if (state.inviteRequired) {
                    next
                } else {
                    KeyboardActions(
                        onDone = {
                            focus.clearFocus()
                            onEvent(SignUpEvent.Submit)
                        },
                    )
                },
        )
        if (state.inviteRequired) {
            AuthTextField(
                value = state.inviteCode,
                onValueChange = { onEvent(SignUpEvent.InviteChanged(it)) },
                label = stringResource(R.string.auth_su_invite),
                placeholder = stringResource(R.string.auth_su_invite_placeholder),
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focus.clearFocus()
                            onEvent(SignUpEvent.Submit)
                        },
                    ),
            )
        }
        TpButton(
            text = stringResource(if (state.loading) R.string.auth_su_submitting else R.string.auth_su_submit),
            onClick = { onEvent(SignUpEvent.Submit) },
            enabled = state.canSubmit || state.loading,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
            tid = "signup.submit",
        )
    }
}

/** The two live hints; each says met / not met in text and in its state description, never by colour alone. */
@Composable
internal fun PasswordRulesList(check: PasswordCheck, minLength: Int, started: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        RuleLine(stringResource(R.string.auth_su_rule_length, minLength), check.longEnough, started)
        RuleLine(stringResource(R.string.auth_su_rule_identity), check.distinctFromIdentity, started)
    }
}

@Composable
private fun RuleLine(text: String, met: Boolean, started: Boolean) {
    val state = stringResource(if (met && started) R.string.auth_rule_met else R.string.auth_rule_unmet)
    val mark = if (met && started) "✓" else "○"
    Text(
        "$mark $text",
        style = TravelTheme.typography.bodySmall,
        color = if (met && started) TravelTheme.colors.primary else TravelTheme.colors.onSurfaceVariant,
        modifier = Modifier.semantics { stateDescription = state },
    )
}
