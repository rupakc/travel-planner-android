package io.github.rupakc.travelplanner.feature.auth.oauth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLanguage
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.OauthInbox
import io.github.rupakc.travelplanner.core.data.repository.OauthRepository
import io.github.rupakc.travelplanner.core.model.auth.OauthCompleteRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthResultKind
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.signup.errorCode
import io.github.rupakc.travelplanner.feature.auth.signup.toAccountMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The username step of a new social account. The signup token stays in [OauthInbox], never in UI state. */
@Immutable
data class OauthUsernameUiState(
    val username: String = "",
    val inviteCode: String = "",
    val inviteRequired: Boolean = false,
    /** False when no `needs_username` draft is held (e.g. the process restarted): the screen goes back. */
    val hasDraft: Boolean = true,
    val loading: Boolean = false,
    val error: AuthMessage? = null,
) {
    val canSubmit: Boolean get() = hasDraft && !loading && username.isNotBlank() && (!inviteRequired || inviteCode.isNotBlank())
}

sealed interface OauthUsernameEvent {
    data class UsernameChanged(val value: String) : OauthUsernameEvent

    data class InviteChanged(val value: String) : OauthUsernameEvent

    data object Submit : OauthUsernameEvent

    data object Cancel : OauthUsernameEvent
}

/** `POST /auth/oauth/complete`; success stores the session and the app's auth gate moves on. */
@HiltViewModel
class OauthUsernameViewModel
    @Inject
    constructor(
        private val oauth: OauthRepository,
        private val inbox: OauthInbox,
        private val language: AppLanguage,
    ) : ViewModel() {
        private val draft = inbox.draft.value
        private val mutableState =
            MutableStateFlow(
                OauthUsernameUiState(
                    username = draft?.suggestedUsername.orEmpty(),
                    inviteRequired = draft?.inviteRequired == true,
                    hasDraft = draft?.signupToken != null,
                ),
            )
        val state: StateFlow<OauthUsernameUiState> = mutableState.asStateFlow()

        fun onEvent(event: OauthUsernameEvent) {
            when (event) {
                is OauthUsernameEvent.UsernameChanged -> mutableState.update { it.copy(username = event.value, error = null) }
                is OauthUsernameEvent.InviteChanged -> mutableState.update { it.copy(inviteCode = event.value, error = null) }
                OauthUsernameEvent.Submit -> submit()
                OauthUsernameEvent.Cancel -> inbox.setDraft(null)
            }
        }

        private fun submit() {
            val s = mutableState.value
            val token = draft?.signupToken
            if (!s.canSubmit || token == null) return
            mutableState.update { it.copy(loading = true, error = null) }
            val request =
                OauthCompleteRequest(
                    signupToken = token,
                    username = s.username.trim().lowercase(),
                    inviteCode = if (s.inviteRequired) s.inviteCode.trim().ifEmpty { null } else null,
                    client = "android",
                    locale = language.current(),
                )
            viewModelScope.launch {
                when (val result = oauth.complete(request)) {
                    is AppResult.Success -> {
                        inbox.setDraft(null)
                        val ok = result.value.kind == OauthResultKind.SIGNED_IN
                        mutableState.update { it.copy(loading = false, error = if (ok) null else AuthMessage.Local(R.string.auth_error_generic)) }
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(loading = false, error = failureMessage(result.error)) }
                    }
                }
            }
        }

        private fun failureMessage(error: AppError): AuthMessage =
            if (error.errorCode() == "signup_token_invalid") AuthMessage.Local(R.string.auth_oauth_err_code_invalid) else error.toAccountMessage()
    }
