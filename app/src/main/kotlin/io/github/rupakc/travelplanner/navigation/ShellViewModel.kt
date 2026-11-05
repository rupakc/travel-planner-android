package io.github.rupakc.travelplanner.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.ProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-shell actions that cross features (sign-out, onboarding completion until the onboarding team owns it). */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val clock: AppClock,
) : ViewModel() {
    suspend fun signOut() = authRepository.logout()

    fun completeOnboarding() {
        viewModelScope.launch {
            val profile = profileRepository.profile.first()
            profileRepository.saveProfile(profile.copy(onboardingCompleted = true, completedAtEpochMillis = clock.now().toEpochMilli()))
        }
    }
}
