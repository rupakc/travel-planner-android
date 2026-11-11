package io.github.rupakc.travelplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentPromptState
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventSink
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.telemetry.ScreenTracker
import io.github.rupakc.travelplanner.telemetry.TelemetryUiBridge
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the auth gate sends the user (spec §4 Networking + task brief). */
enum class AuthGate { LOADING, SIGNED_OUT, MUST_CHANGE_PASSWORD, NEEDS_ONBOARDING, READY }

data class MainUiState(val gate: AuthGate = AuthGate.LOADING, val settings: AppSettings = AppSettings())

/** Pure gate rule, unit-tested. */
fun gateFor(session: Session?, profile: TravellerProfile): AuthGate =
    when {
        session == null -> AuthGate.SIGNED_OUT
        session.user.requiresPasswordChange -> AuthGate.MUST_CHANGE_PASSWORD
        !profile.onboardingCompleted -> AuthGate.NEEDS_ONBOARDING
        else -> AuthGate.READY
    }

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        authRepository: AuthRepository,
        profileRepository: ProfileRepository,
        settingsRepository: SettingsRepository,
        private val consentRepository: ConsentRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        val state: StateFlow<MainUiState> =
            combine(
                authRepository.session,
                profileRepository.profile,
                settingsRepository.settings,
            ) { session, profile, settings -> MainUiState(gateFor(session, profile), settings) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

        /** What the shell's consent host shows (first-run prompt, reopened choices, or nothing). */
        val consentPrompt: StateFlow<ConsentPromptState> = consentRepository.prompt

        /** Design-system taps and toggles → `ui_tap` / `ui_toggle`. */
        val uiEventSink: UiEventSink = TelemetryUiBridge(telemetry)

        /** Destination changes → `screen_view` by route template. */
        val screenTracker: ScreenTracker = ScreenTracker(telemetry)

        fun chooseConsent(choice: ConsentChoice) {
            viewModelScope.launch { consentRepository.choose(choice) }
        }

        fun closeConsentChoices() = consentRepository.closeChoices()

        /** `perf cold_start` for a launch [io.github.rupakc.travelplanner.telemetry.ColdStartGate] accepted. */
        fun reportColdStart(ms: Long) {
            telemetry.emit(
                TelemetryEvents.PERF,
                mapOf("metric" to "cold_start", "value" to ms.toDouble(), "route" to telemetry.currentRoute()),
            )
        }
    }
