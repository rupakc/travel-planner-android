package io.github.rupakc.travelplanner.feature.auth.signup

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthFlags
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.auth.SignupRequest
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sign-up form. Passwords stay in memory only. [minPasswordLength] and [inviteRequired] come from `GET /api/config`. */
@Immutable
data class SignUpUiState(
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val inviteCode: String = "",
    val passwordVisible: Boolean = false,
    val minPasswordLength: Int = AuthFlags.DEFAULT_MIN_PASSWORD_LENGTH,
    val inviteRequired: Boolean = false,
    val loading: Boolean = false,
    val error: AuthMessage? = null,
) {
    val passwordCheck: PasswordCheck get() = PasswordRules.check(password, minPasswordLength, username, email)

    val canSubmit: Boolean
        get() =
            !loading && username.isNotBlank() && email.contains('@') && passwordCheck.ok &&
                (!inviteRequired || inviteCode.isNotBlank())
}

sealed interface SignUpEvent {
    data class UsernameChanged(val value: String) : SignUpEvent

    data class EmailChanged(val value: String) : SignUpEvent

    data class PasswordChanged(val value: String) : SignUpEvent

    data class DisplayNameChanged(val value: String) : SignUpEvent

    data class InviteChanged(val value: String) : SignUpEvent

    data object TogglePasswordVisibility : SignUpEvent

    data object Submit : SignUpEvent
}

sealed interface SignUpEffect {
    /** The 202 arrived: show "Check your email" for [email]. */
    data class CheckEmail(val email: String) : SignUpEffect
}

/** `POST /auth/signup`; success moves to Check email. Telemetry carries enums only, never the email or username. */
@HiltViewModel
class SignUpViewModel
    @Inject
    constructor(
        private val signup: SignupRepository,
        private val config: AuthConfigRepository,
        private val telemetry: Telemetry,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SignUpUiState())
        val state: StateFlow<SignUpUiState> = mutableState.asStateFlow()
        private val effectChannel = Channel<SignUpEffect>(Channel.BUFFERED)
        val effects: Flow<SignUpEffect> = effectChannel.receiveAsFlow()

        init {
            telemetry.emit(TelemetryEvents.SIGNUP_STEP, mapOf("step" to "form"))
            viewModelScope.launch {
                val flags = config.flags()
                mutableState.update { it.copy(minPasswordLength = flags.minPasswordLength, inviteRequired = flags.inviteRequired) }
            }
        }

        fun onEvent(event: SignUpEvent) {
            when (event) {
                is SignUpEvent.UsernameChanged -> mutableState.update { it.copy(username = event.value, error = null) }
                is SignUpEvent.EmailChanged -> mutableState.update { it.copy(email = event.value, error = null) }
                is SignUpEvent.PasswordChanged -> mutableState.update { it.copy(password = event.value, error = null) }
                is SignUpEvent.DisplayNameChanged -> mutableState.update { it.copy(displayName = event.value) }
                is SignUpEvent.InviteChanged -> mutableState.update { it.copy(inviteCode = event.value, error = null) }
                SignUpEvent.TogglePasswordVisibility -> mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
                SignUpEvent.Submit -> submit()
            }
        }

        private fun submit() {
            val s = mutableState.value
            if (!s.canSubmit) return
            mutableState.update { it.copy(loading = true, error = null) }
            telemetry.emit(TelemetryEvents.AUTH_START, mapOf("method" to "password", "mode" to "signup"))
            val request =
                SignupRequest(
                    username = s.username.trim().lowercase(),
                    email = s.email.trim(),
                    password = s.password,
                    displayName = s.displayName.trim().ifEmpty { null },
                    inviteCode = if (s.inviteRequired) s.inviteCode.trim().ifEmpty { null } else null,
                    locale = language.current(),
                    client = "android",
                )
            viewModelScope.launch {
                when (val result = signup.signup(request)) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.AUTH_RESULT, mapOf("method" to "password", "mode" to "signup", "result" to "ok"))
                        telemetry.emit(TelemetryEvents.SIGNUP_STEP, mapOf("step" to "check_email"))
                        mutableState.update { it.copy(loading = false, password = "") }
                        effectChannel.trySend(SignUpEffect.CheckEmail(result.value.email ?: request.email.lowercase()))
                    }

                    is AppResult.Failure -> {
                        val props = mutableMapOf<String, Any?>("method" to "password", "mode" to "signup", "result" to "error")
                        result.error.errorCode()?.let { props["code"] = it }
                        telemetry.emit(TelemetryEvents.AUTH_RESULT, props)
                        mutableState.update { it.copy(loading = false, error = result.error.toAccountMessage()) }
                    }
                }
            }
        }
    }
