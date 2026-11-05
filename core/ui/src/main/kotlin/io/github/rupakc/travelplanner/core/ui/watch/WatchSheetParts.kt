package io.github.rupakc.travelplanner.core.ui.watch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertMode
import io.github.rupakc.travelplanner.core.domain.alerts.Tone
import io.github.rupakc.travelplanner.core.domain.alerts.currencyCode
import io.github.rupakc.travelplanner.core.domain.alerts.currencySymbol
import io.github.rupakc.travelplanner.core.ui.R

/** Background/foreground for a [Tone]; text always says the meaning too (never colour alone). */
@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val x = TravelTheme.extendedColors
    val c = TravelTheme.colors
    return when (tone) {
        Tone.GOOD -> x.successContainer to x.onSuccessContainer
        Tone.WARN -> x.warningContainer to x.onWarningContainer
        Tone.INFO -> x.infoContainer to c.onSurface
        Tone.NEUTRAL -> c.surfaceContainerHigh to c.onSurfaceVariant
    }
}

/** A tinted note box (web amber/emerald/slate panels). [live] announces it politely. */
@Composable
fun ToneBox(tone: Tone, modifier: Modifier = Modifier, live: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val (bg, fg) = toneColors(tone)
    Surface(
        color = bg,
        contentColor = fg,
        shape = TravelShape.ItemCard,
        modifier = modifier.fillMaxWidth().then(if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), content = content)
    }
}

/** The UI locale, observed (recomposes when the per-app language changes). */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun currentLocale(): java.util.Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]

/** "US dollars" for USD, else the code ("EUR") — web `currencyLabel`. */
@Composable
fun currencyLabel(currency: String?): String {
    val code = currencyCode(currency)
    return if (code == "USD") stringResource(R.string.watch_currency_usd) else code
}

/** One radio row of "Tell me" (48 dp target, whole row selectable). */
@Composable
internal fun ModeOption(
    selected: Boolean,
    title: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val border = if (selected) TravelTheme.colors.primary else TravelTheme.colors.outlineVariant
    Surface(
        shape = TravelShape.ItemCard,
        border = androidx.compose.foundation.BorderStroke(TravelTheme.spacing.xxs / 2, border),
        color = if (selected) TravelTheme.colors.primary.copy(alpha = SELECTED_TINT) else TravelTheme.colors.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TravelTheme.spacing.minTouchTarget)
                    .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(TravelTheme.spacing.sm))
                Text(title, style = TravelTheme.typography.titleSmall)
            }
            content()
        }
    }
}

/** A labelled checkbox row (whole row toggles; 48 dp). */
@Composable
internal fun CheckRow(
    checked: Boolean,
    title: String,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(TravelTheme.spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TravelTheme.typography.titleSmall,
                color = if (enabled) TravelTheme.colors.onSurface else TravelTheme.colors.onSurfaceVariant,
            )
            if (subtitle != null) Text(subtitle, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

/**
 * "Tell me" group shared by every watch dialog and edit panel: any drop vs below a target.
 * [targetError] (already localised) is shown under the field; [helper] otherwise.
 */
@Composable
fun AlertModeGroup(
    mode: AlertMode,
    onMode: (AlertMode) -> Unit,
    target: String,
    onTarget: (String) -> Unit,
    currency: String,
    anyDropHint: String,
    modifier: Modifier = Modifier,
    targetError: String? = null,
    helper: String? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.watch_tell_me), style = TravelTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
        ModeOption(mode == AlertMode.ANY_DROP, stringResource(R.string.watch_mode_any_drop), { onMode(AlertMode.ANY_DROP) }) {
            Text(anyDropHint, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        ModeOption(mode == AlertMode.BELOW_TARGET, stringResource(R.string.watch_mode_below_target), { onMode(AlertMode.BELOW_TARGET) }) {
            if (mode == AlertMode.BELOW_TARGET) {
                val label = stringResource(R.string.watch_target_label, currencyLabel(currency))
                OutlinedTextField(
                    value = target,
                    onValueChange = onTarget,
                    label = { Text(label) },
                    prefix = { Text(currencySymbol(currency)) },
                    singleLine = true,
                    isError = targetError != null,
                    supportingText = (targetError ?: helper)?.let { text -> { Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = TravelShape.Input,
                    modifier = Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.xs),
                )
            }
        }
    }
}

/** "Also email me" — disabled until there is an address (web `EmailOption`). */
@Composable
fun EmailOption(checked: Boolean, email: String?, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val has = !email.isNullOrBlank()
    CheckRow(
        checked = checked && has,
        title = stringResource(R.string.watch_email),
        onChange = onChange,
        enabled = has,
        subtitle = if (has) stringResource(R.string.watch_email_sent_to, email.orEmpty()) else stringResource(R.string.watch_email_missing),
        modifier = modifier,
    )
}

/** "Include nearby dates (±3 days)" with its explanation (fare watches). */
@Composable
fun FlexibleOption(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    CheckRow(
        checked = checked,
        title = stringResource(R.string.watch_flexible),
        subtitle = stringResource(R.string.watch_flexible_hint, io.github.rupakc.travelplanner.core.ui.format.localUsd(FLEXIBLE_SAVING_USD)),
        onChange = onChange,
        modifier = modifier,
    )
}

/** The big price line: "Today $454  total for 2 travellers" / "Shown now $410  AI estimate…". */
@Composable
internal fun PriceLead(lead: String, price: String, note: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(lead, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        Text(price, style = TravelTheme.typography.headlineMedium)
    }
    if (note != null) Text(note, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
}

private const val SELECTED_TINT = 0.06f

// Backend alert rules quoted in the sheet copy (services/price_rules.py, fare_watch.py), in USD.
internal const val FARE_ALERT_STEP_USD = 15.0
internal const val FLEXIBLE_SAVING_USD = 25.0
internal const val TRIP_ALERT_STEP_USD = 25.0
