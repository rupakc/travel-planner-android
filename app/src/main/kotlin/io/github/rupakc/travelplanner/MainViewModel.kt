package io.github.rupakc.travelplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.data.repository.SettingsRepository
import io.github.rupakc.travelplanner.core.model.settings.AppSettings
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    ) : ViewModel() {
        val state: StateFlow<MainUiState> =
            combine(
                authRepository.session,
                profileRepository.profile,
                settingsRepository.settings,
            ) { session, profile, settings -> MainUiState(gateFor(session, profile), settings) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())
    }
