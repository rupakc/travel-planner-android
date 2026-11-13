package io.github.rupakc.travelplanner.feature.alerts.v2

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AlertsV2Repository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.alerts.AlertPrefsRules
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTelemetry
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefs
import io.github.rupakc.travelplanner.core.model.alerts.AlertPrefsUpdate
import io.github.rupakc.travelplanner.core.model.alerts.AlertsErrorCode
import io.github.rupakc.travelplanner.core.model.alerts.alertsErrorCode
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why the last save failed. [detail] is the backend text, shown as-is when no translated copy exists. */
@Immutable
sealed interface PrefsError {
    /** `422 alert_prefs_invalid`. */
    data object Invalid : PrefsError

    data class Failed(val detail: String?) : PrefsError
}

@Immutable
sealed interface AlertPrefsUiState {
    data object Loading : AlertPrefsUiState

    data class Failed(val message: String?) : AlertPrefsUiState

    /** [saving]: a PUT is in flight (the controls are disabled, a second change is ignored). [error] clears on the next edit. */
    data class Content(val prefs: AlertPrefs, val saving: Boolean = false, val error: PrefsError? = null) : AlertPrefsUiState
}

/** Alert preferences (`GET|PUT /alerts/preferences`): digest, instant mode, quiet hours, daily caps, grouping; timezone is read-only. */
@HiltViewModel
class AlertPrefsViewModel
    @Inject
    constructor(
        private val alerts: AlertsV2Repository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow<AlertPrefsUiState>(AlertPrefsUiState.Loading)
        val state: StateFlow<AlertPrefsUiState> = mutable.asStateFlow()

        init {
            load()
        }

        fun load() {
            mutable.value = AlertPrefsUiState.Loading
            viewModelScope.launch {
                mutable.value =
                    when (val result = alerts.preferences()) {
                        is AppResult.Success -> AlertPrefsUiState.Content(result.value)
                        is AppResult.Failure -> AlertPrefsUiState.Failed(result.error.message)
                    }
            }
        }

        fun setDigestWeekly(on: Boolean) = save(AlertPrefsUpdate(digestWeekly = on))

        fun setDigestPush(on: Boolean) = save(AlertPrefsUpdate(digestPush = on))

        fun setDigestDay(isoDay: Int) = save(AlertPrefsUpdate(digestDay = isoDay.coerceIn(AlertPrefsRules.DAY_MIN, AlertPrefsRules.DAY_MAX)))

        fun setInstant(mode: String) = save(AlertPrefsUpdate(instant = mode))

        fun setQuietEnabled(on: Boolean) {
            val q = current()?.quietHours
            save(AlertPrefsUpdate(quietHours = AlertPrefsRules.quietHours(on, q?.start, q?.end)))
        }

        fun setQuietTimes(start: String, end: String) {
            val on = current()?.quietHours?.enabled == true
            save(AlertPrefsUpdate(quietHours = AlertPrefsRules.quietHours(on, start, end)))
        }

        /** Sets the cap of [kind] (`flight|hotel|activity|trip`) to [value], clamped 1..10; sends all four caps. */
        fun setCap(kind: String, value: Int) {
            val prefs = current() ?: return
            save(AlertPrefsUpdate(dailyCap = AlertPrefsRules.withCap(prefs, kind, value)))
        }

        fun setGroupWindow(minutes: Int) = save(AlertPrefsUpdate(groupWindowMin = AlertPrefsRules.clampGroup(minutes)))

        private fun current(): AlertPrefs? = (mutable.value as? AlertPrefsUiState.Content)?.prefs

        private fun save(update: AlertPrefsUpdate) {
            val before = mutable.value as? AlertPrefsUiState.Content ?: return
            // One PUT at a time: a change made while one is in flight is not sent (the screen disables the controls meanwhile).
            if (before.saving) return
            mutable.update { (it as? AlertPrefsUiState.Content)?.copy(saving = true, error = null) ?: it }
            viewModelScope.launch {
                when (val result = alerts.updatePreferences(update)) {
                    is AppResult.Success -> {
                        mutable.value = AlertPrefsUiState.Content(result.value)
                        AlertPrefsRules.telemetryField(update)?.let { telemetry.emit(AlertTelemetry.PREFS_CHANGE, mapOf("field" to it)) }
                    }

                    is AppResult.Failure -> {
                        val error =
                            if (result.error.alertsErrorCode() ==
                                AlertsErrorCode.ALERT_PREFS_INVALID
                            ) {
                                PrefsError.Invalid
                            } else {
                                PrefsError.Failed(result.error.message)
                            }
                        // Keep the last server-confirmed values so the controls snap back.
                        mutable.value = before.copy(saving = false, error = error)
                    }
                }
            }
        }
    }
