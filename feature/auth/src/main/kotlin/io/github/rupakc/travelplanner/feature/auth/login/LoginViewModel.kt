package io.github.rupakc.travelplanner.feature.auth.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.toLoginMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val canSubmit: Boolean get() = username.isNotBlank() && password.isNotEmpty() && !loading
}

sealed interface LoginEvent {
    data class UsernameChanged(val value: String) : LoginEvent

    data class PasswordChanged(val value: String) : LoginEvent

    data object TogglePasswordVisibility : LoginEvent

    data object Submit : LoginEvent
}

/**
 * Signs in with `AuthRepository.login`. On success the app's auth gate moves on
 * (change password / onboarding / Home); [LoginUiState.signedIn] is informational.
 */
@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(LoginUiState(username = savedState[KEY_USERNAME] ?: ""))
        val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

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
