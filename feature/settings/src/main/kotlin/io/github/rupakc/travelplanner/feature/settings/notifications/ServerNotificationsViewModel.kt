package io.github.rupakc.travelplanner.feature.settings.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.JourneyFlagsRepository
import io.github.rupakc.travelplanner.core.data.repository.NotificationSettingsRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.notifications.NotificationSettingsRules
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.alerts.QuietHours
import io.github.rupakc.travelplanner.core.model.notifications.NotificationCategory
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettings
import io.github.rupakc.travelplanner.core.model.notifications.NotificationSettingsUpdate
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The categories always listed; `social` and `quiz` are added when their feature is on ([ServerNotificationsUiState.categories]). */
val ListedCategories: List<NotificationCategory> =
    listOf(
        NotificationCategory.PRICE_DROPS,
        NotificationCategory.TRIP_REMINDERS,
        NotificationCategory.MORNING_BRIEF,
        NotificationCategory.WEATHER,
        NotificationCategory.NEARBY,
    )

/** Why a load or save failed: [message] is the backend `detail` (shown as-is), null means show our own generic text. */
@Immutable
data class NotificationsFailure(val message: String?)

/** The quiet-hours window as shown (`HH:MM` times, never null). */
@Immutable
data class QuietWindow(val enabled: Boolean, val start: String, val end: String) {
    fun toWire() = QuietHours(enabled, start, end)
}

/** State of the server-backed part of Settings > Notifications. */
@Immutable
data class ServerNotificationsUiState(
    /** `features.notifications_api` is on; false hides the whole section and nothing is called. */
    val available: Boolean = false,
    val loading: Boolean = true,
    val settings: NotificationSettings? = null,
    val saving: Boolean = false,
    val loadError: NotificationsFailure? = null,
    val saveError: NotificationsFailure? = null,
    val local: AppSettings = AppSettings(),
    /** Alerts v2 exists on this backend, so the alert preferences screen can be linked. */
    val alertPrefsAvailable: Boolean = false,
    /** The switches shown: [ListedCategories] plus `social` (`features.social`) and `quiz` (play on and the game visible). */
    val categories: List<NotificationCategory> = ListedCategories,
) {
    /**
     * Whether [category] shows as on. The three categories that also gate an on-device notifier (trip, price, nearby)
     * are on only when the server switch and the local switch both are.
     */
    fun isOn(category: NotificationCategory): Boolean {
        val server = settings?.isEnabled(category) ?: return false
        // The morning brief rides the trip switch on the device (the push is dropped when it is off), so it shows off too.
        val toggle = category.localSwitch() ?: NotificationToggle.TRIP.takeIf { category == NotificationCategory.MORNING_BRIEF }
        return server && (toggle?.let { local.isOn(it) } ?: true)
    }

    /** Quiet hours as shown: the server value, else the default window, switched off. */
    val quietHours: QuietWindow
        get() {
            val q = settings?.quietHours
            return QuietWindow(q?.enabled ?: false, q?.start ?: DEFAULT_START, q?.end ?: DEFAULT_END)
        }

    val dailyCap: Int get() = NotificationSettingsRules.clampCap(settings?.dailyCap ?: NotificationSettingsRules.DEFAULT_CAP)

    companion object {
        const val DEFAULT_START = "22:00"
        const val DEFAULT_END = "07:00"
    }
}

/** Actions of the server-backed part. */
sealed interface ServerNotificationsEvent {
    /** Reload after a failure. */
    data object Retry : ServerNotificationsEvent

    /** Silent reload (screen resumed): quiet hours are shared with the alert preferences, which can change elsewhere. */
    data object Refresh : ServerNotificationsEvent

    data class SetCategory(val category: NotificationCategory, val enabled: Boolean) : ServerNotificationsEvent

    data class SetQuietEnabled(val enabled: Boolean) : ServerNotificationsEvent

    /** [start] and [end] are `HH:MM`. */
    data class SetQuietTimes(val start: String, val end: String) : ServerNotificationsEvent

    data class SetCap(val cap: Int) : ServerNotificationsEvent
}

private fun NotificationCategory.localSwitch(): NotificationToggle? =
    when (this) {
        NotificationCategory.TRIP_REMINDERS -> NotificationToggle.TRIP
        NotificationCategory.PRICE_DROPS -> NotificationToggle.PRICE_ALERTS
        NotificationCategory.NEARBY -> NotificationToggle.NEARBY
        else -> null
    }

private fun AppSettings.isOn(toggle: NotificationToggle): Boolean =
    when (toggle) {
        NotificationToggle.TRIP -> tripNotifications
        NotificationToggle.PRICE_ALERTS -> priceAlertNotifications
        NotificationToggle.NEARBY -> nearbyNotifications
        NotificationToggle.NEARBY_ALWAYS -> nearbyNotificationsAlways
    }

/**
 * Settings > Notifications, server side (`GET|PUT /notification-settings`, ruling R7). Calls nothing when the backend lacks
 * `notifications_api`. Saves are not optimistic: a switch moves when the server's full answer arrives, so a refusal
 * (`notification_settings_invalid`) leaves the screen as it was. The `trip_reminders`, `price_drops` and `nearby` switches
 * also drive the matching on-device setting after the server accepted the change.
 */
