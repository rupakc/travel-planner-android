package io.github.rupakc.travelplanner.feature.settings.server

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.LoadingIndicator
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.health.Health
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold

@Composable
internal fun ServerRoute(onBack: () -> Unit, viewModel: ServerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ServerScreen(state, viewModel::onEvent, onBack)
}

/** Backend URL + "Test connection" (stateless). */
@Composable
fun ServerScreen(state: ServerUiState, onEvent: (ServerEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsScaffold(stringResource(R.string.settings_server), onBack = onBack, modifier = modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            Text(
                stringResource(R.string.settings_server_current, state.effectiveUrl.ifEmpty { stringResource(R.string.settings_server_none) }),
                style = TravelTheme.typography.bodyMedium,
            )
            val problem =
                when (state.problem) {
                    UrlProblem.INVALID -> stringResource(R.string.settings_server_invalid)
                    UrlProblem.UNSUPPORTED_SCHEME -> stringResource(R.string.settings_server_scheme)
                    UrlProblem.MISSING_HOST -> stringResource(R.string.settings_server_host)
                    UrlProblem.HTTPS_REQUIRED -> stringResource(R.string.settings_server_https)
                    null -> null
                }
            TpOutlinedTextField(
                value = state.input,
                onValueChange = { onEvent(ServerEvent.InputChanged(it)) },
                label = { Text(stringResource(R.string.settings_server_label)) },
                placeholder = { Text(state.defaultUrl.ifEmpty { stringResource(R.string.settings_server_placeholder) }) },
                isError = problem != null,
                supportingText = {
                    Text(
                        problem ?: if (state.saved) stringResource(R.string.settings_server_saved) else stringResource(R.string.settings_server_help),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onEvent(ServerEvent.Save) }),
                modifier = Modifier.fillMaxWidth(),
                tid = "settings.settings_server_label",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                TpButton(stringResource(R.string.settings_server_save), onClick = { onEvent(ServerEvent.Save) }, tid = "settings.settings_server_save")
                TpButton(
                    stringResource(R.string.settings_server_test),
                    onClick = { onEvent(ServerEvent.Test) },
                    variant = TpButtonVariant.Secondary,
                    loading = state.test == ConnectionTest.Running,
                    tid = "settings.settings_server_test",
                )
                TpButton(
                    stringResource(R.string.settings_server_default),
                    onClick = { onEvent(ServerEvent.UseDefault) },
                    variant = TpButtonVariant.Text,
                    enabled = !state.usingDefault || state.input.isNotEmpty(),
                    tid = "settings.settings_server_default",
                )
            }
            SettingsNote(stringResource(R.string.settings_server_test_note))
            TestResult(state.test)
        }
    }
    if (state.confirmingSwitch) {
        AlertDialog(
            onDismissRequest = { onEvent(ServerEvent.CancelSwitch) },
            title = { Text(stringResource(R.string.settings_server_switch_title)) },
            text = { Text(stringResource(R.string.settings_server_switch_message, state.pendingUrl ?: state.defaultUrl)) },
            confirmButton = {
                TpTextButton(onClick = {
                    onEvent(ServerEvent.ConfirmSwitch)
                }, tid = "settings.settings_server_switch_confirm") { Text(stringResource(R.string.settings_server_switch_confirm)) }
            },
            dismissButton = {
                TpTextButton(onClick = {
                    onEvent(ServerEvent.CancelSwitch)
                }, tid = "settings.settings_cancel") { Text(stringResource(R.string.settings_cancel)) }
            },
        )
    }
}

@Composable
private fun TestResult(test: ConnectionTest?) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when (test) {
            null -> {
                Unit
            }

            ConnectionTest.Running -> {
                LoadingIndicator(label = stringResource(R.string.settings_server_testing))
            }

            is ConnectionTest.Failed -> {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_server_test_failed)) },
                    supportingContent = { Text(test.error.message ?: stringResource(R.string.settings_server_test_failed_help)) },
                    leadingContent = { Icon(Icons.Filled.Warning, contentDescription = null, tint = TravelTheme.colors.error) },
                )
            }

            is ConnectionTest.Ok -> {
                HealthFlags(test.health)
            }
        }
    }
}

@Composable
private fun HealthFlags(health: Health) {
    SettingsHeading(stringResource(R.string.settings_server_connected))
    health.service?.let { SettingsNote(it) }
    listOf(
        R.string.settings_server_flag_serpapi to health.serpapiConfigured,
        R.string.settings_server_flag_fare_watch to health.fareWatchAvailable,
        R.string.settings_server_flag_email to health.emailAlertsAvailable,
        R.string.settings_server_flag_push to health.pushConfigured,
    ).forEach { (label, on) ->
        ListItem(
            headlineContent = { Text(stringResource(label)) },
            supportingContent = { Text(stringResource(if (on) R.string.settings_server_flag_yes else R.string.settings_server_flag_no)) },
            leadingContent = {
                Icon(
                    if (on) Icons.Filled.Check else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (on) TravelTheme.extendedColors.success else TravelTheme.colors.onSurfaceVariant,
                )
            },
        )
    }
}

@Preview(name = "Server light", widthDp = 380, heightDp = 900)
@Composable
private fun ServerPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        ServerScreen(
            ServerUiState(
                input = "http://192.168.1.20:8001",
                effectiveUrl = "http://192.168.1.20:8001",
                usingDefault = false,
                test = ConnectionTest.Ok(Health(status = "ok", service = "travel-planner-backend", serpapiConfigured = true)),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}

@Preview(name = "Server dark error", widthDp = 380, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ServerPreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { ServerScreen(ServerUiState(input = "ftp://x", problem = UrlProblem.INVALID), onEvent = {}, onBack = {}) }
}
