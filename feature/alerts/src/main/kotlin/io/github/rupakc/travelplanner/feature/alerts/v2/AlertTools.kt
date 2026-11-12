package io.github.rupakc.travelplanner.feature.alerts.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.alerts.R

/**
 * Alerts 2.0 entry points above the watch list: "Suggested watches" (needs live prices) and "Alert settings".
 * Renders nothing against a backend without the v2 API.
 */
@Composable
fun AlertTools(onOpenPrefs: () -> Unit, modifier: Modifier = Modifier, viewModel: AlertSuggestionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertToolsContent(state, viewModel::open, onOpenPrefs, modifier)
    if (state.sheetOpen) {
        AlertSuggestionsSheet(state.load, viewModel::accept, viewModel::dismiss, viewModel::load, viewModel::close)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AlertToolsContent(state: AlertToolsUiState, onOpenSuggestions: () -> Unit, onOpenPrefs: () -> Unit, modifier: Modifier = Modifier) {
    if (!state.prefsAvailable && !state.suggestionsAvailable) return
    FlowRow(
        modifier.padding(horizontal = TravelTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        if (state.suggestionsAvailable) {
            TpButton(
                stringResource(R.string.alerts_tools_suggestions),
                onOpenSuggestions,
                variant = TpButtonVariant.Tonal,
                leadingIcon = Icons.Filled.Notifications,
                tid = "alerts.tools.suggestions",
            )
        }
        if (state.prefsAvailable) {
            TpButton(
                stringResource(R.string.alerts_tools_prefs),
                onOpenPrefs,
                variant = TpButtonVariant.Text,
                leadingIcon = Icons.Filled.Settings,
                tid = "alerts.tools.prefs",
            )
        }
    }
}
