package io.github.rupakc.travelplanner.feature.auth.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.designsystem.component.SocialProvider
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.auth.AuthErrorCodes
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.signup.Cooldown
import io.github.rupakc.travelplanner.feature.auth.signup.toAccountMessage
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
    /** `auth.signup_available`: show "Create account". */
    val signupAvailable: Boolean = false,
    /** `auth.password_reset_available`: show "Forgot password?". */
    val resetAvailable: Boolean = false,
    /** Set by `403 account_pending`: the account still waits for its confirmation email. */
    val pending: PendingAccount? = null,
) {
    val canSubmit: Boolean get() = username.isNotBlank() && password.isNotEmpty() && !loading
}

/**
 * The pending-account card: [hint] is the server's masked address (`j•••@example.com`, display only); resend
 * needs the full address, which the user types in [email].
 */
@Immutable
data class PendingAccount(
    val hint: String? = null,
    val email: String = "",
    val sending: Boolean = false,
    val sent: Boolean = false,
    val cooldownSeconds: Int = 0,
    val error: AuthMessage? = null,
) {
    val canResend: Boolean get() = email.contains('@') && !sending && cooldownSeconds == 0
}

sealed interface LoginEvent {
    data class PendingEmailChanged(val value: String) : LoginEvent

    data object ResendPending : LoginEvent

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
        private val signup: SignupRepository,
        private val authConfig: AuthConfigRepository,
        private val telemetry: Telemetry,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(LoginUiState(username = savedState[KEY_USERNAME] ?: ""))
        val state: StateFlow<LoginUiState> = mutableState.asStateFlow()
        private val effectChannel = Channel<LoginEffect>(Channel.BUFFERED)
        val effects: Flow<LoginEffect> = effectChannel.receiveAsFlow()
        private var providers: Map<String, AuthProviderInfo> = emptyMap()

        private val cooldown = Cooldown(viewModelScope) { left -> updatePending { it.copy(cooldownSeconds = left) } }

        init {
            viewModelScope.launch {
                val flags = authConfig.flags()
                mutableState.update { it.copy(signupAvailable = flags.signupAvailable, resetAvailable = flags.resetAvailable) }
            }
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

                is LoginEvent.PendingEmailChanged -> {
                    updatePending { it.copy(email = event.value, error = null) }
                }

                LoginEvent.ResendPending -> {
                    resendPending()
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
                    is AppResult.Failure -> onLoginFailed(result.error)
                }
            }
        }

        private fun onLoginFailed(error: AppError) {
            if (error is AppError.Forbidden && error.code == AuthErrorCodes.ACCOUNT_PENDING) {
                telemetry.emit(TelemetryEvents.AUTH_RESULT, mapOf("method" to "password", "mode" to "signin", "result" to "pending"))
                mutableState.update { it.copy(loading = false, error = null, pending = PendingAccount(hint = error.extras["email_hint"])) }
            } else {
                mutableState.update { it.copy(loading = false, error = error.toLoginMessage()) }
            }
        }

        private fun updatePending(transform: (PendingAccount) -> PendingAccount) {
            mutableState.update { state -> state.pending?.let { state.copy(pending = transform(it)) } ?: state }
        }

        private fun resendPending() {
            val pending = mutableState.value.pending?.takeIf { it.canResend } ?: return
            updatePending { it.copy(sending = true, sent = false, error = null) }
            viewModelScope.launch {
                when (val result = signup.resendVerification(pending.email.trim(), language.current())) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.EMAIL_VERIFY, mapOf("result" to "resent"))
                        updatePending { it.copy(sending = false, sent = true) }
                        cooldown.start()
                    }

                    is AppResult.Failure -> {
                        updatePending { it.copy(sending = false, error = result.error.toAccountMessage()) }
                    }
                }
            }
        }

        private companion object {
            const val KEY_USERNAME = "username"
        }
    }
