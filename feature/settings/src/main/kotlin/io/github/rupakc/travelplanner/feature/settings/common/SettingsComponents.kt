package io.github.rupakc.travelplanner.feature.settings.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.settings.R

/** Layout constants shared by settings screens (defined once). */
internal object SettingsDimens {
    val ContentMaxWidth = 720.dp
}

/** Alpha of the highlight behind a row opened from global search. */
private const val HIGHLIGHT_ALPHA = 0.55f

/** Settings sub-screen frame: top bar with Back, content centred and width-capped on large screens. */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = title, onBack = onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = SettingsDimens.ContentMaxWidth).fillMaxSize()) {
                content(PaddingValues(bottom = TravelTheme.spacing.xxl))
            }
        }
    }
}

/**
 * A LazyColumn whose items are identified by [keys] (in order); when [highlightKey] is set it scrolls
 * to that item once (global search deep link).
 */
@Composable
internal fun KeyedSettingsList(
    keys: List<String>,
    highlightKey: String?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(),
    content: LazyListScope.() -> Unit,
) {
    LaunchedEffect(highlightKey) {
        val index = highlightKey?.let(keys::indexOf) ?: -1
        if (index >= 0) state.scrollToItem(index)
    }
    LazyColumn(modifier = modifier.fillMaxSize(), state = state, contentPadding = contentPadding, content = content)
}

@Composable
internal fun Modifier.highlighted(on: Boolean): Modifier =
    if (on) background(TravelTheme.colors.primaryContainer.copy(alpha = HIGHLIGHT_ALPHA)) else this

/** Section title (semantic heading). */
@Composable
internal fun SettingsHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TravelTheme.typography.titleSmall,
        color = TravelTheme.colors.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = TravelTheme.spacing.gutter, end = TravelTheme.spacing.gutter, top = TravelTheme.spacing.xl, bottom = TravelTheme.spacing.xs)
                .semantics { heading() },
    )
}

/** A navigational or action row. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    highlighted: Boolean = false,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = if (destructive) TravelTheme.colors.error else TravelTheme.colors.onSurface
    ListItem(
        headlineContent = { Text(title, color = color) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = if (destructive) color else TravelTheme.colors.onSurfaceVariant) } },
        colors = ListItemDefaults.colors(containerColor = TravelTheme.colors.surface.copy(alpha = 0f)),
        modifier =
            modifier
                .highlighted(highlighted)
                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
    )
}

/** A row with a switch; the whole row toggles and TalkBack reads On/Off. */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
) {
    val state = stringResource(if (checked) R.string.settings_state_on else R.string.settings_state_off)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = TravelTheme.colors.surface.copy(alpha = 0f)),
        modifier =
            modifier
                .highlighted(highlighted)
                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { stateDescription = state },
    )
}

/** Small explanatory text under a section. */
@Composable
internal fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.xs)) {
        Text(text, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}
