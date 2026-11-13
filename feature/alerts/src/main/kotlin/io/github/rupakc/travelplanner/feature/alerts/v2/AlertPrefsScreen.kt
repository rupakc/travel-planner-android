package io.github.rupakc.travelplanner.feature.alerts.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpDropdownMenuItem
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpSwitch
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.component.tpSelectable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertPrefsRules
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.alerts.R
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Alert settings route (Alerts 2.0 preferences). */
@Composable
fun AlertPrefsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: AlertPrefsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertPrefsScreen(state, AlertPrefsActions(viewModel), onBack, modifier)
}

/** The form's actions, so the stateless screen is testable without a ViewModel. */
class AlertPrefsActions(
    val onRetry: () -> Unit = {},
    val onDigestWeekly: (Boolean) -> Unit = {},
    val onDigestPush: (Boolean) -> Unit = {},
    val onDigestDay: (Int) -> Unit = {},
    val onInstant: (String) -> Unit = {},
    val onQuietEnabled: (Boolean) -> Unit = {},
    val onQuietTimes: (start: String, end: String) -> Unit = { _, _ -> },
    val onCap: (kind: String, value: Int) -> Unit = { _, _ -> },
    val onGroup: (Int) -> Unit = {},
) {
    constructor(vm: AlertPrefsViewModel) : this(
        vm::load,
        vm::setDigestWeekly,
        vm::setDigestPush,
        vm::setDigestDay,
        vm::setInstant,
        vm::setQuietEnabled,
        vm::setQuietTimes,
        vm::setCap,
        vm::setGroupWindow,
    )
}

@Composable
fun AlertPrefsScreen(state: AlertPrefsUiState, actions: AlertPrefsActions, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier, topBar = { TpTopBar(stringResource(R.string.alerts_prefs_title), onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            when (state) {
                AlertPrefsUiState.Loading -> {
                    SkeletonList(message = stringResource(R.string.alerts_loading_short))
                }

                is AlertPrefsUiState.Failed -> {
                    ErrorState(
                        state.message,
                        title = stringResource(R.string.alerts_prefs_load_failed),
                        onRetry = actions.onRetry,
                        tid = "alerts.alert_prefs_retry",
                    )
                }

                is AlertPrefsUiState.Content -> {
                    PrefsForm(state, actions)
                }
            }
        }
    }
}

@Composable
private fun PrefsForm(state: AlertPrefsUiState.Content, a: AlertPrefsActions) {
    val p = state.prefs
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        val errorText =
            when (val e = state.error) {
                null -> null
                PrefsError.Invalid -> stringResource(R.string.alerts_prefs_invalid)
                is PrefsError.Failed -> e.detail ?: stringResource(R.string.alerts_prefs_save_failed)
            }
        if (errorText != null) {
            Text(errorText, color = TravelTheme.colors.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        }
        DigestSection(p, a, !state.saving)
        HorizontalDivider()
        InstantSection(p, a, !state.saving)
        HorizontalDivider()
        QuietSection(p, a, !state.saving)
        HorizontalDivider()
        CapsSection(p, a, !state.saving)
        HorizontalDivider()
        GroupSection(p, a, !state.saving)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().widthIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        TpSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled, tid = "alerts.alert_prefs_screen_switch")
    }
}

@Composable
private fun DigestSection(p: AlertPrefs, a: AlertPrefsActions, idle: Boolean) {
    SectionTitle(stringResource(R.string.alerts_prefs_digest_section))
    SwitchRow(stringResource(R.string.alerts_prefs_digest_weekly), p.digestWeekly, enabled = idle, onChange = a.onDigestWeekly)
    SwitchRow(stringResource(R.string.alerts_prefs_digest_push), p.digestPush, enabled = idle && p.digestWeekly, onChange = a.onDigestPush)
    val locale = currentLocale()
    val day = (p.digestDay ?: AlertPrefsRules.DAY_MIN).coerceIn(AlertPrefsRules.DAY_MIN, AlertPrefsRules.DAY_MAX)
    Picker(
        label = stringResource(R.string.alerts_prefs_digest_day),
        value = DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale),
        options = (AlertPrefsRules.DAY_MIN..AlertPrefsRules.DAY_MAX).map { it to DayOfWeek.of(it).getDisplayName(TextStyle.FULL, locale) },
        enabled = idle && p.digestWeekly,
        onPick = a.onDigestDay,
    )
}

