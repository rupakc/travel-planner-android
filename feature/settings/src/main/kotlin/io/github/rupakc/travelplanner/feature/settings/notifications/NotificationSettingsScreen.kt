package io.github.rupakc.travelplanner.feature.settings.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsRow
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold
import io.github.rupakc.travelplanner.feature.settings.common.SettingsSwitchRow

@Composable
internal fun NotificationSettingsRoute(
    onBack: () -> Unit,
    onOpenAlertPrefs: () -> Unit,
    viewModel: NotificationSettingsViewModel,
    serverViewModel: ServerNotificationsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val server by serverViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var systemEnabled by remember { mutableStateOf(true) }
    LifecycleResumeEffect(context) {
        systemEnabled = context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false
        serverViewModel.onEvent(ServerNotificationsEvent.Refresh)
        onPauseOrDispose { }
    }
    NotificationSettingsScreen(
        settings = state,
        systemEnabled = systemEnabled,
        onToggle = viewModel::onEvent,
        onOpenSystem = { channel -> openSystemSettings(context, channel) },
        onBack = onBack,
        server = server,
        onServerEvent = serverViewModel::onEvent,
        onOpenAlertPrefs = onOpenAlertPrefs,
    )
}

/** Opens the app's (or one channel's) system notification settings. */
private fun openSystemSettings(context: Context, channel: ChannelShortcut?) {
    val intent =
        if (channel == null) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_CHANNEL_ID, channel.channelId)
        }
    runCatching { context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** In-app notification switches + shortcuts to each system channel (stateless). */
@Composable
fun NotificationSettingsScreen(
    settings: AppSettings?,
    systemEnabled: Boolean,
    onToggle: (SetToggle) -> Unit,
    onOpenSystem: (ChannelShortcut?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    server: ServerNotificationsUiState = ServerNotificationsUiState(),
    onServerEvent: (ServerNotificationsEvent) -> Unit = {},
    onOpenAlertPrefs: (() -> Unit)? = null,
) {
    SettingsScaffold(stringResource(R.string.settings_notifications), onBack = onBack, modifier = modifier) {
        if (settings == null) {
            SkeletonList(Modifier.padding(TravelTheme.spacing.gutter))
            return@SettingsScaffold
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!systemEnabled) {
                Surface(
                    color = TravelTheme.extendedColors.warningContainer,
                    shape = TravelShape.ItemCard,
                    modifier = Modifier.fillMaxWidth().padding(TravelTheme.spacing.gutter).semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Column(Modifier.padding(TravelTheme.spacing.md)) {
                        Text(stringResource(R.string.settings_notif_system_off), color = TravelTheme.extendedColors.onWarningContainer)
                        TpButton(stringResource(R.string.settings_notif_turn_on), onClick = { onOpenSystem(null) }, tid = "settings.settings_notif_turn_on")
                    }
                }
            }
            if (server.available) {
                ServerNotificationsSection(server, onServerEvent, onOpenAlertPrefs = onOpenAlertPrefs)
                // Trip, price and nearby are switched by the category rows above (they also drive the on-device setting).
                SettingsHeading(stringResource(R.string.settings_notif_nearby))
            } else {
                SettingsHeading(stringResource(R.string.settings_notif_in_app))
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_trip),
                    subtitle = stringResource(R.string.settings_notif_trip_sub),
                    icon = Icons.Filled.DateRange,
                    checked = settings.tripNotifications,
                    onCheckedChange = { onToggle(SetToggle(NotificationToggle.TRIP, it)) },
                    tid = "settings.notification_settings_switch_1",
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_price),
                    subtitle = stringResource(R.string.settings_notif_price_sub),
                    icon = Icons.Filled.Notifications,
                    checked = settings.priceAlertNotifications,
                    onCheckedChange = { onToggle(SetToggle(NotificationToggle.PRICE_ALERTS, it)) },
                    tid = "settings.notification_settings_switch_2",
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_nearby),
                    subtitle = stringResource(R.string.settings_notif_nearby_sub),
                    icon = Icons.Filled.LocationOn,
                    checked = settings.nearbyNotifications,
                    onCheckedChange = { onToggle(SetToggle(NotificationToggle.NEARBY, it)) },
                    tid = "settings.notification_settings_switch_3",
                )
            }
            // The background check never asks for a fresh fix: it reuses the coarse cell stored by Near me.
            SettingsNote(stringResource(R.string.settings_notif_nearby_location_note))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_notif_nearby_always),
                subtitle = stringResource(R.string.settings_notif_nearby_always_sub),
                checked = settings.nearbyNotificationsAlways && settings.nearbyNotifications,
                enabled = settings.nearbyNotifications,
                onCheckedChange = { onToggle(SetToggle(NotificationToggle.NEARBY_ALWAYS, it)) },
                tid = "settings.notification_settings_switch_4",
            )
            SettingsHeading(stringResource(R.string.settings_notif_system))
            SettingsNote(stringResource(R.string.settings_notif_system_note))
            ChannelShortcut.entries.forEach { channel ->
                SettingsRow(
                    stringResource(channel.titleRes()),
                    subtitle = stringResource(R.string.settings_notif_channel_sub),
                    icon = if (channel == ChannelShortcut.ACCOUNT) Icons.Filled.Person else null,
                    onClick = { onOpenSystem(channel) },
                )
            }
        }
    }
}

private fun ChannelShortcut.titleRes(): Int =
    when (this) {
        ChannelShortcut.TRIP_REMINDERS -> R.string.settings_notif_trip
        ChannelShortcut.PRICE_ALERTS -> R.string.settings_notif_price
        ChannelShortcut.NEARBY -> R.string.settings_notif_nearby
        ChannelShortcut.ACCOUNT -> R.string.settings_notif_account
    }

@Preview(name = "Notifications light", widthDp = 380, heightDp = 900)
@Composable
private fun NotificationsPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { NotificationSettingsScreen(AppSettings(), systemEnabled = false, onToggle = {}, onOpenSystem = {}, onBack = {}) }
}

@Preview(name = "Notifications dark", widthDp = 380, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsPreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { NotificationSettingsScreen(AppSettings(), systemEnabled = true, onToggle = {}, onOpenSystem = {}, onBack = {}) }
}
