package io.github.rupakc.travelplanner.feature.auth.server

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.server.ServerUrlResult
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R

/** Bottom sheet hosting [ServerContent]; [onDismiss] also undoes an unsaved test. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerSheet(state: ServerUiState, onEvent: (ServerEvent) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ServerContent(state, onEvent, Modifier.navigationBarsPadding())
    }
}

/** Server address form: field + validation, default, Test connection result (live), Save, Reset to default. */
@Composable
internal fun ServerContent(state: ServerUiState, onEvent: (ServerEvent) -> Unit, modifier: Modifier = Modifier) {
    val errorText = state.inputError?.messageRes()?.let { stringResource(it) }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Text(stringResource(R.string.auth_server_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(R.string.auth_server_message, stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name)),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.input,
            onValueChange = { onEvent(ServerEvent.InputChanged(it)) },
            label = { Text(stringResource(R.string.auth_server_url)) },
            singleLine = true,
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            shape = TravelShape.Input,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onEvent(ServerEvent.Test) }),
            modifier = Modifier.fillMaxWidth().semantics { if (errorText != null) error(errorText) },
        )
        Text(
            if (state.defaultUrl.isBlank()) stringResource(R.string.auth_server_no_default) else stringResource(R.string.auth_server_default, state.defaultUrl),
            style = TravelTheme.typography.bodySmall,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        state.test?.let { TestResult(it) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TpButton(
                stringResource(if (state.testing) R.string.auth_server_testing else R.string.auth_server_test),
                { onEvent(ServerEvent.Test) },
                variant = TpButtonVariant.Secondary,
                loading = state.testing,
            )
            TpButton(stringResource(R.string.auth_server_save), { onEvent(ServerEvent.Save) }, enabled = !state.testing)
            if (state.isOverride) {
                TpButton(stringResource(R.string.auth_server_reset), { onEvent(ServerEvent.Reset) }, variant = TpButtonVariant.Text, enabled = !state.testing)
            }
        }
    }
}

@Composable
private fun TestResult(test: ServerTest) {
    val ok = test is ServerTest.Reachable
    TpCard(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        selected = true,
        accent = if (ok) TravelTheme.extendedColors.success else TravelTheme.colors.error,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            when (test) {
                is ServerTest.Reachable -> {
                    Text(stringResource(R.string.auth_server_reachable), style = TravelTheme.typography.titleSmall, color = TravelTheme.extendedColors.success)
                    Text(
                        stringResource(if (test.serpapiConfigured) R.string.auth_server_serpapi_on else R.string.auth_server_serpapi_off),
                        style = TravelTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(if (test.pushConfigured) R.string.auth_server_push_on else R.string.auth_server_push_off),
                        style = TravelTheme.typography.bodyMedium,
                    )
                }

                is ServerTest.Unreachable -> {
                    val text =
                        when (val m = test.message) {
                            is AuthMessage.Server -> m.text
                            is AuthMessage.Local -> stringResource(m.res)
                        }
                    Text(text, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.error)
                }
            }
        }
    }
}

@StringRes
private fun ServerUrlResult.messageRes(): Int? =
    when (this) {
        is ServerUrlResult.Valid -> null
        ServerUrlResult.Blank -> R.string.auth_server_error_blank
        ServerUrlResult.Malformed -> R.string.auth_server_error_malformed
        ServerUrlResult.UnsupportedScheme -> R.string.auth_server_error_scheme
        ServerUrlResult.MissingHost -> R.string.auth_server_error_host
        ServerUrlResult.HttpsRequired -> R.string.auth_server_error_https
    }

@Preview(name = "Server – reachable, light")
@Composable
private fun ServerReachablePreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        ServerContent(
            ServerUiState(input = "http://192.168.1.20:8001", defaultUrl = "http://10.0.2.2:8001", isOverride = true, test = ServerTest.Reachable(false, true)),
            {},
        )
    }
}

@Preview(name = "Server – invalid, dark")
@Composable
private fun ServerInvalidPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        ServerContent(ServerUiState(input = "192.168.1.20", defaultUrl = "", inputError = ServerUrlResult.Malformed), {})
    }
}
