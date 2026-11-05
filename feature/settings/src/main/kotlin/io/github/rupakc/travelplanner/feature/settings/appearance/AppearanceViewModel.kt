package io.github.rupakc.travelplanner.feature.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppearanceEvent {
    data class SetTheme(val mode: ThemeMode) : AppearanceEvent

    data class SetDynamicColor(val enabled: Boolean) : AppearanceEvent
}

/** Settings → Appearance: theme (system/light/dark) and dynamic colour (device-only settings). */
@HiltViewModel
class AppearanceViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val state: StateFlow<AppSettings?> =
            settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        fun onEvent(event: AppearanceEvent) {
            viewModelScope.launch {
                when (event) {
                    is AppearanceEvent.SetTheme -> settingsRepository.setThemeMode(event.mode)
                    is AppearanceEvent.SetDynamicColor -> settingsRepository.setDynamicColor(event.enabled)
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
