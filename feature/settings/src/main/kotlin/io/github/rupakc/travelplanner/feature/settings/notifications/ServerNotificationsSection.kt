package io.github.rupakc.travelplanner.feature.settings.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationSettingsRules
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.notifications.CategorySetting
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.PushStatus
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsRow
import io.github.rupakc.travelplanner.feature.settings.common.SettingsSwitchRow

/**
 * The server-backed part of Settings > Notifications: category switches, quiet hours, daily cap, time zone and push
 * status. Emits nothing when [ServerNotificationsUiState.available] is false. Stateless.
 */
@Composable
internal fun ServerNotificationsSection(
    state: ServerNotificationsUiState,
    onEvent: (ServerNotificationsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenAlertPrefs: (() -> Unit)? = null,
) {
    if (!state.available) return
    val settings = state.settings
    Column(modifier) {
        when {
            state.loading && settings == null -> {
                SkeletonList(Modifier.padding(TravelTheme.spacing.gutter))
            }

            settings == null -> {
                ErrorState(
                    state.loadError?.message ?: stringResource(R.string.settings_notifsrv_error_load),
                    onRetry = { onEvent(ServerNotificationsEvent.Retry) },
                )
            }

            else -> {
                state.saveError?.let { failure ->
                    Text(
                        failure.message ?: stringResource(R.string.settings_notifsrv_error_save),
                        color = TravelTheme.colors.error,
                        style = TravelTheme.typography.bodyMedium,
                        modifier = Modifier.padding(TravelTheme.spacing.gutter).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Categories(state, onEvent)
                QuietAndLimits(state, settings, onEvent)
                PushStatusRows(settings)
                if (state.alertPrefsAvailable && onOpenAlertPrefs != null) {
                    SettingsRow(
                        stringResource(R.string.settings_notifsrv_alert_prefs),
                        subtitle = stringResource(R.string.settings_notifsrv_alert_prefs_sub),
                        onClick = onOpenAlertPrefs,
                    )
                }
            }
        }
    }
}

@Composable
private fun Categories(state: ServerNotificationsUiState, onEvent: (ServerNotificationsEvent) -> Unit) {
    SettingsHeading(stringResource(R.string.settings_notif_in_app))
    SettingsNote(stringResource(R.string.settings_notifsrv_intro))
    state.categories.forEach { category ->
        SettingsSwitchRow(
            title = stringResource(category.titleRes()),
            subtitle = stringResource(category.hintRes()),
            checked = state.isOn(category),
            enabled = !state.saving,
            onCheckedChange = { onEvent(ServerNotificationsEvent.SetCategory(category, it)) },
        )
    }
}

@Composable
private fun QuietAndLimits(state: ServerNotificationsUiState, settings: NotificationSettings, onEvent: (ServerNotificationsEvent) -> Unit) {
    val quiet = state.quietHours
    var picking by remember { mutableStateOf<QuietEnd?>(null) }
    SettingsHeading(stringResource(R.string.settings_notifsrv_limits_heading))
    SettingsSwitchRow(
        title = stringResource(R.string.settings_notifsrv_quiet),
        subtitle = stringResource(R.string.settings_notifsrv_quiet_hint),
        checked = quiet.enabled,
        enabled = !state.saving,
        onCheckedChange = { onEvent(ServerNotificationsEvent.SetQuietEnabled(it)) },
    )
    if (quiet.enabled) {
        SettingsRow(
            stringResource(R.string.settings_notifsrv_quiet_from),
            subtitle = quiet.start,
            onClick = { if (!state.saving) picking = QuietEnd.START },
        )
        SettingsRow(
            stringResource(R.string.settings_notifsrv_quiet_until),
            subtitle = quiet.end,
            onClick = { if (!state.saving) picking = QuietEnd.END },
        )
        if (settings.timezone != null) {
            SettingsNote(stringResource(R.string.settings_notifsrv_quiet_timezone, settings.timezone.orEmpty()))
            if (settings.quietHoursActive) {
                SettingsNote(
                    stringResource(R.string.settings_notifsrv_quiet_active),
                    Modifier.semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    },
                )
            }
        } else {
            SettingsNote(stringResource(R.string.settings_notifsrv_quiet_no_timezone))
        }
    }
    picking?.let { end ->
        val initial = NotificationSettingsRules.parseTime(if (end == QuietEnd.START) quiet.start else quiet.end) ?: (0 to 0)
        QuietTimeDialog(
            title = stringResource(if (end == QuietEnd.START) R.string.settings_notifsrv_quiet_from else R.string.settings_notifsrv_quiet_until),
            initialHour = initial.first,
            initialMinute = initial.second,
            onDismiss = { picking = null },
            onConfirm = { hour, minute ->
                picking = null
                val time = NotificationSettingsRules.formatTime(hour, minute)
                onEvent(
                    if (end ==
                        QuietEnd.START
                    ) {
                        ServerNotificationsEvent.SetQuietTimes(time, quiet.end)
                    } else {
                        ServerNotificationsEvent.SetQuietTimes(quiet.start, time)
                    },
                )
            },
        )
    }
    CapRow(state, onEvent)
}

private enum class QuietEnd { START, END }

@Composable
private fun CapRow(state: ServerNotificationsUiState, onEvent: (ServerNotificationsEvent) -> Unit) {
    val cap = state.dailyCap
    val value = pluralStringResource(R.plurals.settings_notifsrv_cap_value, cap, cap)
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = TravelTheme.spacing.minTouchTarget,
        ).padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_notifsrv_cap), style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onSurface)
            Text(stringResource(R.string.settings_notifsrv_cap_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        TpIconButton(
            onClick = { onEvent(ServerNotificationsEvent.SetCap(cap - 1)) },
            enabled = !state.saving && cap > NotificationSettingsRules.MIN_CAP,
            tid = "settings.server_notifications_section_icon_button",
        ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.settings_notifsrv_cap_less)) }
        Text(
            value,
            style = TravelTheme.typography.titleSmall,
            modifier =
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = value
                },
        )
        TpIconButton(
            onClick = { onEvent(ServerNotificationsEvent.SetCap(cap + 1)) },
            enabled = !state.saving && cap < NotificationSettingsRules.MAX_CAP,
            tid = "settings.server_notifications_section_icon_button_2",
        ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.settings_notifsrv_cap_more)) }
    }
}

