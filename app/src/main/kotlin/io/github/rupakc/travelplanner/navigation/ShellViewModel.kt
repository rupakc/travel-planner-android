package io.github.rupakc.travelplanner.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import io.github.rupakc.travelplanner.core.network.AuthEvent
import io.github.rupakc.travelplanner.core.network.AuthEvents
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App-shell actions that cross features (sign-out, onboarding completion until the onboarding team owns it). */
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
        private val clock: AppClock,
        authEvents: AuthEvents,
    ) : ViewModel() {
        /** Emits when a 401 cleared the session (shown as a snackbar). */
        val sessionExpired: Flow<AuthEvent> = authEvents.flow

        suspend fun signOut() = authRepository.logout()

        fun completeOnboarding() {
            viewModelScope.launch {
                val profile = profileRepository.profile.first()
                profileRepository.saveProfile(profile.copy(onboardingCompleted = true, completedAtEpochMillis = clock.now().toEpochMilli()))
            }
        }
    }
