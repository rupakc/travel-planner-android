package io.github.rupakc.travelplanner.feature.auth.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.designsystem.component.SocialProvider
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.toLoginMessage
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Login form state. The password is never written to saved state. */
@Immutable
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val loading: Boolean = false,
    val error: AuthMessage? = null,
    val signedIn: Boolean = false,
    /** Display name of the provider whose sign-in is "coming soon" (the note under the social buttons), else null. */
    val comingSoonProvider: String? = null,
) {
    val canSubmit: Boolean get() = username.isNotBlank() && password.isNotEmpty() && !loading
}

sealed interface LoginEvent {
    data class UsernameChanged(val value: String) : LoginEvent

    data class PasswordChanged(val value: String) : LoginEvent

    data object TogglePasswordVisibility : LoginEvent

    data object Submit : LoginEvent

    /** A "Continue with …" button: opens the provider's sign-in when the server offers it, else "coming soon". */
    data class SocialProviderTapped(val provider: SocialProvider) : LoginEvent

    /** The footer "Privacy choices" button. */
    data object OpenPrivacyChoices : LoginEvent
}

/** One-shot effects for the login route. */
sealed interface LoginEffect {
    /** Open the provider's same-origin sign-in start URL (already checked) in a Custom Tab. */
    data class OpenUrl(val url: String) : LoginEffect
}

/**
 * Signs in with `AuthRepository.login`. On success the app's auth gate moves on
 * (change password / onboarding / Home); [LoginUiState.signedIn] is informational.
 * Social providers are feature-detected once (`/api/auth/providers`, web `SocialSignInButtons`): until a
 * provider is configured with a safe start URL its button shows "coming soon"; the form never waits for it.
 */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val savedState: SavedStateHandle,
        private val authProviders: AuthProvidersRepository,
        private val consent: ConsentRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(LoginUiState(username = savedState[KEY_USERNAME] ?: ""))
        val state: StateFlow<LoginUiState> = mutableState.asStateFlow()
        private val effectChannel = Channel<LoginEffect>(Channel.BUFFERED)
        val effects: Flow<LoginEffect> = effectChannel.receiveAsFlow()
        private var providers: Map<String, AuthProviderInfo> = emptyMap()

        init {
            // The repository never throws: a 404 or any failure is an empty map (every provider "coming soon").
            viewModelScope.launch { providers = authProviders.providers() }
        }

        fun onEvent(event: LoginEvent) {
            when (event) {
                is LoginEvent.UsernameChanged -> {
                    savedState[KEY_USERNAME] = event.value
                    mutableState.update { it.copy(username = event.value, error = null) }
                }

                is LoginEvent.PasswordChanged -> {
                    mutableState.update { it.copy(password = event.value, error = null) }
                }

                LoginEvent.TogglePasswordVisibility -> {
                    mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
                }

                LoginEvent.Submit -> {
                    submit()
                }

                is LoginEvent.SocialProviderTapped -> {
                    chooseProvider(event.provider)
                }

                LoginEvent.OpenPrivacyChoices -> {
                    consent.openChoices()
                }
            }
        }

        private fun chooseProvider(provider: SocialProvider) {
            val start = providers[provider.id]?.takeIf { it.configured }?.startUrl
            if (start != null) {
                mutableState.update { it.copy(comingSoonProvider = null) }
                effectChannel.trySend(LoginEffect.OpenUrl(start))
            } else {
                mutableState.update { it.copy(comingSoonProvider = provider.displayName) }
            }
        }

        private fun submit() {
            val current = mutableState.value
            if (!current.canSubmit) return
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = authRepository.login(current.username.trim(), current.password)) {
                    is AppResult.Success -> mutableState.update { it.copy(loading = false, password = "", signedIn = true) }
                    is AppResult.Failure -> mutableState.update { it.copy(loading = false, error = result.error.toLoginMessage()) }
                }
            }
        }

        private companion object {
            const val KEY_USERNAME = "username"
        }
    }
