package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape

/**
 * Pill chip with selected state (web aria-pressed chips: interests, filters, mode toggle). A click reports
 * TOGGLE with `on = !selected` to [LocalUiEventSink] under [tid] (`auto:chip` when null).
 */
@Composable
fun TpChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    tid: String? = null,
) {
    val sink = LocalUiEventSink.current
    FilterChip(
        selected = selected,
        onClick = {
            sink.report(UiEventKind.TOGGLE, tid ?: "auto:chip", !selected)
            onClick()
        },
        // Decorative emoji ("🍜 Food") are shown but not read aloud.
        label = { Text(label, Modifier.readsWithoutEmoji(label)) },
        modifier = modifier,
        enabled = enabled,
        shape = TravelShape.Pill,
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
        colors = FilterChipDefaults.filterChipColors(),
        border = FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected, borderWidth = 1.dp),
    )
}