@Composable
private fun PushStatusRows(settings: NotificationSettings) {
    SettingsHeading(stringResource(R.string.settings_notifsrv_push_heading))
    val pushOn = settings.push.fcm
    SettingsRow(
        stringResource(if (pushOn) R.string.settings_notifsrv_push_on else R.string.settings_notifsrv_push_off),
        subtitle = stringResource(if (pushOn) R.string.settings_notifsrv_push_on_sub else R.string.settings_notifsrv_push_off_sub),
    )
    val devices = settings.devices ?: 0
    SettingsRow(
        if (devices >
            0
        ) {
            pluralStringResource(R.plurals.settings_notifsrv_devices, devices, devices)
        } else {
            stringResource(R.string.settings_notifsrv_devices_none)
        },
    )
    SettingsRow(
        stringResource(R.string.settings_notifsrv_timezone),
        subtitle = settings.timezone ?: stringResource(R.string.settings_notifsrv_timezone_unset),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietTimeDialog(title: String, initialHour: Int, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val picker = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = picker) },
        confirmButton = {
            TpTextButton(onClick = { onConfirm(picker.hour, picker.minute) }, tid = "settings.ok") { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TpTextButton(onClick = onDismiss, tid = "settings.cancel") { Text(stringResource(android.R.string.cancel)) } },
    )
}

private fun NotificationCategory.titleRes(): Int =
    when (this) {
        NotificationCategory.PRICE_DROPS -> R.string.settings_notifsrv_price_drops
        NotificationCategory.TRIP_REMINDERS -> R.string.settings_notifsrv_trip_reminders
        NotificationCategory.MORNING_BRIEF -> R.string.settings_notifsrv_morning_brief
        NotificationCategory.WEATHER -> R.string.settings_notifsrv_weather
        NotificationCategory.NEARBY -> R.string.settings_notifsrv_nearby
        NotificationCategory.SOCIAL -> R.string.settings_notifsrv_social
        NotificationCategory.QUIZ -> R.string.settings_notifsrv_quiz
        else -> R.string.settings_notifications
    }

private fun NotificationCategory.hintRes(): Int =
    when (this) {
        NotificationCategory.PRICE_DROPS -> R.string.settings_notifsrv_price_drops_hint
        NotificationCategory.TRIP_REMINDERS -> R.string.settings_notifsrv_trip_reminders_hint
        NotificationCategory.MORNING_BRIEF -> R.string.settings_notifsrv_morning_brief_hint
        NotificationCategory.WEATHER -> R.string.settings_notifsrv_weather_hint
        NotificationCategory.NEARBY -> R.string.settings_notifsrv_nearby_hint
        NotificationCategory.SOCIAL -> R.string.settings_notifsrv_social_hint
        NotificationCategory.QUIZ -> R.string.settings_notifsrv_quiz_hint
        else -> R.string.settings_notifications_sub
    }

@Preview(name = "Server notifications", widthDp = 380, heightDp = 1100)
@Composable
private fun ServerNotificationsPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        ServerNotificationsSection(
            ServerNotificationsUiState(
                available = true,
                loading = false,
                settings =
                    NotificationSettings(
                        categories = mapOf("weather" to CategorySetting(false)),
                        quietHours = QuietHours(true, "22:00", "07:00"),
                        dailyCap = 3,
                        timezone = "Asia/Kolkata",
                        quietHoursActive = true,
                        push = PushStatus(fcm = true),
                        devices = 2,
                    ),
            ),
            onEvent = {},
        )
    }
}
