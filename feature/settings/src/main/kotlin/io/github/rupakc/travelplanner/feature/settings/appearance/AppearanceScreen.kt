package io.github.rupakc.travelplanner.feature.settings.appearance

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.tpSelectable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold
import io.github.rupakc.travelplanner.feature.settings.common.SettingsSwitchRow
import io.github.rupakc.travelplanner.feature.settings.common.highlighted
import io.github.rupakc.travelplanner.feature.settings.root.labelRes

@Composable
internal fun AppearanceRoute(highlightKey: String?, onBack: () -> Unit, viewModel: AppearanceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppearanceScreen(state, viewModel::onEvent, onBack, highlightKey = highlightKey)
}

/** Theme and dynamic colour (stateless). [settings] null = still loading. */
@Composable
fun AppearanceScreen(
    settings: AppSettings?,
    onEvent: (AppearanceEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    highlightKey: String? = null,
    dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    SettingsScaffold(stringResource(R.string.settings_appearance), onBack = onBack, modifier = modifier) {
        if (settings == null) {
            SkeletonList(Modifier.padding(TravelTheme.spacing.gutter))
            return@SettingsScaffold
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SettingsHeading(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup().highlighted(highlightKey == SettingsEntry.THEME.key)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = TravelTheme.spacing.minTouchTarget)
                            .tpSelectable(
                                tid = "settings.appearance_theme_mode",
                                selected = settings.themeMode == mode,
                                role = Role.RadioButton,
                                onClick = { onEvent(AppearanceEvent.SetTheme(mode)) },
                            )
                            .padding(horizontal = TravelTheme.spacing.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.themeMode == mode, onClick = null)
                        Spacer(Modifier.width(TravelTheme.spacing.md))
                        Text(stringResource(mode.labelRes()), style = TravelTheme.typography.bodyLarge)
                    }
                }
            }
            SettingsHeading(stringResource(R.string.settings_colour))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(if (dynamicColorSupported) R.string.settings_dynamic_color_sub else R.string.settings_dynamic_color_unsupported),
                checked = settings.dynamicColor && dynamicColorSupported,
                enabled = dynamicColorSupported,
                highlighted = highlightKey == SettingsEntry.DYNAMIC_COLOR.key,
                onCheckedChange = { onEvent(AppearanceEvent.SetDynamicColor(it)) },
            )
            SettingsNote(stringResource(R.string.settings_dynamic_color_note, stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name)))
        }
    }
}

@Preview(name = "Appearance light", widthDp = 380, heightDp = 600)
@Composable
private fun AppearancePreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { AppearanceScreen(AppSettings(), onEvent = {}, onBack = {}, highlightKey = "theme") }
}

@Preview(name = "Appearance dark", widthDp = 380, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearancePreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { AppearanceScreen(AppSettings(themeMode = ThemeMode.DARK, dynamicColor = true), onEvent = {}, onBack = {}) }
}