@HiltViewModel
class ServerNotificationsViewModel
    @Inject
    constructor(
        private val repo: NotificationSettingsRepository,
        private val flags: JourneyFlagsRepository,
        private val settingsRepository: SettingsRepository,
        private val telemetry: Telemetry,
        private val account: AccountRepository,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(ServerNotificationsUiState())
        val state: StateFlow<ServerNotificationsUiState> = mutable.asStateFlow()
        private var loadJob: Job? = null
        private var viewed = false

        init {
            viewModelScope.launch { settingsRepository.settings.collect { s -> mutable.update { it.copy(local = s) } } }
            load(silent = false)
        }

        fun onEvent(event: ServerNotificationsEvent) {
            when (event) {
                ServerNotificationsEvent.Retry -> {
                    load(silent = false)
                }

                ServerNotificationsEvent.Refresh -> {
                    if (!mutable.value.saving) load(silent = true)
                }

                is ServerNotificationsEvent.SetCategory -> {
                    setCategory(event.category, event.enabled)
                }

                is ServerNotificationsEvent.SetQuietEnabled -> {
                    setQuiet(mutable.value.quietHours.copy(enabled = event.enabled), event.enabled)
                }

                is ServerNotificationsEvent.SetQuietTimes -> {
                    val current = mutable.value.quietHours
                    if (current.start != event.start || current.end != event.end) setQuiet(current.copy(start = event.start, end = event.end), current.enabled)
                }

                is ServerNotificationsEvent.SetCap -> {
                    setCap(NotificationSettingsRules.clampCap(event.cap))
                }
            }
        }

        private suspend fun categoriesFor(gates: JourneyFlags): List<NotificationCategory> {
            val quiz = gates.play && gates.game && (account.settings() as? AppResult.Success)?.value?.hideGame != true
            return ListedCategories +
                listOfNotNull(NotificationCategory.SOCIAL.takeIf { gates.social }, NotificationCategory.QUIZ.takeIf { quiz })
        }

        private fun load(silent: Boolean) {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val gates = flags.flags()
                    mutable.update { it.copy(alertPrefsAvailable = gates.alerts.v2Available, categories = categoriesFor(gates)) }
                    if (!gates.notifications.apiAvailable) {
                        mutable.update { it.copy(available = false, loading = false) }
                        return@launch
                    }
                    if (!silent) mutable.update { it.copy(available = true, loading = true, loadError = null) }
                    when (val result = repo.settings()) {
                        is AppResult.Success -> {
                            mutable.update { it.copy(available = true, loading = false, settings = result.value, loadError = null) }
                            if (!viewed) {
                                viewed = true
                                telemetry.emit(TelemetryEvents.NOTIF_SETTINGS_VIEW)
                            }
                        }

                        // A silent refresh that fails keeps what is on screen.
                        is AppResult.Failure -> {
                            if (!silent || mutable.value.settings == null) {
                                mutable.update { it.copy(available = true, loading = false, loadError = NotificationsFailure(result.error.message)) }
                            }
                        }
                    }
                }
        }

        private fun setCategory(category: NotificationCategory, enabled: Boolean) {
            save(NotificationSettingsUpdate.category(category, enabled)) {
                category.localSwitch()?.let { applyLocal(it, enabled) }
                // The brief rides the trip switch: turning it on must not stay off behind a local trip switch that is off.
                if (category == NotificationCategory.MORNING_BRIEF && enabled) applyLocal(NotificationToggle.TRIP, true)
                telemetry.emit(TelemetryEvents.NOTIF_CATEGORY_TOGGLE, mapOf("category" to category.wire, "on" to enabled))
            }
        }

        private fun setQuiet(quiet: QuietWindow, enabled: Boolean) {
            save(NotificationSettingsUpdate(quietHours = quiet.toWire())) {
                telemetry.emit(TelemetryEvents.NOTIF_QUIET_HOURS_CHANGE, mapOf("enabled" to enabled))
            }
        }

        private fun setCap(cap: Int) {
            if (cap == mutable.value.dailyCap) return
            save(NotificationSettingsUpdate(dailyCap = cap)) { telemetry.emit(TelemetryEvents.NOTIF_CAP_CHANGE, mapOf("cap" to cap)) }
        }

        private suspend fun applyLocal(toggle: NotificationToggle, enabled: Boolean) {
            when (toggle) {
                NotificationToggle.TRIP -> settingsRepository.setNotificationToggles(trip = enabled)
                NotificationToggle.PRICE_ALERTS -> settingsRepository.setNotificationToggles(priceAlerts = enabled)
                NotificationToggle.NEARBY -> settingsRepository.setNotificationToggles(nearby = enabled)
                NotificationToggle.NEARBY_ALWAYS -> settingsRepository.setNotificationToggles(nearbyAlways = enabled)
            }
        }

        private fun save(update: NotificationSettingsUpdate, onSaved: suspend () -> Unit) {
            if (mutable.value.saving) return
            loadJob?.cancel()
            mutable.update { it.copy(saving = true, saveError = null) }
            viewModelScope.launch {
                when (val result = repo.update(update)) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(saving = false, settings = result.value) }
                        onSaved()
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(saving = false, saveError = NotificationsFailure(result.error.message)) }
                    }
                }
            }
        }
    }
