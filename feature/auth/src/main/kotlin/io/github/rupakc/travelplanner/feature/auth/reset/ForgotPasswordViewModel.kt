package io.github.rupakc.travelplanner.feature.auth.reset

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.signup.toAccountMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ForgotPasswordUiState(
    val email: String = "",
    val loading: Boolean = false,
    /** The 202 arrived (always, whether or not the address exists). */
    val sent: Boolean = false,
    /** `auth.password_reset_available` is false: say so instead of offering a form that cannot work. */
    val unavailable: Boolean = false,
    val error: AuthMessage? = null,
) {
    val canSubmit: Boolean get() = email.contains('@') && !loading && !unavailable
}

sealed interface ForgotPasswordEvent {
    data class EmailChanged(val value: String) : ForgotPasswordEvent

    data object Submit : ForgotPasswordEvent
}

/** `POST /auth/password-reset/request`: the answer is the same `202` for every address. */
@HiltViewModel
class ForgotPasswordViewModel
    @Inject
    constructor(
        private val signup: SignupRepository,
        private val config: AuthConfigRepository,
        private val telemetry: Telemetry,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ForgotPasswordUiState())
        val state: StateFlow<ForgotPasswordUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                val flags = config.flags()
                mutableState.update { it.copy(unavailable = !flags.resetAvailable) }
            }
        }

        fun onEvent(event: ForgotPasswordEvent) {
            when (event) {
                is ForgotPasswordEvent.EmailChanged -> mutableState.update { it.copy(email = event.value, error = null) }
                ForgotPasswordEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val s = mutableState.value
            if (!s.canSubmit) return
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = signup.requestPasswordReset(s.email.trim(), language.current())) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.PASSWORD_RESET, mapOf("step" to "request", "result" to "ok"))
                        mutableState.update { it.copy(loading = false, sent = true) }
                    }

                    is AppResult.Failure -> {
                        telemetry.emit(TelemetryEvents.PASSWORD_RESET, mapOf("step" to "request", "result" to "error"))
                        mutableState.update { it.copy(loading = false, error = result.error.toAccountMessage()) }
                    }
                }
            }
        }
    }
