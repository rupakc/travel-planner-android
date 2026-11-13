package io.github.rupakc.travelplanner.feature.auth.verify

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.signup.Cooldown
import io.github.rupakc.travelplanner.feature.auth.signup.LinkTokens
import io.github.rupakc.travelplanner.feature.auth.signup.errorCode
import io.github.rupakc.travelplanner.feature.auth.signup.toAccountMessage
import io.github.rupakc.travelplanner.feature.auth.signup.verifyResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VerifyPhase {
    /** No token yet: the user pastes the link or code. */
    NeedsToken,
    Working,

    /** Account activated, session stored (the app moves on by itself; the repository forwards the game delta to GameEvents). */
    SignedIn,

    /** `session == null`: an email on an already active account was confirmed. */
    Confirmed,
    Failed,

    /** The link confirmed another account than the signed-in one: switching is the user's call. */
    ConfirmSwitch,
}

@Immutable
data class VerifyEmailUiState(
    val phase: VerifyPhase = VerifyPhase.NeedsToken,
    val pasted: String = "",
    val error: AuthMessage? = null,
    /** An expired or changed-email link offers a new one for this address. */
    val canResend: Boolean = false,
    val email: String = "",
    val sending: Boolean = false,
    val resent: Boolean = false,
    val cooldownSeconds: Int = 0,
    /** The signed-in username shown while [VerifyPhase.ConfirmSwitch] waits. */
    val currentUsername: String = "",
    /** The account the redeemed link belongs to, while [VerifyPhase.ConfirmSwitch] waits. */
    val newUsername: String = "",
) {
    val canSubmitPaste: Boolean get() = pasted.isNotBlank() && phase != VerifyPhase.Working
    val canSendResend: Boolean get() = email.contains('@') && !sending && cooldownSeconds == 0
}

sealed interface VerifyEmailEvent {
    data class PastedChanged(val value: String) : VerifyEmailEvent

    data object SubmitPasted : VerifyEmailEvent

    data class EmailChanged(val value: String) : VerifyEmailEvent

    data object Resend : VerifyEmailEvent

    /** ConfirmSwitch: sign the current user out, then sign in as the link's account. */
    data object SignOutAndVerify : VerifyEmailEvent

    /** ConfirmSwitch: stay signed in as the current user. */
    data object CancelSwitch : VerifyEmailEvent
}

/**
 * `POST /auth/verify-email`. The token comes from the deep link (route argument, a link or a bare token) or is
 * pasted (a full URL or a bare code). The repository stores the session when one is returned. A link that
 * belongs to another account than the signed-in one never swaps sessions silently: the user decides first.
 */
@HiltViewModel
class VerifyEmailViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val signup: SignupRepository,
        private val auth: AuthRepository,
        private val telemetry: Telemetry,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(VerifyEmailUiState())
        val state: StateFlow<VerifyEmailUiState> = mutableState.asStateFlow()
        private val cooldown = Cooldown(viewModelScope) { left -> mutableState.update { it.copy(cooldownSeconds = left) } }

        /** A redeemed response for another user, waiting for the user to switch or stay. */
        private var held: VerifyEmailResponse? = null

        init {
            LinkTokens.extract(savedState.get<String>("token"))?.let(::verify)
        }

        fun onEvent(event: VerifyEmailEvent) {
            when (event) {
                is VerifyEmailEvent.PastedChanged -> {
                    mutableState.update { it.copy(pasted = event.value, error = null) }
                }

                VerifyEmailEvent.SubmitPasted -> {
                    submitPasted()
                }

                is VerifyEmailEvent.EmailChanged -> {
                    mutableState.update { it.copy(email = event.value) }
                }

                VerifyEmailEvent.Resend -> {
                    resend()
                }

                VerifyEmailEvent.SignOutAndVerify -> {
                    signOutAndVerify()
                }

                VerifyEmailEvent.CancelSwitch -> {
                    // The token is spent and the email confirmed; the signed-in user simply stays signed in.
                    held = null
                    mutableState.update { it.copy(phase = VerifyPhase.Confirmed, currentUsername = "", newUsername = "") }
                }
            }
        }

        private fun submitPasted() {
            val token = LinkTokens.extract(mutableState.value.pasted)
            if (token == null) {
                telemetry.emit(TelemetryEvents.EMAIL_VERIFY, mapOf("result" to "invalid"))
                mutableState.update { it.copy(error = AuthMessage.Local(R.string.auth_err_token_invalid)) }
            } else {
                verify(token)
            }
        }

        private fun signOutAndVerify() {
            val response = held ?: return
            held = null
            mutableState.update { it.copy(phase = VerifyPhase.Working, currentUsername = "", newUsername = "") }
            viewModelScope.launch {
                // Another user: the old session and its local data go first (the same sign-out as Settings).
                auth.logout()
                when (signup.storeSession(response)) {
                    is AppResult.Success -> mutableState.update { it.copy(phase = VerifyPhase.SignedIn) }
                    is AppResult.Failure -> mutableState.update { it.copy(phase = VerifyPhase.Confirmed) }
                }
            }
        }

        /**
         * Redeems [token] without storing a session. Signed out or the same user: the session is stored at once (nothing is
         * logged out, nothing is cleared). Another user: the choice is theirs, so the response waits ([VerifyPhase.ConfirmSwitch]).
         */
        private fun verify(token: String) {
            mutableState.update { it.copy(phase = VerifyPhase.Working, error = null, canResend = false) }
            viewModelScope.launch {
                when (val result = signup.redeemEmailToken(token)) {
                    is AppResult.Success -> {
                        onRedeemed(result.value)
                    }

                    is AppResult.Failure -> {
                        val error = result.error
                        telemetry.emit(TelemetryEvents.EMAIL_VERIFY, mapOf("result" to error.verifyResult()))
                        val resendable = error.errorCode() in setOf("token_expired", "email_changed")
                        mutableState.update {
                            it.copy(phase = VerifyPhase.Failed, error = error.toAccountMessage(), canResend = resendable)
                        }
                    }
                }
            }
        }

        private suspend fun onRedeemed(response: VerifyEmailResponse) {
            telemetry.emit(TelemetryEvents.EMAIL_VERIFY, mapOf("result" to "ok"))
            telemetry.emit(TelemetryEvents.SIGNUP_STEP, mapOf("step" to "done"))
            val session = response.session
            val current = auth.session.first()?.user?.username
            when {
                session == null -> {
                    mutableState.update { it.copy(phase = VerifyPhase.Confirmed) }
                }

                current != null && !current.equals(session.user.username, ignoreCase = true) -> {
                    held = response
                    mutableState.update {
                        it.copy(phase = VerifyPhase.ConfirmSwitch, currentUsername = current, newUsername = session.user.username)
                    }
                }

                else -> {
                    val stored = signup.storeSession(response)
                    mutableState.update { it.copy(phase = if (stored is AppResult.Success) VerifyPhase.SignedIn else VerifyPhase.Confirmed) }
                }
            }
        }

        private fun resend() {
            val s = mutableState.value
            if (!s.canSendResend) return
            mutableState.update { it.copy(sending = true, resent = false) }
            viewModelScope.launch {
                when (val result = signup.resendVerification(s.email.trim(), language.current())) {
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
