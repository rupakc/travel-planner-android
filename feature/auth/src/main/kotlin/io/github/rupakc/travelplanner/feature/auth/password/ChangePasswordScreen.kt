package io.github.rupakc.travelplanner.feature.auth.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.ui.AuthBackdrop
import io.github.rupakc.travelplanner.feature.auth.ui.AuthErrorBanner
import io.github.rupakc.travelplanner.feature.auth.ui.AuthHeader
import io.github.rupakc.travelplanner.feature.auth.ui.PasswordTextField

/**
 * Change password. [forced] = temporary-password gate screen (web ChangePasswordPage copy, no back);
 * otherwise the voluntary Settings flow with a top bar and back. [onDone] fires once after success.
 */
@Composable
internal fun ChangePasswordRoute(
    forced: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    ChangePasswordScreen(state, forced, viewModel::onEvent, onBack = onBack)
}

@Composable
internal fun ChangePasswordScreen(
    state: ChangePasswordUiState,
    forced: Boolean,
    onEvent: (ChangePasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    if (forced) {
        AuthBackdrop(modifier) {
            AuthHeader(
                title = stringResource(R.string.auth_change_password_title),
                subtitle = state.username?.let { stringResource(R.string.auth_change_password_welcome, it) },
                icon = rememberVectorPainter(Icons.Filled.Lock),
            )
            PasswordForm(state, forced = true, onEvent = onEvent)
        }
    } else {
        Scaffold(
            modifier = modifier,
            topBar = { TpTopBar(stringResource(R.string.auth_change_password_voluntary_title), onBack = onBack) },
        ) { padding ->
            AuthBackdrop(Modifier.padding(padding).consumeWindowInsets(padding)) {
                Text(
                    stringResource(R.string.auth_change_password_voluntary_message),
                    style = TravelTheme.typography.bodyLarge,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
                if (state.done) {
                    PasswordUpdated(onBack)
                } else {
                    PasswordForm(state, forced = false, onEvent = onEvent)
                }
            }
        }
    }
}

/** Voluntary flow success: confirmation (announced) + Done back to Settings. */
@Composable
private fun PasswordUpdated(onBack: () -> Unit) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            Text(
                stringResource(R.string.auth_password_updated),
                style = TravelTheme.typography.titleMedium,
                color = TravelTheme.extendedColors.success,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TpButton(stringResource(R.string.auth_done), onClick = onBack, modifier = Modifier.fillMaxWidth(), tid = "auth.auth_done")
        }
    }
}

@Composable
private fun PasswordForm(state: ChangePasswordUiState, forced: Boolean, onEvent: (ChangePasswordEvent) -> Unit) {
    val focus = LocalFocusManager.current
    val next: () -> Unit = { focus.moveFocus(FocusDirection.Down) }
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            state.error?.let { AuthErrorBanner(it) }
            PasswordTextField(
                value = state.current,
                onValueChange = { onEvent(ChangePasswordEvent.Changed(PasswordField.CURRENT, it)) },
                label = stringResource(if (forced) R.string.auth_temporary_password else R.string.auth_current_password),
                visible = PasswordField.CURRENT in state.visible,
                onToggleVisible = { onEvent(ChangePasswordEvent.ToggleVisibility(PasswordField.CURRENT)) },
                enabled = !state.loading,
                imeAction = ImeAction.Next,
                onImeAction = next,
            )
            PasswordTextField(
                value = state.new,
                onValueChange = { onEvent(ChangePasswordEvent.Changed(PasswordField.NEW, it)) },
                label = stringResource(R.string.auth_new_password),
                placeholder = stringResource(R.string.auth_new_password_placeholder),
                visible = PasswordField.NEW in state.visible,
                onToggleVisible = { onEvent(ChangePasswordEvent.ToggleVisibility(PasswordField.NEW)) },
                contentType = ContentType.NewPassword,
                enabled = !state.loading,
                imeAction = ImeAction.Next,
                onImeAction = next,
            )
            PasswordTextField(
                value = state.confirm,
                onValueChange = { onEvent(ChangePasswordEvent.Changed(PasswordField.CONFIRM, it)) },
                label = stringResource(R.string.auth_confirm_password),
                visible = PasswordField.CONFIRM in state.visible,
                onToggleVisible = { onEvent(ChangePasswordEvent.ToggleVisibility(PasswordField.CONFIRM)) },
                contentType = ContentType.NewPassword,
                enabled = !state.loading,
                imeAction = ImeAction.Done,
                onImeAction = {
                    focus.clearFocus()
                    onEvent(ChangePasswordEvent.Submit)
                },
            )
            val label =
                when {
                    state.loading -> R.string.auth_saving
                    forced -> R.string.auth_set_password
                    else -> R.string.auth_update_password
                }
            TpButton(
                text = stringResource(label),
                onClick = { onEvent(ChangePasswordEvent.Submit) },
                enabled = state.canSubmit || state.loading,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
                tid = "auth.change_password_screen_tp_button",
            )
        }
    }
}

@Preview(name = "Forced change – light")
@Composable
private fun ForcedPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { ChangePasswordScreen(ChangePasswordUiState(username = "alice"), forced = true, onEvent = {}) }
}

@Preview(name = "Forced change – error, dark")
@Composable
private fun ForcedErrorPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        ChangePasswordScreen(
            ChangePasswordUiState(username = "alice", current = "temp", new = "abc", confirm = "abd", error = AuthMessage.Local(R.string.auth_error_mismatch)),
            forced = true,
            onEvent = {},
        )
    }
}

@Preview(name = "Voluntary change – light")
@Composable
private fun VoluntaryPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { ChangePasswordScreen(ChangePasswordUiState(username = "alice"), forced = false, onEvent = {}) }
}
