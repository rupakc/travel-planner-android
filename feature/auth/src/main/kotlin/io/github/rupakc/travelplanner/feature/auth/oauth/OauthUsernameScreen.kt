package io.github.rupakc.travelplanner.feature.auth.oauth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.rupakc.travelplanner.feature.auth.ui.AuthCardScaffold
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthTextField

@Composable
internal fun OauthUsernameRoute(onCancel: () -> Unit, viewModel: OauthUsernameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.hasDraft) { if (!state.hasDraft) onCancel() }
    OauthUsernameScreen(
        state = state,
        onEvent = {
            viewModel.onEvent(it)
            if (it == OauthUsernameEvent.Cancel) onCancel()
        },
    )
}

/** "Choose a username" after a first social sign-in. */
@Composable
internal fun OauthUsernameScreen(state: OauthUsernameUiState, onEvent: (OauthUsernameEvent) -> Unit, modifier: Modifier = Modifier) {
    AuthCardScaffold(title = stringResource(R.string.auth_oauth_username_title), subtitle = null, modifier = modifier) {
        Text(
            stringResource(R.string.auth_oauth_username_intro),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        state.error?.let { AuthErrorBanner(it) }
        AuthTextField(
            value = state.username,
            onValueChange = { onEvent(OauthUsernameEvent.UsernameChanged(it)) },
            label = stringResource(R.string.auth_username),
            placeholder = stringResource(R.string.auth_su_username_placeholder),
            leadingIcon = Icons.Filled.Person,
            contentType = ContentType.NewUsername,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(imeAction = if (state.inviteRequired) ImeAction.Next else ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onEvent(OauthUsernameEvent.Submit) }),
        )
        Text(stringResource(R.string.auth_su_username_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        if (state.inviteRequired) {
            AuthTextField(
                value = state.inviteCode,
                onValueChange = { onEvent(OauthUsernameEvent.InviteChanged(it)) },
                label = stringResource(R.string.auth_su_invite),
                placeholder = stringResource(R.string.auth_su_invite_placeholder),
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onEvent(OauthUsernameEvent.Submit) }),
            )
        }
        TpButton(
            text = stringResource(if (state.loading) R.string.auth_oauth_username_submitting else R.string.auth_oauth_username_submit),
            onClick = { onEvent(OauthUsernameEvent.Submit) },
            enabled = state.canSubmit || state.loading,
            loading = state.loading,
            modifier = Modifier.fillMaxWidth(),
            tid = "oauth.username.submit",
        )
        TpButton(
            stringResource(R.string.auth_oauth_username_cancel),
            { onEvent(OauthUsernameEvent.Cancel) },
            variant = TpButtonVariant.Text,
            modifier = Modifier.fillMaxWidth(),
            tid = "auth.auth_oauth_username_cancel",
        )
    }
}
