package io.github.rupakc.travelplanner.feature.auth.verify

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.signup.Cooldown
import io.github.rupakc.travelplanner.feature.auth.signup.toAccountMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Check your email" for [email] (null when the screen was opened without one). */
@Immutable
data class CheckEmailUiState(
    val email: String? = null,
    val sending: Boolean = false,
    val resent: Boolean = false,
    val cooldownSeconds: Int = 0,
    val error: AuthMessage? = null,
) {
    val canResend: Boolean get() = !email.isNullOrBlank() && !sending && cooldownSeconds == 0
}

sealed interface CheckEmailEvent {
    data object Resend : CheckEmailEvent
}

/** `POST /auth/verify-email/resend` with a 30 s cooldown between sends (the first send came with the sign-up). */
@HiltViewModel
class CheckEmailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val signup: SignupRepository,
        private val telemetry: Telemetry,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(CheckEmailUiState(email = savedState.get<String>("email")?.takeIf { it.isNotBlank() }))
        val state: StateFlow<CheckEmailUiState> = mutableState.asStateFlow()
        private val cooldown = Cooldown(viewModelScope) { left -> mutableState.update { it.copy(cooldownSeconds = left) } }

        init {
            // The sign-up just sent the first mail, so the button starts in cooldown.
            if (mutableState.value.email != null) cooldown.start()
        }

        fun onEvent(event: CheckEmailEvent) {
            when (event) {
                CheckEmailEvent.Resend -> resend()
            }
        }

        private fun resend() {
            val s = mutableState.value
            val email = s.email ?: return
            if (!s.canResend) return
            mutableState.update { it.copy(sending = true, error = null, resent = false) }
            viewModelScope.launch {
                when (val result = signup.resendVerification(email, language.current())) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.EMAIL_VERIFY, mapOf("result" to "resent"))
                        mutableState.update { it.copy(sending = false, resent = true) }
                        cooldown.start()
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(sending = false, error = result.error.toAccountMessage()) }
                    }
                }
            }
        }
    }
