package io.github.rupakc.travelplanner.feature.settings.taste

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.preferences.TasteSignal
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold

/** Web shows the top 4 values per signal type. */
private const val TOP_VALUES = 4

@Composable
internal fun TasteProfileRoute(onBack: () -> Unit, viewModel: TasteProfileViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TasteProfileScreen(state, viewModel::onEvent, onBack)
}

/** Taste Graph view + Reset (stateless). */
@Composable
fun TasteProfileScreen(state: TasteUiState, onEvent: (TasteEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsScaffold(stringResource(R.string.settings_taste_title), onBack = onBack, modifier = modifier) {
        when {
            state.loading -> {
                SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), lines = 4)
            }

            state.error != null -> {
                ErrorState(state.error.message ?: stringResource(R.string.settings_taste_load_failed), onRetry = { onEvent(TasteEvent.Retry) })
            }

            !state.hasSignals -> {
                EmptyState(
                    title = stringResource(R.string.settings_taste_heading),
                    message = stringResource(R.string.settings_taste_empty),
                    icon = Icons.Filled.Favorite,
                )
            }

            else -> {
                Signals(state, onEvent)
            }
        }
    }
    if (state.confirmingReset) {
        AlertDialog(
            onDismissRequest = { onEvent(TasteEvent.CancelReset) },
            title = { Text(stringResource(R.string.settings_taste_reset)) },
            text = { Text(stringResource(R.string.settings_taste_reset_confirm)) },
            confirmButton = { TextButton(onClick = { onEvent(TasteEvent.ConfirmReset) }) { Text(stringResource(R.string.settings_taste_reset)) } },
            dismissButton = { TextButton(onClick = { onEvent(TasteEvent.CancelReset) }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

@Composable
private fun Signals(state: TasteUiState, onEvent: (TasteEvent) -> Unit) {
    val profile = state.profile ?: return
    val total = profile.totalSignals ?: 0
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
    ) {
        Text(stringResource(R.string.settings_taste_heading), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(
            pluralStringResource(R.plurals.settings_taste_built_from, total, total),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        profile.signals.forEach { (type, entries) ->
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text(signalLabel(type), style = TravelTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    entries.filter { !it.value.isNullOrBlank() }.take(TOP_VALUES).forEach { e ->
                        SuggestionChip(onClick = {}, enabled = false, label = {
                            Text(
                                stringResource(
                                    R.string.settings_taste_value,
                                    e.value.orEmpty(),
                                    e.count ?: 0,
                                ),
                            )
                        })
                    }
                }
            }
        }
        TpButton(
            stringResource(R.string.settings_taste_reset),
            onClick = { onEvent(TasteEvent.AskReset) },
            variant = TpButtonVariant.Destructive,
            loading = state.resetting,
            leadingIcon = Icons.Filled.Delete,
        )
        state.resetError?.let {
            Text(
                it.message ?: stringResource(R.string.settings_taste_reset_failed),
                color = TravelTheme.colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Web SIGNAL_LABELS; unknown types show their raw name (as the web does). */
@Composable
private fun signalLabel(type: String): String =
    when (type) {
        "activity_category" -> stringResource(R.string.settings_signal_activity_category)
        "event_category" -> stringResource(R.string.settings_signal_event_category)
        "hotel_tier" -> stringResource(R.string.settings_signal_hotel_tier)
        "flight_style" -> stringResource(R.string.settings_signal_flight_style)
        "interest" -> stringResource(R.string.settings_signal_interest)
        "destination" -> stringResource(R.string.settings_signal_destination)
        "pace" -> stringResource(R.string.settings_signal_pace)
        else -> type
    }

private val previewTaste =
    TasteUiState(
        loading = false,
        profile =
            TasteProfile(
                signals = mapOf("interest" to listOf(TasteSignal("food", 3), TasteSignal("art", 1)), "destination" to listOf(TasteSignal("Tokyo", 2))),
                totalSignals = 6,
            ),
    )

@Preview(name = "Taste light", widthDp = 380, heightDp = 700)
@Composable
private fun TastePreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { TasteProfileScreen(previewTaste, onEvent = {}, onBack = {}) }
}

@Preview(name = "Taste dark empty", widthDp = 380, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TastePreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { TasteProfileScreen(TasteUiState(loading = false, profile = TasteProfile()), onEvent = {}, onBack = {}) }
}