@Composable
private fun InstantSection(p: AlertPrefs, a: AlertPrefsActions, idle: Boolean) {
    SectionTitle(stringResource(R.string.alerts_prefs_instant_section))
    val current = if (p.instant == AlertPrefsRules.INSTANT_TARGETS_ONLY) AlertPrefsRules.INSTANT_TARGETS_ONLY else AlertPrefsRules.INSTANT_ALL
    Column(Modifier.selectableGroup()) {
        listOf(
            AlertPrefsRules.INSTANT_ALL to R.string.alerts_prefs_instant_all,
            AlertPrefsRules.INSTANT_TARGETS_ONLY to R.string.alerts_prefs_instant_targets,
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().widthIn(
                    min = 48.dp,
                ).tpSelectable(tid = "alerts.alert_prefs_screen_selectable", selected = current == mode, enabled = idle, role = Role.RadioButton, onClick = {
                    a.onInstant(mode)
                }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == mode, onClick = null)
                Text(stringResource(label), Modifier.padding(start = TravelTheme.spacing.sm))
            }
        }
    }
}

@Composable
private fun QuietSection(p: AlertPrefs, a: AlertPrefsActions, idle: Boolean) {
    SectionTitle(stringResource(R.string.alerts_prefs_quiet_section))
    val q = p.quietHours
    val enabled = q?.enabled == true
    SwitchRow(stringResource(R.string.alerts_prefs_quiet_switch), enabled, enabled = idle, onChange = a.onQuietEnabled)
    val start = q?.start.takeIf(AlertPrefsRules::isValidTime) ?: AlertPrefsRules.DEFAULT_START
    val end = q?.end.takeIf(AlertPrefsRules::isValidTime) ?: AlertPrefsRules.DEFAULT_END
    val options = remember { timeOptions() }
    val locale = currentLocale()

    fun timeLabel(hhmm: String) = timeLabel(hhmm, locale)
    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        Picker(
            stringResource(R.string.alerts_prefs_quiet_start),
            timeLabel(start),
            options.map {
                it to timeLabel(it)
            },
            enabled && idle,
            Modifier.weight(1f),
        ) { a.onQuietTimes(it, end) }
        Picker(
            stringResource(R.string.alerts_prefs_quiet_end),
            timeLabel(end),
            options.map {
                it to timeLabel(it)
            },
            enabled && idle,
            Modifier.weight(1f),
        ) { a.onQuietTimes(start, it) }
    }
    Text(
        if (p.timezone.isNullOrBlank()) {
            stringResource(
                R.string.alerts_prefs_timezone_unset,
            )
        } else {
            stringResource(R.string.alerts_prefs_timezone, p.timezone.orEmpty())
        },
        color = TravelTheme.colors.onSurfaceVariant,
    )
    val note =
        when {
            AlertPrefsRules.quietHoursNeedTimezone(p) -> R.string.alerts_prefs_quiet_no_tz
            p.quietHoursActive -> R.string.alerts_prefs_quiet_active
            else -> null
        }
    if (note !=
        null
    ) {
        Text(stringResource(note), color = TravelTheme.colors.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun CapsSection(p: AlertPrefs, a: AlertPrefsActions, idle: Boolean) {
    SectionTitle(stringResource(R.string.alerts_prefs_caps_section))
    AlertPrefsRules.CAP_KINDS.forEach { kind ->
        val label = stringResource(kindLabel(kind))
        Stepper(
            label = label,
            value = AlertPrefsRules.cap(p, kind),
            valueText = AlertPrefsRules.cap(p, kind).toString(),
            min = AlertPrefsRules.CAP_MIN,
            max = AlertPrefsRules.CAP_MAX,
            lessA11y = stringResource(R.string.alerts_prefs_less_a11y, label),
            moreA11y = stringResource(R.string.alerts_prefs_more_a11y, label),
            onChange = { a.onCap(kind, it) },
            step = 1,
            enabled = idle,
        )
    }
}

@Composable
private fun GroupSection(p: AlertPrefs, a: AlertPrefsActions, idle: Boolean) {
    SectionTitle(stringResource(R.string.alerts_prefs_group_section))
    Text(stringResource(R.string.alerts_prefs_group_desc), color = TravelTheme.colors.onSurfaceVariant)
    val minutes = p.groupWindowMin ?: 0
    val label = stringResource(R.string.alerts_prefs_group_section)
    Stepper(
        label = label,
        value = minutes,
        valueText = if (minutes == 0) stringResource(R.string.alerts_prefs_group_off) else stringResource(R.string.alerts_prefs_minutes, minutes),
        min = AlertPrefsRules.GROUP_MIN,
        max = AlertPrefsRules.GROUP_MAX,
        lessA11y = stringResource(R.string.alerts_prefs_less_a11y, label),
        moreA11y = stringResource(R.string.alerts_prefs_more_a11y, label),
        onChange = a.onGroup,
        step = AlertPrefsRules.GROUP_STEP,
        enabled = idle,
    )
}

private fun kindLabel(kind: String): Int =
    when (kind) {
        "flight" -> R.string.alerts_prefs_kind_flight
        "hotel" -> R.string.alerts_prefs_kind_hotel
        "activity" -> R.string.alerts_prefs_kind_activity
        else -> R.string.alerts_prefs_kind_trip
    }

private const val HALF_HOURS_PER_DAY = 48
private const val HALF_HOUR = 30

/** Quiet-hours choices: every half hour of the day as `HH:MM`. */
private fun timeOptions(): List<String> = (0 until HALF_HOURS_PER_DAY).map { "%02d:%02d".format(it / 2, if (it % 2 == 0) 0 else HALF_HOUR) }

private fun timeLabel(hhmm: String, locale: Locale): String =
    runCatching { LocalTime.parse(hhmm).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }.getOrDefault(hhmm)

@Composable
private fun Stepper(
    label: String,
    value: Int,
    valueText: String,
    min: Int,
    max: Int,
    step: Int,
    lessA11y: String,
    moreA11y: String,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        TpIconButton(onClick = {
            onChange((value - step).coerceAtLeast(min))
        }, enabled = enabled && value > min, tid = "alerts.alert_prefs_screen_icon_button") {
            // No minus glyph in material-icons-core: a text minus, named for TalkBack on the button.
            Text("\u2212", Modifier.clearAndSetSemantics { contentDescription = lessA11y }, style = TravelTheme.typography.titleLarge)
        }
        Text(valueText, Modifier.widthIn(min = 56.dp).semantics { liveRegion = LiveRegionMode.Polite }, style = TravelTheme.typography.titleMedium)
        TpIconButton(onClick = {
            onChange((value + step).coerceAtMost(max))
        }, enabled = enabled && value < max, tid = "alerts.alert_prefs_screen_icon_button_2") {
            Icon(Icons.Filled.Add, contentDescription = moreA11y)
        }
    }
}

/** A labelled dropdown. [value] is the shown text of the current choice. */
@Composable
private fun <T> Picker(label: String, value: String, options: List<Pair<T, String>>, enabled: Boolean, modifier: Modifier = Modifier, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, color = TravelTheme.colors.onSurfaceVariant, style = TravelTheme.typography.labelMedium)
        TpButton(
            text = value,
            onClick = { open = true },
            enabled = enabled,
            variant = TpButtonVariant.Tonal,
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = "$label: $value"
                    stateDescription = value
                },
            tid = "alerts.alert_prefs_screen_tp_button",
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                TpDropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onPick(key)
                }, tid = "alerts.alert_prefs_screen_dropdown_menu_item")
            }
        }
    }
}
