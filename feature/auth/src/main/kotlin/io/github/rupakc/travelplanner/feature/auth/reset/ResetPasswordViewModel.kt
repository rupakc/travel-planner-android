package io.github.rupakc.travelplanner.feature.auth.reset

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthFlags
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.signup.LinkTokens
import io.github.rupakc.travelplanner.feature.auth.signup.PasswordRules
import io.github.rupakc.travelplanner.feature.auth.signup.resetResult
import io.github.rupakc.travelplanner.feature.auth.signup.toAccountMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [token] is null until a deep link or a pasted link/code supplies one. Passwords stay in memory only. */
@Immutable
data class ResetPasswordUiState(
    val token: String? = null,
    val pasted: String = "",
    val password: String = "",
    val confirm: String = "",
    val passwordVisible: Boolean = false,
    val minPasswordLength: Int = AuthFlags.DEFAULT_MIN_PASSWORD_LENGTH,
    val loading: Boolean = false,
    val error: AuthMessage? = null,
    val done: Boolean = false,
) {
    val mismatch: Boolean get() = confirm.isNotEmpty() && confirm != password
    val canSubmit: Boolean
        get() = token != null && !loading && !done && password.length >= minPasswordLength && confirm == password
}

sealed interface ResetPasswordEvent {
    data class PastedChanged(val value: String) : ResetPasswordEvent

    data object UsePasted : ResetPasswordEvent

    data class PasswordChanged(val value: String) : ResetPasswordEvent

    data class ConfirmChanged(val value: String) : ResetPasswordEvent

    data object TogglePasswordVisibility : ResetPasswordEvent

    data object Submit : ResetPasswordEvent
}

/**
 * `POST /auth/password-reset/confirm`. Success stores the returned session (every other device was signed out),
 * so the app's auth gate moves on by itself. Rules mirror the server: minimum length from config, else 8.
 */
@HiltViewModel
class ResetPasswordViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val signup: SignupRepository,
        private val config: AuthConfigRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ResetPasswordUiState(token = LinkTokens.extract(savedState.get<String>("token"))))
        val state: StateFlow<ResetPasswordUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                val flags = config.flags()
                mutableState.update { it.copy(minPasswordLength = flags.minPasswordLength) }
            }
        }

        fun onEvent(event: ResetPasswordEvent) {
            when (event) {
                is ResetPasswordEvent.PastedChanged -> mutableState.update { it.copy(pasted = event.value, error = null) }
                ResetPasswordEvent.UsePasted -> usePasted()
                is ResetPasswordEvent.PasswordChanged -> mutableState.update { it.copy(password = event.value, error = null) }
                is ResetPasswordEvent.ConfirmChanged -> mutableState.update { it.copy(confirm = event.value, error = null) }
                ResetPasswordEvent.TogglePasswordVisibility -> mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
                ResetPasswordEvent.Submit -> submit()
            }
        }

        private fun usePasted() {
            val token = LinkTokens.extract(mutableState.value.pasted)
            mutableState.update {
                if (token == null) it.copy(error = AuthMessage.Local(R.string.auth_err_token_invalid)) else it.copy(token = token, error = null)
            }
        }

        private fun submit() {
            val s = mutableState.value
            val token = s.token ?: return
            if (!s.canSubmit) return
            if (!PasswordRules.check(s.password, s.minPasswordLength).ok) return
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = signup.confirmPasswordReset(token, s.password)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.PASSWORD_RESET, mapOf("step" to "confirm", "result" to "ok"))
                        mutableState.update { it.copy(loading = false, done = true, password = "", confirm = "") }
                    }

                    is AppResult.Failure -> {
                        telemetry.emit(TelemetryEvents.PASSWORD_RESET, mapOf("step" to "confirm", "result" to result.error.resetResult()))
                        mutableState.update { it.copy(loading = false, error = result.error.toAccountMessage()) }
                    }
                }
            }
        }
    }
