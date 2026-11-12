package io.github.rupakc.travelplanner.feature.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.telemetry.Telemetry
import io.github.rupakc.travelplanner.core.domain.telemetry.TelemetryEvents
import io.github.rupakc.travelplanner.core.model.auth.SessionInfo
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class SessionsPhase { Loading, Error, List, Unsupported }

@Immutable
internal data class SessionsUiState(
    val phase: SessionsPhase = SessionsPhase.Loading,
    val error: AccountMessage? = null,
    val sessions: List<SessionInfo> = emptyList(),
    val revoking: String? = null,
    val confirmRevoke: SessionInfo? = null,
    val confirmSignOutAll: Boolean = false,
    val busy: Boolean = false,
    val message: AccountMessage? = null,
)

internal sealed interface SessionsEvent {
    data object Retry : SessionsEvent

    data class AskRevoke(val session: SessionInfo) : SessionsEvent

    data object ConfirmRevoke : SessionsEvent

    data object AskSignOutAll : SessionsEvent

    data object ConfirmSignOutAll : SessionsEvent

    data object Dismiss : SessionsEvent

    data object MessageShown : SessionsEvent
}

internal sealed interface SessionsEffect {
    data object SignedOut : SessionsEffect
}

/**
 * Signed-in devices (Task B1 endpoint) and "Sign out everywhere". A backend without the list answers 404:
 * the screen then offers only "Sign out everywhere" ([SessionsPhase.Unsupported]).
 */
@HiltViewModel
internal class SessionsViewModel
    @Inject
    constructor(
        private val repo: AccountRepository,
        private val telemetry: Telemetry,
    ) : ViewModel() {
        private val mutable = MutableStateFlow(SessionsUiState())
        val state: StateFlow<SessionsUiState> = mutable.asStateFlow()
        private val effectChannel = Channel<SessionsEffect>(Channel.BUFFERED)
        val effects = effectChannel.receiveAsFlow()

        init {
            load()
        }

        fun onEvent(event: SessionsEvent) {
            when (event) {
                SessionsEvent.Retry -> load()
                is SessionsEvent.AskRevoke -> mutable.update { it.copy(confirmRevoke = event.session) }
                SessionsEvent.ConfirmRevoke -> revoke()
                SessionsEvent.AskSignOutAll -> mutable.update { it.copy(confirmSignOutAll = true) }
                SessionsEvent.ConfirmSignOutAll -> signOutAll()
                SessionsEvent.Dismiss -> mutable.update { it.copy(confirmRevoke = null, confirmSignOutAll = false) }
                SessionsEvent.MessageShown -> mutable.update { it.copy(message = null) }
            }
        }

        private fun load() {
            mutable.update { it.copy(phase = SessionsPhase.Loading, error = null) }
            viewModelScope.launch {
                when (val r = repo.sessions()) {
                    is AppResult.Success -> {
                        mutable.update { it.copy(phase = SessionsPhase.List, sessions = r.value) }
                    }

                    is AppResult.Failure -> {
                        val unsupported = r.error is AppError.NotFound
                        mutable.update {
                            if (unsupported) {
                                it.copy(phase = SessionsPhase.Unsupported)
                            } else {
                                it.copy(phase = SessionsPhase.Error, error = r.error.toMessage(R.string.account_err_sessions))
                            }
                        }
                    }
                }
            }
        }

        private fun revoke() {
            val target = mutable.value.confirmRevoke ?: return
            mutable.update { it.copy(confirmRevoke = null, revoking = target.id) }
            viewModelScope.launch {
                val r = repo.revokeSession(target.id)
                // 404 session_not_found: already gone, which is what the user wanted.
                val gone = r is AppResult.Success || (r as? AppResult.Failure)?.error is AppError.NotFound
                if (gone) {
                    mutable.update { s -> s.copy(revoking = null, sessions = s.sessions.filterNot { it.id == target.id }) }
                } else {
                    mutable.update { it.copy(revoking = null, message = (r as AppResult.Failure).error.toMessage(R.string.account_err_revoke)) }
                }
            }
        }

        private fun signOutAll() {
            mutable.update { it.copy(confirmSignOutAll = false, busy = true) }
            viewModelScope.launch {
                when (val r = repo.logoutAll()) {
                    is AppResult.Success -> {
                        telemetry.emit(TelemetryEvents.ACCOUNT_ACTION, mapOf("action" to "signout_all"))
                        mutable.update { it.copy(busy = false) }
                        effectChannel.send(SessionsEffect.SignedOut)
                    }

                    is AppResult.Failure -> {
                        mutable.update { it.copy(busy = false, message = r.error.toMessage(R.string.account_err_signout_all)) }
                    }
                }
            }
        }
    }
