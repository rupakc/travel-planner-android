package io.github.rupakc.travelplanner.feature.settings.features

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.profile.descriptionRes
import io.github.rupakc.travelplanner.core.ui.profile.featureIcon
import io.github.rupakc.travelplanner.core.ui.profile.titleRes
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.KeyedSettingsList
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold
import io.github.rupakc.travelplanner.feature.settings.common.SettingsSwitchRow

@Composable
internal fun FeaturesRoute(onBack: () -> Unit, viewModel: FeaturesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FeaturesScreen(state, viewModel::onEvent, onBack)
}

/** Every catalogue feature with a switch, grouped; "Reset to my type's defaults". */
@Composable
fun FeaturesScreen(state: FeaturesUiState, onEvent: (FeaturesEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsScaffold(stringResource(R.string.settings_features), onBack = onBack, modifier = modifier) { padding ->
        if (state.loading) {
            SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), lines = 6)
            return@SettingsScaffold
        }
        KeyedSettingsList(keys = emptyList(), highlightKey = null, contentPadding = padding) {
            item(key = "intro", contentType = "note") {
                SettingsNote(pluralStringResource(R.plurals.settings_features_on, state.onCount, state.onCount, state.rows.size))
                SettingsNote(stringResource(R.string.settings_features_intro))
            }
            FeatureGroup.entries.forEach { group ->
                item(key = "h-${group.name}", contentType = "heading") { SettingsHeading(stringResource(group.titleRes())) }
                state.rows.filter { FeatureGroup.of(it.id) == group }.forEach { row ->
                    item(key = row.id.id, contentType = "feature") {
                        val description = stringResource(row.id.descriptionRes())
                        SettingsSwitchRow(
                            title = stringResource(row.id.titleRes()),
                            subtitle = if (row.defaultOn) stringResource(R.string.settings_features_default_on, description) else description,
                            icon = featureIcon(row.iconName.orEmpty()),
                            checked = row.on,
                            onCheckedChange = { onEvent(FeaturesEvent.Toggle(row.id, it)) },
                        )
                    }
                }
            }
            item(key = "reset", contentType = "reset") {
                TpButton(
                    stringResource(R.string.settings_features_reset),
                    onClick = { onEvent(FeaturesEvent.ResetToDefaults) },
                    variant = TpButtonVariant.Secondary,
                    enabled = state.hasOverrides,
                    modifier = Modifier.padding(TravelTheme.spacing.gutter),
                )
            }
        }
    }
}

private fun FeatureGroup.titleRes(): Int =
    when (this) {
        FeatureGroup.PLAN -> R.string.settings_features_group_plan
        FeatureGroup.RESULTS -> R.string.settings_features_group_results
        FeatureGroup.TRIP_INFO -> R.string.settings_features_group_info
    }

private val previewFeatures =
    FeaturesUiState(
        loading = false,
        rows = FeatureGroup.entries.flatMap { it.members }.map { FeatureRow(it, on = it.ordinal % 2 == 0, defaultOn = it == FeatureId.CHAT) },
        hasOverrides = true,
    )

@Preview(name = "Features light", widthDp = 380, heightDp = 1600)
@Composable
private fun FeaturesPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { FeaturesScreen(previewFeatures, onEvent = {}, onBack = {}) }
}

@Preview(name = "Features dark", widthDp = 380, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeaturesPreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { FeaturesScreen(previewFeatures, onEvent = {}, onBack = {}) }
}
