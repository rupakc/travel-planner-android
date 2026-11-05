package io.github.rupakc.travelplanner.feature.auth.password

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.model.auth.MIN_PASSWORD_LENGTH
import io.github.rupakc.travelplanner.feature.auth.AuthMessage
import io.github.rupakc.travelplanner.feature.auth.R
import io.github.rupakc.travelplanner.feature.auth.toChangePasswordMessage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which password field an event targets. */
enum class PasswordField { CURRENT, NEW, CONFIRM }

/** Change-password form. Passwords stay in memory only (never saved state). */
@Immutable
data class ChangePasswordUiState(
    val username: String? = null,
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val visible: Set<PasswordField> = emptySet(),
    val loading: Boolean = false,
    val error: AuthMessage? = null,
    val done: Boolean = false,
) {
    val canSubmit: Boolean get() = current.isNotEmpty() && new.isNotEmpty() && confirm.isNotEmpty() && !loading && !done
}

sealed interface ChangePasswordEvent {
    data class Changed(val field: PasswordField, val value: String) : ChangePasswordEvent

    data class ToggleVisibility(val field: PasswordField) : ChangePasswordEvent

    data object Submit : ChangePasswordEvent
}

/**
 * Web ChangePasswordPage rules: confirm must match ("New passwords do not match"), then
 * at least [MIN_PASSWORD_LENGTH] characters, then `POST /api/auth/change-password`.
 * Success stores the fresh token and clears `requires_password_change` (the gate moves on when forced).
 */
@HiltViewModel
class ChangePasswordViewModel
    @Inject
    constructor(private val authRepository: AuthRepository) : ViewModel() {
        private val mutableState = MutableStateFlow(ChangePasswordUiState())
        val state: StateFlow<ChangePasswordUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                authRepository.session.collect { session -> mutableState.update { it.copy(username = session?.user?.username) } }
            }
        }

        fun onEvent(event: ChangePasswordEvent) {
            when (event) {
                is ChangePasswordEvent.Changed -> {
                    mutableState.update {
                        when (event.field) {
                            PasswordField.CURRENT -> it.copy(current = event.value, error = null)
                            PasswordField.NEW -> it.copy(new = event.value, error = null)
                            PasswordField.CONFIRM -> it.copy(confirm = event.value, error = null)
                        }
                    }
                }

                is ChangePasswordEvent.ToggleVisibility -> {
                    mutableState.update {
                        it.copy(visible = if (event.field in it.visible) it.visible - event.field else it.visible + event.field)
                    }
                }

                ChangePasswordEvent.Submit -> {
                    submit()
                }
            }
        }

        private fun submit() {
            val form = mutableState.value
            if (!form.canSubmit) return
            val invalid =
                when {
                    form.new != form.confirm -> AuthMessage.Local(R.string.auth_error_mismatch)
                    form.new.length < MIN_PASSWORD_LENGTH -> AuthMessage.Local(R.string.auth_error_too_short)
                    else -> null
                }
            if (invalid != null) {
                mutableState.update { it.copy(error = invalid) }
                return
            }
            mutableState.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                when (val result = authRepository.changePassword(form.current, form.new)) {
                    is AppResult.Success -> {
                        mutableState.update { it.copy(loading = false, done = true, current = "", new = "", confirm = "") }
                    }

                    is AppResult.Failure -> {
                        mutableState.update { it.copy(loading = false, error = result.error.toChangePasswordMessage()) }
                    }
                }
            }
        }
    }
