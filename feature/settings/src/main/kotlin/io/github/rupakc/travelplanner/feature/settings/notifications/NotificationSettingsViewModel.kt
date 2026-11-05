package io.github.rupakc.travelplanner.feature.settings.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** In-app notification switches (read by the notifications team's workers from the settings DataStore). */
enum class NotificationToggle { TRIP, PRICE_ALERTS, NEARBY, NEARBY_ALWAYS }

/** Notification channel ids (must match core:notifications `TravelChannel`; features can't depend on that module). */
enum class ChannelShortcut(val channelId: String) {
    TRIP_REMINDERS("trip_reminders"),
    PRICE_ALERTS("price_alerts"),
    NEARBY("nearby_suggestions"),
    ACCOUNT("account"),
}

data class SetToggle(val toggle: NotificationToggle, val enabled: Boolean)

/** Settings → Notifications. */
@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<AppSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun onEvent(event: SetToggle) {
            viewModelScope.launch {
                when (event.toggle) {
                    NotificationToggle.TRIP -> settingsRepository.setNotificationToggles(trip = event.enabled)
                    NotificationToggle.PRICE_ALERTS -> settingsRepository.setNotificationToggles(priceAlerts = event.enabled)
                    NotificationToggle.NEARBY -> settingsRepository.setNotificationToggles(nearby = event.enabled)
                    NotificationToggle.NEARBY_ALWAYS -> settingsRepository.setNotificationToggles(nearbyAlways = event.enabled)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
